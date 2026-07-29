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

    fun start(): PlaybackCoordinator {
        restore(autoPlay = false)
        return this
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
            QueueNavigator.replace(items, startMediaKey, queue.mode, random).copy(
                playbackSpeed = queue.playbackSpeed,
            ),
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
        playCurrent()
    }

    override fun pause() = launchMutation { engine.pause() }

    override fun stop() = launchMutation { engine.stop() }

    override fun seekTo(positionMs: Long) = launchMutation {
        engine.seekTo(positionMs)
    }

    override fun setPlaybackSpeed(speed: Float) = launchMutation {
        updatePlaybackSpeed(speed)
    }

    suspend fun setPlayWhenReadyFromSession(playWhenReady: Boolean) = mutate {
        if (playWhenReady) playCurrent() else engine.pause()
    }

    suspend fun seek(
        mediaItemIndex: Int,
        positionMs: Long,
        @Suppress("UNUSED_PARAMETER") seekCommand: Int,
    ) = mutate {
        val selected = queue.items.getOrNull(mediaItemIndex) ?: return@mutate
        if (selected.mediaKey != queue.currentMediaKey) {
            setQueue(QueueNavigator.select(queue, selected.mediaKey), persist = true)
            loadCurrent(autoPlay = true)
        }
        if (positionMs >= 0L) engine.seekTo(positionMs)
    }

    suspend fun setPlaybackSpeedFromSession(speed: Float) = mutate {
        updatePlaybackSpeed(speed)
    }

    suspend fun add(index: Int, items: List<QueueMediaItem>) = mutate {
        val insertedKeys = items.mapTo(mutableSetOf()) { it.mediaKey }
        val retained = queue.items.filterNot { it.mediaKey in insertedKeys }.toMutableList()
        retained.addAll(index.coerceIn(0, retained.size), items)
        replaceItems(retained, queue.currentMediaKey)
    }

    suspend fun moveRange(fromIndex: Int, toIndex: Int, newIndex: Int) = mutate {
        val from = fromIndex.coerceIn(0, queue.items.size)
        val to = toIndex.coerceIn(from, queue.items.size)
        if (from == to) return@mutate
        val moved = queue.items.subList(from, to)
        val retained = queue.items.toMutableList().apply {
            subList(from, to).clear()
        }
        retained.addAll(newIndex.coerceIn(0, retained.size), moved)
        replaceItems(retained, queue.currentMediaKey)
    }

    suspend fun removeRange(fromIndex: Int, toIndex: Int) = mutate {
        val from = fromIndex.coerceIn(0, queue.items.size)
        val to = toIndex.coerceIn(from, queue.items.size)
        if (from == to) return@mutate
        val removedKeys = queue.items.subList(from, to).mapTo(mutableSetOf()) { it.mediaKey }
        val removedCurrent = queue.currentMediaKey in removedKeys
        val retained = queue.items.filterNot { it.mediaKey in removedKeys }
        val nextCurrentMediaKey = if (removedCurrent) {
            retained.getOrNull(from)?.mediaKey ?: retained.lastOrNull()?.mediaKey
        } else {
            queue.currentMediaKey
        }
        replaceItems(retained, nextCurrentMediaKey)
        if (removedCurrent && queue.currentItem != null) loadCurrent(autoPlay = true)
    }

    suspend fun replaceFromMedia3(
        items: List<QueueMediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ) = mutate {
        val startMediaKey = items.getOrNull(startIndex)?.mediaKey
            ?: items.firstOrNull()?.mediaKey
        replaceItems(items, startMediaKey)
        if (queue.currentItem != null) {
            loadCurrent(
                autoPlay = mutableSessionState.value.playback.status == PlaybackStatus.PLAYING,
            )
            if (startPositionMs >= 0L) engine.seekTo(startPositionMs)
        }
    }

    suspend fun setPlaybackModeFromSession(mode: PlaybackMode) = mutate {
        setQueue(QueueNavigator.setMode(queue, mode, random), persist = true)
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
            mutate(block)
        }
    }

    private suspend fun mutate(block: suspend () -> Unit) {
        mutex.withLock { block() }
    }

    private suspend fun playCurrent() {
        if (queue.currentItem != null && loadedMediaKey != queue.currentMediaKey) {
            loadCurrent(autoPlay = true)
        } else {
            engine.play()
        }
    }

    private suspend fun updatePlaybackSpeed(speed: Float) {
        engine.setPlaybackSpeed(speed)
        setQueue(queue.copy(playbackSpeed = speed), persist = true)
    }

    private suspend fun replaceItems(
        items: List<QueueMediaItem>,
        requestedCurrentMediaKey: String?,
    ) {
        val next = if (items.isEmpty()) {
            PlaybackQueue(mode = queue.mode, playbackSpeed = queue.playbackSpeed)
        } else {
            QueueNavigator.replace(
                items = items,
                startMediaKey = requestedCurrentMediaKey ?: items.first().mediaKey,
                mode = queue.mode,
                random = random,
            ).copy(playbackSpeed = queue.playbackSpeed)
        }
        setQueue(next, persist = true)
        if (next.items.isEmpty()) {
            loadedMediaKey = null
            clearPendingResume()
            engine.stop()
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
