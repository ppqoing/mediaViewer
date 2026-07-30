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
    val playWhenReady: Boolean = false,
    val queue: PlaybackQueue = PlaybackQueue(),
    val currentItem: QueueMediaItem? = null,
    val errorMessage: String? = null,
    val canSkipPrevious: Boolean = false,
    val canSkipNext: Boolean = false,
)

data class PlaybackResumptionSnapshot(
    val items: List<QueueMediaItem> = emptyList(),
    val startIndex: Int = 0,
    val startPositionMs: Long = 0L,
)

data class PlaybackPersistenceSnapshot(
    val queue: PlaybackQueue,
    val currentMediaKey: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtEpochMs: Long,
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
    private var pauseCorrectionIssued = false
    private var endpointRecoveryUsedForMediaKey: String? = null
    private val engineObserver: Job

    override val state: StateFlow<PlaybackState> = engine.state
    override val sessionState: StateFlow<PlaybackSessionState> =
        mutableSessionState.asStateFlow()

    init {
        engineObserver = coordinatorScope.launch {
            engine.state.collect { playback ->
                mutex.withLock {
                    updatePlayback(playback)
                    correctUnexpectedPlaying(playback)
                    applyPendingResume(playback)
                    when {
                        playback.status == PlaybackStatus.PLAYING -> {
                            endpointRecoveryUsedForMediaKey = null
                        }
                        playback.status == PlaybackStatus.ENDED && lastStatus != PlaybackStatus.ENDED ->
                            advance(QueueAdvanceReason.ENDED)
                        playback.status == PlaybackStatus.ERROR && lastStatus != PlaybackStatus.ERROR ->
                            recoverCurrentEndpointLocked(playback)
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
            playWhenReady = autoPlay,
            error = null,
        )
        if (autoPlay) loadCurrent(autoPlay = true)
    }

    suspend fun playbackResumptionSnapshot(): PlaybackResumptionSnapshot =
        mutex.withLock {
            val restored = queueRepository.restore()
            val startIndex = restored.currentIndex
            if (restored.items.isEmpty() || startIndex !in restored.items.indices) {
                return@withLock PlaybackResumptionSnapshot()
            }
            PlaybackResumptionSnapshot(
                items = restored.items,
                startIndex = startIndex,
                startPositionMs = positionStore.resumePosition(
                    restored.items[startIndex].mediaKey,
                )?.coerceAtLeast(0L) ?: 0L,
            )
        }

    fun captureCurrentSnapshot(): PlaybackPersistenceSnapshot {
        val state = mutableSessionState.value
        return PlaybackPersistenceSnapshot(
            queue = state.queue,
            currentMediaKey = state.currentItem?.mediaKey,
            positionMs = state.playback.positionMs,
            durationMs = state.playback.durationMs,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
    }

    suspend fun persistSnapshot(snapshot: PlaybackPersistenceSnapshot) {
        queueRepository.save(snapshot.queue)
        snapshot.currentMediaKey?.let { mediaKey ->
            positionStore.record(
                mediaKey = mediaKey,
                positionMs = snapshot.positionMs,
                durationMs = snapshot.durationMs,
                updatedAtEpochMs = snapshot.updatedAtEpochMs,
            )
        }
    }

    suspend fun saveCurrentSnapshot() = mutate {
        runCatching {
            persistSnapshot(captureCurrentSnapshot())
        }.onFailure {
            setError(it.message ?: "播放状态保存失败")
        }
    }

    fun publishError(message: String) = launchMutation {
        setError(message)
    }

    override fun replaceQueue(items: List<QueueMediaItem>, startMediaKey: String) = launchMutation {
        updatePlayWhenReady(true)
        setQueue(
            QueueNavigator.replace(items, startMediaKey, queue.mode, random).copy(
                playbackSpeed = queue.playbackSpeed,
            ),
            persist = true,
        )
        if (queue.currentItem != null) {
            loadCurrent(autoPlay = true)
        }
    }

    override fun playNext(item: QueueMediaItem) = launchMutation {
        setQueue(QueueNavigator.addNext(queue, item), persist = true)
    }

    override fun append(item: QueueMediaItem) = launchMutation {
        setQueue(QueueNavigator.append(queue, item), persist = true)
    }

    override fun select(mediaKey: String) = launchMutation {
        if (queue.items.none { it.mediaKey == mediaKey }) return@launchMutation
        updatePlayWhenReady(true)
        setQueue(QueueNavigator.select(queue, mediaKey), persist = true)
        loadCurrent(autoPlay = true)
    }

    override fun reloadCurrent() = launchMutation {
        reloadCurrentLocked()
    }

    suspend fun reloadCurrentFromSession() = mutate {
        reloadCurrentLocked()
    }

    override fun skipPrevious() = launchMutation {
        QueueNavigator.previous(queue)?.let {
            updatePlayWhenReady(true)
            selectAndLoad(it, autoPlay = true)
        }
    }

    override fun skipNext() = launchMutation {
        QueueNavigator.next(queue, QueueAdvanceReason.USER)?.let {
            updatePlayWhenReady(true)
            selectAndLoad(it, autoPlay = true)
        }
    }

    override fun move(mediaKey: String, toIndex: Int) = launchMutation {
        setQueue(QueueNavigator.move(queue, mediaKey, toIndex), persist = true)
    }

    override fun remove(mediaKey: String) = launchMutation {
        val wasCurrent = queue.currentMediaKey == mediaKey
        setQueue(QueueNavigator.remove(queue, mediaKey, random), persist = true)
        if (wasCurrent && queue.currentItem != null) {
            updatePlayWhenReady(true)
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
        updatePlayWhenReady(true)
        playCurrent()
    }

    override fun pause() = launchMutation {
        updatePlayWhenReady(false)
        persistCurrentPositionLocked()
        engine.pause()
    }

    fun pauseForInterruption() = launchMutation {
        engine.pause()
    }

    override fun stop() = launchMutation {
        updatePlayWhenReady(false)
        persistCurrentPositionLocked()
        engine.stop()
    }

    override fun seekTo(positionMs: Long) = launchMutation {
        engine.seekTo(positionMs)
    }

    override fun setPlaybackSpeed(speed: Float) = launchMutation {
        updatePlaybackSpeed(speed)
    }

    suspend fun setPlayWhenReadyFromSession(playWhenReady: Boolean) = mutate {
        updatePlayWhenReady(playWhenReady)
        if (playWhenReady) {
            playCurrent()
        } else {
            persistCurrentPositionLocked()
            engine.pause()
        }
    }

    suspend fun seek(
        mediaItemIndex: Int,
        positionMs: Long,
        @Suppress("UNUSED_PARAMETER") seekCommand: Int,
    ) = mutate {
        val selected = queue.items.getOrNull(mediaItemIndex) ?: return@mutate
        if (selected.mediaKey != queue.currentMediaKey) {
            setQueue(QueueNavigator.select(queue, selected.mediaKey), persist = true)
            loadCurrent(autoPlay = mutableSessionState.value.playWhenReady)
        }
        if (positionMs >= 0L) engine.seekTo(positionMs)
    }

    suspend fun setPlaybackSpeedFromSession(speed: Float) = mutate {
        updatePlaybackSpeed(speed)
    }

    suspend fun add(index: Int, items: List<QueueMediaItem>) = mutate {
        applyQueueEdit(QueueNavigator.addAll(queue, index, items))
    }

    suspend fun moveRange(fromIndex: Int, toIndex: Int, newIndex: Int) = mutate {
        applyQueueEdit(QueueNavigator.moveRange(queue, fromIndex, toIndex, newIndex))
    }

    suspend fun removeRange(fromIndex: Int, toIndex: Int) = mutate {
        val from = fromIndex.coerceIn(0, queue.items.size)
        val to = toIndex.coerceIn(from, queue.items.size)
        if (from == to) return@mutate
        val removedKeys = queue.items.subList(from, to).mapTo(mutableSetOf()) { it.mediaKey }
        val removedCurrent = queue.currentMediaKey in removedKeys
        applyQueueEdit(QueueNavigator.removeRange(queue, from, to))
        if (removedCurrent && queue.currentItem != null) {
            loadCurrent(autoPlay = mutableSessionState.value.playWhenReady)
        }
    }

    suspend fun replaceRange(
        fromIndex: Int,
        toIndex: Int,
        items: List<QueueMediaItem>,
    ) = mutate {
        val from = fromIndex.coerceIn(0, queue.items.size)
        val to = toIndex.coerceIn(from, queue.items.size)
        val replacedCurrent = queue.currentMediaKey in
            queue.items.subList(from, to).map { it.mediaKey }
        applyQueueEdit(QueueNavigator.replaceRange(queue, from, to, items))
        if (replacedCurrent && queue.currentItem != null) {
            loadCurrent(autoPlay = mutableSessionState.value.playWhenReady)
        }
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
                autoPlay = mutableSessionState.value.playWhenReady,
            )
            if (startPositionMs >= 0L) engine.seekTo(startPositionMs)
        }
    }

    suspend fun setPlaybackModeFromSession(mode: PlaybackMode) = mutate {
        setQueue(QueueNavigator.setMode(queue, mode, random), persist = true)
    }

    override fun attachVideoOutput(host: ViewGroup) = engine.attachVideoOutput(host)

    override fun detachVideoOutput() = engine.detachVideoOutput()

    override fun refreshVideoOutput() = engine.refreshVideoOutput()

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

    private suspend fun reloadCurrentLocked() {
        if (queue.currentItem == null) return
        updatePlayWhenReady(true)
        loadCurrent(autoPlay = true)
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
        applyQueueEdit(next)
    }

    private suspend fun applyQueueEdit(next: PlaybackQueue) {
        setQueue(next, persist = true)
    }

    private suspend fun advance(reason: QueueAdvanceReason) {
        val next = QueueNavigator.next(queue, reason)
        if (next == null) {
            updatePlayWhenReady(false)
            engine.stop()
            return
        }
        selectAndLoad(next, autoPlay = mutableSessionState.value.playWhenReady)
    }

    private suspend fun selectAndLoad(mediaKey: String, autoPlay: Boolean) {
        setQueue(QueueNavigator.select(queue, mediaKey), persist = true)
        loadCurrent(autoPlay = autoPlay)
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
        updatePlayback(engine.state.value)
        engine.setPlaybackSpeed(queue.playbackSpeed)
        if (autoPlay) engine.play()
    }

    private suspend fun recoverCurrentEndpointLocked(
        playback: PlaybackState,
    ) {
        val item = queue.currentItem ?: run {
            setError(playback.errorMessage ?: "播放失败")
            return
        }
        if (endpointRecoveryUsedForMediaKey == item.mediaKey) {
            setError(playback.errorMessage ?: "播放失败")
            return
        }
        endpointRecoveryUsedForMediaKey = item.mediaKey
        pendingResumeMediaKey = item.mediaKey
        pendingResumeMs = playback.positionMs.coerceAtLeast(0L)
        resumeApplied = false
        val refreshed = runCatching {
            session.refreshAfterRequestFailure()
        }.getOrElse { error ->
            setError("端点刷新失败：${error.message ?: "未知错误"}")
            return
        }
        when (refreshed) {
            is AppResult.Success -> {
                updateSession(error = null)
                loadedMediaKey = null
                loadCurrent(
                    autoPlay = mutableSessionState.value.playWhenReady,
                )
            }

            is AppResult.Failure -> setError(refreshed.error.userMessage)
        }
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
        val currentMediaKey = queue.currentMediaKey
        if (currentMediaKey != next.currentMediaKey) {
            persistCurrentPositionLocked(
                ended = mutableSessionState.value.playback.status ==
                    PlaybackStatus.ENDED,
            )
            endpointRecoveryUsedForMediaKey = null
        }
        queue = next
        if (next.items.isEmpty()) {
            loadedMediaKey = null
            clearPendingResume()
            pauseCorrectionIssued = false
            engine.stop()
            updateSession(
                playback = PlaybackState(
                    playbackSpeed = next.playbackSpeed,
                ),
                playWhenReady = false,
            )
        } else {
            updateSession()
        }
        if (!persist) return
        runCatching { queueRepository.save(next) }
            .onFailure { setError(it.message ?: "播放队列保存失败") }
    }

    private suspend fun persistCurrentPositionLocked(
        ended: Boolean = mutableSessionState.value.playback.status ==
            PlaybackStatus.ENDED,
    ) {
        val mediaKey = queue.currentMediaKey ?: return
        val playback = mutableSessionState.value.playback
        runCatching {
            positionStore.record(
                mediaKey = mediaKey,
                positionMs = playback.positionMs,
                durationMs = playback.durationMs,
                updatedAtEpochMs = System.currentTimeMillis(),
                ended = ended,
            )
        }.onFailure {
            setError(it.message ?: "播放状态保存失败")
        }
    }

    private fun updatePlayback(playback: PlaybackState) {
        updateSession(playback = playback)
    }

    private fun setError(message: String) {
        updateSession(error = message)
    }

    private fun updatePlayWhenReady(playWhenReady: Boolean) {
        pauseCorrectionIssued = if (playWhenReady) {
            false
        } else {
            mutableSessionState.value.playback.status == PlaybackStatus.PLAYING
        }
        updateSession(playWhenReady = playWhenReady)
    }

    private fun correctUnexpectedPlaying(playback: PlaybackState) {
        if (playback.status == PlaybackStatus.PAUSED) {
            pauseCorrectionIssued = false
        } else if (
            playback.status == PlaybackStatus.PLAYING &&
            !mutableSessionState.value.playWhenReady &&
            !pauseCorrectionIssued
        ) {
            pauseCorrectionIssued = true
            engine.pause()
        }
    }

    private fun updateSession(
        playback: PlaybackState = mutableSessionState.value.playback,
        playWhenReady: Boolean = mutableSessionState.value.playWhenReady,
        error: String? = mutableSessionState.value.errorMessage,
    ) {
        mutableSessionState.value = PlaybackSessionState(
            playback = playback,
            playWhenReady = playWhenReady,
            queue = queue,
            currentItem = queue.currentItem,
            errorMessage = error,
            canSkipPrevious = QueueNavigator.previous(queue) != null,
            canSkipNext = QueueNavigator.next(
                queue,
                QueueAdvanceReason.USER,
            ) != null,
        )
    }

    private fun clearPendingResume() {
        pendingResumeMediaKey = null
        pendingResumeMs = null
        resumeApplied = false
    }
}
