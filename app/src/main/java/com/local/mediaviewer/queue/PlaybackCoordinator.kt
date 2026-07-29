package com.local.mediaviewer.queue

import android.view.ViewGroup
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.playback.PlaybackEngine
import com.local.mediaviewer.playback.PlaybackPositionStore
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.playback.VideoScaleMode
import com.local.mediaviewer.player.QueuePlaybackController
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

data class PlaybackSessionState(
    val playback: PlaybackState = PlaybackState(),
    val queue: PlaybackQueue = PlaybackQueue(),
    val currentItem: QueueMediaItem? = null,
    val errorMessage: String? = null,
)

class PlaybackCoordinator(
    private val engine: PlaybackEngine,
    private val queueRepository: PlaybackQueueRepository,
    private val positionStore: PlaybackPositionStore,
    private val session: ServerSessionManager,
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
) : QueuePlaybackController {
    private val coordinatorJob = SupervisorJob(scope.coroutineContext[Job])
    private val coordinatorScope = CoroutineScope(scope.coroutineContext + coordinatorJob)
    private val mutex = Mutex()
    private val mutableSessionState = MutableStateFlow(
        PlaybackSessionState(playback = engine.state.value),
    )
    private var queue = PlaybackQueue()
    private var pendingResumeMediaKey: String? = null
    private var pendingResumeMs: Long? = null
    private var resumeApplied = false
    private var loadedMediaKey: String? = null
    private var lastStatus = engine.state.value.status
    private val engineObserver: Job

    override val state: StateFlow<PlaybackState> = engine.state
    override val sessionState: StateFlow<PlaybackSessionState> =
        mutableSessionState.asStateFlow()

    init {
        engineObserver = coordinatorScope.launch {
            engine.state.collect { playback ->
                mutex.withLock {
                    updatePlayback(playback)
                    applyPendingResume(playback)
                    when {
                        playback.status == PlaybackStatus.ENDED && lastStatus != PlaybackStatus.ENDED ->
                            advance(QueueAdvanceReason.ENDED)
                        playback.status == PlaybackStatus.ERROR && lastStatus != PlaybackStatus.ERROR ->
                            setError(playback.errorMessage ?: "播放失败")
                    }
                    lastStatus = playback.status
                }
            }
        }
    }

    fun restore(autoPlay: Boolean = false) = launchMutation {
        queue = queueRepository.restore()
        val current = queue.currentItem
        pendingResumeMediaKey = current?.mediaKey
        pendingResumeMs = current?.let { positionStore.resumePosition(it.mediaKey) }
        resumeApplied = false
        loadedMediaKey = null
        updateSession(
            playback = if (autoPlay) engine.state.value else engine.state.value.copy(
                status = PlaybackStatus.PAUSED,
            ),
            error = null,
        )
        if (autoPlay) loadCurrent(autoPlay = true)
    }

    override fun replaceQueue(items: List<QueueMediaItem>, startMediaKey: String) = launchMutation {
        setQueue(
            QueueNavigator.replace(items, startMediaKey, queue.mode, random),
            persist = true,
        )
        loadCurrent(autoPlay = true)
    }

    override fun playNext(item: QueueMediaItem) = launchMutation {
        setQueue(QueueNavigator.addNext(queue, item), persist = true)
    }

    override fun append(item: QueueMediaItem) = launchMutation {
        setQueue(QueueNavigator.append(queue, item), persist = true)
    }

    override fun select(mediaKey: String) = launchMutation {
        if (queue.items.none { it.mediaKey == mediaKey }) return@launchMutation
        setQueue(QueueNavigator.select(queue, mediaKey), persist = true)
        loadCurrent(autoPlay = true)
    }

    override fun skipPrevious() = launchMutation {
        QueueNavigator.previous(queue)?.let { selectAndLoad(it) }
    }

    override fun skipNext() = launchMutation {
        QueueNavigator.next(queue, QueueAdvanceReason.USER)?.let { selectAndLoad(it) }
    }

    override fun move(mediaKey: String, toIndex: Int) = launchMutation {
        setQueue(QueueNavigator.move(queue, mediaKey, toIndex), persist = true)
    }

    override fun remove(mediaKey: String) = launchMutation {
        val wasCurrent = queue.currentMediaKey == mediaKey
        setQueue(QueueNavigator.remove(queue, mediaKey, random), persist = true)
        if (queue.currentItem == null) {
            loadedMediaKey = null
            clearPendingResume()
            engine.stop()
        } else if (wasCurrent) {
            loadCurrent(autoPlay = true)
        }
    }

    override fun clearExceptCurrent() = launchMutation {
        val current = queue.currentItem
        val reduced = if (current == null) PlaybackQueue(
            mode = queue.mode,
            playbackSpeed = queue.playbackSpeed,
        ) else QueueNavigator.replace(
            items = listOf(current),
            startMediaKey = current.mediaKey,
            mode = queue.mode,
            random = random,
        ).copy(playbackSpeed = queue.playbackSpeed)
        setQueue(reduced, persist = true)
    }

    override fun clearAll() = launchMutation {
        setQueue(
            PlaybackQueue(mode = queue.mode, playbackSpeed = queue.playbackSpeed),
            persist = true,
        )
        loadedMediaKey = null
        clearPendingResume()
        engine.stop()
    }

    override fun setPlaybackMode(mode: PlaybackMode) = launchMutation {
        setQueue(QueueNavigator.setMode(queue, mode, random), persist = true)
    }

    override fun prepare(url: String) {
        launchMutation {
            loadedMediaKey = null
            engine.prepare(url)
        }
    }

    override fun play() = launchMutation {
        if (queue.currentItem != null && loadedMediaKey != queue.currentMediaKey) {
            loadCurrent(autoPlay = true)
        } else {
            engine.play()
        }
    }

    override fun pause() = launchMutation { engine.pause() }

    override fun stop() = launchMutation { engine.stop() }

    override fun seekTo(positionMs: Long) = launchMutation {
        engine.seekTo(positionMs)
    }

    override fun setPlaybackSpeed(speed: Float) = launchMutation {
        engine.setPlaybackSpeed(speed)
        setQueue(queue.copy(playbackSpeed = speed), persist = true)
    }

    override fun attachVideoOutput(host: ViewGroup) = engine.attachVideoOutput(host)

    override fun detachVideoOutput() = engine.detachVideoOutput()

    override fun setVideoScaleMode(mode: VideoScaleMode) = engine.setVideoScaleMode(mode)

    override fun close() {
        engineObserver.cancel()
        coordinatorJob.cancel()
        engine.close()
    }

    private fun launchMutation(block: suspend () -> Unit) {
        coordinatorScope.launch {
            mutex.withLock { block() }
        }
    }

    private suspend fun advance(reason: QueueAdvanceReason) {
        val next = QueueNavigator.next(queue, reason)
        if (next == null) {
            engine.stop()
            return
        }
        selectAndLoad(next)
    }

    private suspend fun selectAndLoad(mediaKey: String) {
        setQueue(QueueNavigator.select(queue, mediaKey), persist = true)
        loadCurrent(autoPlay = true)
    }

    private suspend fun loadCurrent(autoPlay: Boolean) {
        val item = queue.currentItem ?: run {
            loadedMediaKey = null
            engine.stop()
            return
        }
        val endpoint = connectedEndpointOrRefresh() ?: return
        if (pendingResumeMediaKey != item.mediaKey) {
            pendingResumeMediaKey = item.mediaKey
            pendingResumeMs = positionStore.resumePosition(item.mediaKey)
            resumeApplied = false
        }
        loadedMediaKey = item.mediaKey
        engine.prepare(endpoint.requestUrlFor(item.logicalUrl))
        engine.setPlaybackSpeed(queue.playbackSpeed)
        if (autoPlay) engine.play()
    }

    private suspend fun connectedEndpointOrRefresh() =
        (session.state.value as? ServerSessionState.Connected)?.endpoint ?: when (
            val refreshed = session.refreshAfterRequestFailure()
        ) {
            is AppResult.Success -> refreshed.value
            is AppResult.Failure -> {
                setError(refreshed.error.userMessage)
                null
            }
        }

    private fun applyPendingResume(playback: PlaybackState) {
        val resume = pendingResumeMs ?: return
        if (!resumeApplied && playback.isSeekable && playback.durationMs > resume) {
            engine.seekTo(resume)
            resumeApplied = true
            pendingResumeMs = null
        }
    }

    private suspend fun setQueue(next: PlaybackQueue, persist: Boolean) {
        queue = next
        updateSession()
        if (!persist) return
        runCatching { queueRepository.save(next) }
            .onFailure { setError(it.message ?: "播放队列保存失败") }
    }

    private fun updatePlayback(playback: PlaybackState) {
        updateSession(playback = playback)
    }

    private fun setError(message: String) {
        updateSession(error = message)
    }

    private fun updateSession(
        playback: PlaybackState = mutableSessionState.value.playback,
        error: String? = mutableSessionState.value.errorMessage,
    ) {
        mutableSessionState.value = PlaybackSessionState(
            playback = playback,
            queue = queue,
            currentItem = queue.currentItem,
            errorMessage = error,
        )
    }

    private fun clearPendingResume() {
        pendingResumeMediaKey = null
        pendingResumeMs = null
        resumeApplied = false
    }
}
