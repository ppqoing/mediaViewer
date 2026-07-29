package com.local.mediaviewer.service

import android.view.ViewGroup
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.playback.PlaybackEngine
import com.local.mediaviewer.playback.PlaybackPositionStore
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.VideoScaleMode
import com.local.mediaviewer.queue.PlaybackCoordinator
import com.local.mediaviewer.queue.PlaybackQueue
import com.local.mediaviewer.queue.PlaybackQueueRepository
import com.local.mediaviewer.queue.QueueMediaItem
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal fun serviceTestCoordinator(
    scope: CoroutineScope,
    engine: ServiceTestEngine = ServiceTestEngine(),
    repository: ServiceTestQueueRepository = ServiceTestQueueRepository(),
    positions: ServiceTestPositionStore = ServiceTestPositionStore(),
): PlaybackCoordinator = PlaybackCoordinator(
    engine = engine,
    queueRepository = repository,
    positionStore = positions,
    session = ServiceTestSession(),
    scope = scope,
)

internal fun serviceTestItem(key: String): QueueMediaItem = QueueMediaItem(
    mediaKey = key,
    name = "媒体 $key",
    logicalUrl = "http://media.example:8080/$key.mp4",
    kind = MediaKind.VIDEO,
)

internal class ServiceTestEngine : PlaybackEngine {
    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState
    val attachedHosts = mutableListOf<ViewGroup>()
    var detachCalls = 0
    val scaleModes = mutableListOf<VideoScaleMode>()
    var prepareCalls = 0
    var playCalls = 0

    override fun prepare(url: String) {
        prepareCalls += 1
    }

    override fun attachVideoOutput(host: ViewGroup) {
        attachedHosts += host
    }

    override fun detachVideoOutput() {
        detachCalls += 1
    }

    override fun setVideoScaleMode(mode: VideoScaleMode) {
        scaleModes += mode
    }

    override fun setPlaybackSpeed(speed: Float) = Unit

    override fun play() {
        playCalls += 1
    }

    override fun pause() = Unit

    override fun stop() = Unit

    override fun seekTo(positionMs: Long) = Unit

    override fun close() = Unit
}

internal class ServiceTestQueueRepository(
    initial: PlaybackQueue = PlaybackQueue(),
    private val restoreFailure: Throwable? = null,
) : PlaybackQueueRepository {
    private val mutableQueue = MutableStateFlow(initial)
    override val queue: StateFlow<PlaybackQueue> = mutableQueue

    override suspend fun restore(): PlaybackQueue {
        restoreFailure?.let { throw it }
        return mutableQueue.value
    }

    override suspend fun save(queue: PlaybackQueue) {
        mutableQueue.value = queue
    }
}

internal class ServiceTestPositionStore(
    private val positions: Map<String, Long> = emptyMap(),
) : PlaybackPositionStore {
    override suspend fun resumePosition(mediaKey: String): Long? = positions[mediaKey]

    override suspend fun record(
        mediaKey: String,
        positionMs: Long,
        durationMs: Long,
        updatedAtEpochMs: Long,
        ended: Boolean,
    ) = Unit

    override suspend fun clear(mediaKey: String) = Unit
}

private class ServiceTestSession : ServerSessionManager {
    private val endpoint = SessionEndpoint(
        logicalBaseUrl = "http://media.example:8080",
        requestBaseUrl = "http://10.0.0.9:8080",
        ipv4 = "10.0.0.9",
    )
    override val state: StateFlow<ServerSessionState> = MutableStateFlow(
        ServerSessionState.Connected(endpoint, listOf(endpoint.ipv4)),
    )

    override suspend fun connectSaved() = Unit

    override suspend fun testCandidate(
        input: String,
    ): AppResult<com.local.mediaviewer.network.ConnectionTestResult> =
        error("unused")

    override suspend fun saveCandidate(
        result: com.local.mediaviewer.network.ConnectionTestResult,
    ) = Unit

    override suspend fun refreshAfterRequestFailure(): AppResult<SessionEndpoint> =
        error("unused")
}
