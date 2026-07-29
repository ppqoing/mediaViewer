package com.local.mediaviewer.queue

import android.view.ViewGroup
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.playback.PlaybackEngine
import com.local.mediaviewer.playback.PlaybackPositionStore
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.playback.VideoScaleMode
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackCoordinatorTest {
    @Test
    fun `播放结束自动准备并播放顺序队列下一项`() = runTest {
        val engine = FakeEngine()
        val coordinator = coordinator(engine, scope = this)

        coordinator.replaceQueue(listOf(item("a"), item("b")), "a")
        advanceUntilIdle()
        engine.emit(PlaybackState(status = PlaybackStatus.ENDED))
        advanceUntilIdle()

        assertEquals(listOf(requestUrlFor("a"), requestUrlFor("b")), engine.prepareCalls)
        assertEquals(2, engine.playCalls)
        assertEquals("b", coordinator.sessionState.value.currentItem?.mediaKey)
        coordinator.close()
    }

    @Test
    fun `播放错误停在当前项且不自动跳到下一项`() = runTest {
        val engine = FakeEngine()
        val coordinator = coordinator(engine, scope = this)

        coordinator.replaceQueue(listOf(item("a"), item("b")), "a")
        advanceUntilIdle()
        engine.emit(PlaybackState(status = PlaybackStatus.ERROR, errorMessage = "network"))
        advanceUntilIdle()

        assertEquals(listOf(requestUrlFor("a")), engine.prepareCalls)
        assertEquals("a", coordinator.sessionState.value.currentItem?.mediaKey)
        assertEquals("network", coordinator.sessionState.value.errorMessage)
        coordinator.close()
    }

    @Test
    fun `冷恢复不触发引擎直到用户播放并只恢复一次位置`() = runTest {
        val engine = FakeEngine()
        val repository = FakeQueueRepository(
            PlaybackQueue(items = listOf(item("a")), currentMediaKey = "a"),
        )
        val positions = FakePositionStore(mapOf("a" to 30_000L))
        val coordinator = coordinator(engine, repository, positions, this)

        coordinator.restore(autoPlay = false)
        advanceUntilIdle()

        assertTrue(engine.prepareCalls.isEmpty())
        assertTrue(engine.seekCalls.isEmpty())
        assertEquals(0, engine.playCalls)
        assertEquals(PlaybackStatus.PAUSED, coordinator.sessionState.value.playback.status)

        coordinator.play()
        advanceUntilIdle()
        engine.emit(PlaybackState(status = PlaybackStatus.OPENING, isSeekable = true, durationMs = 60_000L))
        advanceUntilIdle()
        engine.emit(PlaybackState(status = PlaybackStatus.PLAYING, isSeekable = true, durationMs = 60_000L))
        advanceUntilIdle()

        assertEquals(listOf(requestUrlFor("a")), engine.prepareCalls)
        assertEquals(listOf(30_000L), engine.seekCalls)
        assertEquals(1, engine.playCalls)
        coordinator.close()
    }

    @Test
    fun `保存队列失败仍保留内存队列并显示非阻塞错误`() = runTest {
        val repository = FakeQueueRepository(saveFailure = IllegalStateException("disk full"))
        val coordinator = coordinator(FakeEngine(), repository, scope = this)

        coordinator.append(item("a"))
        advanceUntilIdle()

        assertEquals("a", coordinator.sessionState.value.queue.items.single().mediaKey)
        assertEquals("disk full", coordinator.sessionState.value.errorMessage)
        coordinator.close()
    }

    private fun coordinator(
        engine: FakeEngine,
        repository: FakeQueueRepository = FakeQueueRepository(),
        positions: FakePositionStore = FakePositionStore(),
        scope: CoroutineScope,
    ) = PlaybackCoordinator(
        engine = engine,
        queueRepository = repository,
        positionStore = positions,
        session = FakeSession(),
        scope = scope,
    )

    private fun item(key: String) = QueueMediaItem(
        mediaKey = key,
        name = key,
        logicalUrl = "http://media.example:8080/$key.mp4",
        kind = MediaKind.VIDEO,
    )

    private fun requestUrlFor(key: String) = "http://10.0.0.9:8080/$key.mp4"
}

private class FakeEngine : PlaybackEngine {
    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState
    val prepareCalls = mutableListOf<String>()
    val seekCalls = mutableListOf<Long>()
    var playCalls = 0

    fun emit(state: PlaybackState) {
        mutableState.value = state
    }

    override fun prepare(url: String) {
        prepareCalls += url
    }

    override fun attachVideoOutput(host: ViewGroup) = Unit
    override fun detachVideoOutput() = Unit
    override fun setVideoScaleMode(mode: VideoScaleMode) = Unit
    override fun setPlaybackSpeed(speed: Float) = Unit
    override fun play() {
        playCalls += 1
    }
    override fun pause() = Unit
    override fun stop() = Unit
    override fun seekTo(positionMs: Long) {
        seekCalls += positionMs
    }
    override fun close() = Unit
}

private class FakeQueueRepository(
    initial: PlaybackQueue = PlaybackQueue(),
    private val saveFailure: Throwable? = null,
) : PlaybackQueueRepository {
    private val mutableQueue = MutableStateFlow(initial)
    override val queue: StateFlow<PlaybackQueue> = mutableQueue

    override suspend fun restore(): PlaybackQueue = mutableQueue.value

    override suspend fun save(queue: PlaybackQueue) {
        saveFailure?.let { throw it }
        mutableQueue.value = queue
    }
}

private class FakePositionStore(
    private val positions: Map<String, Long> = emptyMap(),
) : PlaybackPositionStore {
    override suspend fun resumePosition(mediaKey: String): Long? = positions[mediaKey]
    override suspend fun record(mediaKey: String, positionMs: Long, durationMs: Long, updatedAtEpochMs: Long, ended: Boolean) = Unit
    override suspend fun clear(mediaKey: String) = Unit
}

private class FakeSession : ServerSessionManager {
    private val endpoint = SessionEndpoint(
        logicalBaseUrl = "http://media.example:8080",
        requestBaseUrl = "http://10.0.0.9:8080",
        ipv4 = "10.0.0.9",
    )
    override val state: StateFlow<ServerSessionState> = MutableStateFlow(
        ServerSessionState.Connected(endpoint, listOf(endpoint.ipv4)),
    )

    override suspend fun connectSaved() = Unit
    override suspend fun testCandidate(input: String): AppResult<com.local.mediaviewer.network.ConnectionTestResult> = error("unused")
    override suspend fun saveCandidate(result: com.local.mediaviewer.network.ConnectionTestResult) = Unit
    override suspend fun refreshAfterRequestFailure(): AppResult<SessionEndpoint> = error("unused")
}
