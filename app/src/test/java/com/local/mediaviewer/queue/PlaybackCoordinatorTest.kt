package com.local.mediaviewer.queue

import android.view.ViewGroup
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.playback.PlaybackEngine
import com.local.mediaviewer.playback.PlaybackDemuxStrategy
import com.local.mediaviewer.playback.PlaybackPositionStore
import com.local.mediaviewer.playback.PlaybackSource
import com.local.mediaviewer.playback.PlaybackSourceResolver
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.playback.PassthroughPlaybackSourceResolver
import com.local.mediaviewer.playback.VideoScaleMode
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackCoordinatorTest {
    @Test
    fun `queue load passes resolved demux strategy to engine`() = runTest {
        val engine = FakeEngine()
        val resolver = PlaybackSourceResolver { url ->
            PlaybackSource(url, PlaybackDemuxStrategy.AVFORMAT)
        }
        val coordinator = coordinator(
            engine = engine,
            sourceResolver = resolver,
            scope = this,
        )

        coordinator.replaceQueue(listOf(item("a")), "a")
        advanceUntilIdle()

        assertEquals(
            PlaybackSource(requestUrlFor("a"), PlaybackDemuxStrategy.AVFORMAT),
            engine.prepareSources.single(),
        )
        coordinator.close()
    }

    @Test
    fun `direct prepare also uses source resolver`() = runTest {
        val engine = FakeEngine()
        val resolver = PlaybackSourceResolver { url ->
            PlaybackSource(url, PlaybackDemuxStrategy.AVFORMAT)
        }
        val coordinator = coordinator(engine, sourceResolver = resolver, scope = this)

        coordinator.prepare("http://10.0.0.9:8080/direct.mp4")
        advanceUntilIdle()

        assertEquals(PlaybackDemuxStrategy.AVFORMAT, engine.prepareSources.single().demuxStrategy)
        coordinator.close()
    }

    @Test
    fun `endpoint recovery resolves the refreshed request url`() = runTest {
        val resolvedUrls = mutableListOf<String>()
        val resolver = PlaybackSourceResolver { url ->
            resolvedUrls += url
            PlaybackSource(url, PlaybackDemuxStrategy.AVFORMAT)
        }
        val session = FakeSession(
            refreshedEndpoint = SessionEndpoint(
                logicalBaseUrl = "http://media.example:8080",
                requestBaseUrl = "http://10.0.0.10:8080",
                ipv4 = "10.0.0.10",
            ),
        )
        val engine = FakeEngine()
        val coordinator = coordinator(
            engine = engine,
            session = session,
            sourceResolver = resolver,
            scope = this,
        )
        coordinator.replaceQueue(listOf(item("a")), "a")
        advanceUntilIdle()

        engine.emit(PlaybackState(status = PlaybackStatus.ERROR, errorMessage = "old endpoint"))
        advanceUntilIdle()

        assertEquals(
            listOf(
                "http://10.0.0.9:8080/a.mp4",
                "http://10.0.0.10:8080/a.mp4",
            ),
            resolvedUrls,
        )
        assertEquals(
            PlaybackDemuxStrategy.AVFORMAT,
            engine.prepareSources.last().demuxStrategy,
        )
        coordinator.close()
    }

    @Test
    fun `serialized probe cannot apply old media after newer media`() = runTest {
        val firstProbeGate = CompletableDeferred<Unit>()
        val resolver = PlaybackSourceResolver { url ->
            if (url.endsWith("/a.mp4")) firstProbeGate.await()
            PlaybackSource(url)
        }
        val engine = FakeEngine()
        val coordinator = coordinator(engine, sourceResolver = resolver, scope = this)

        coordinator.replaceQueue(listOf(item("a"), item("b")), "a")
        runCurrent()
        coordinator.select("b")
        firstProbeGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            listOf(requestUrlFor("a"), requestUrlFor("b")),
            engine.prepareSources.map(PlaybackSource::url),
        )
        assertEquals("b", coordinator.sessionState.value.currentItem?.mediaKey)
        coordinator.close()
    }

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
    fun `播放错误刷新后停在当前项且不自动跳到下一项`() = runTest {
        val engine = FakeEngine()
        val coordinator = coordinator(engine, scope = this)

        coordinator.replaceQueue(listOf(item("a"), item("b")), "a")
        advanceUntilIdle()
        engine.emit(PlaybackState(status = PlaybackStatus.ERROR, errorMessage = "network"))
        advanceUntilIdle()

        assertEquals(
            listOf(requestUrlFor("a"), requestUrlFor("a")),
            engine.prepareCalls,
        )
        assertEquals("a", coordinator.sessionState.value.currentItem?.mediaKey)
        assertNull(coordinator.sessionState.value.errorMessage)
        coordinator.close()
    }

    @Test
    fun `顺序队列到末尾清除播放意图并停止引擎`() = runTest {
        val engine = FakeEngine()
        val coordinator = coordinator(engine, scope = this)
        coordinator.replaceQueue(listOf(item("a")), "a")
        advanceUntilIdle()

        engine.emit(PlaybackState(status = PlaybackStatus.ENDED))
        advanceUntilIdle()

        assertFalse(coordinator.sessionState.value.playWhenReady)
        assertEquals(1, engine.stopCalls)
        coordinator.close()
    }

    @Test
    fun `删除唯一当前项统一清除播放意图并停止引擎`() = runTest {
        val engine = FakeEngine()
        val coordinator = coordinator(engine, scope = this)
        coordinator.replaceQueue(listOf(item("a")), "a")
        advanceUntilIdle()
        val stopsBefore = engine.stopCalls

        coordinator.remove("a")
        advanceUntilIdle()

        assertTrue(coordinator.sessionState.value.queue.items.isEmpty())
        assertFalse(coordinator.sessionState.value.playWhenReady)
        assertTrue(engine.stopCalls > stopsBefore)
        coordinator.close()
    }

    @Test
    fun `替换为空队列统一清除播放意图并停止引擎`() = runTest {
        val engine = FakeEngine()
        val coordinator = coordinator(engine, scope = this)
        coordinator.replaceQueue(listOf(item("a")), "a")
        advanceUntilIdle()
        val stopsBefore = engine.stopCalls

        coordinator.replaceQueue(emptyList(), "missing")
        advanceUntilIdle()

        assertTrue(coordinator.sessionState.value.queue.items.isEmpty())
        assertFalse(coordinator.sessionState.value.playWhenReady)
        assertTrue(engine.stopCalls > stopsBefore)
        coordinator.close()
    }

    @Test
    fun `Media3 批量删除全部项目统一清除意图并停止引擎`() = runTest {
        val engine = FakeEngine()
        val coordinator = coordinator(engine, scope = this)
        coordinator.replaceQueue(listOf(item("a"), item("b")), "a")
        advanceUntilIdle()
        val stopsBefore = engine.stopCalls

        coordinator.removeRange(0, 2)
        advanceUntilIdle()

        assertTrue(coordinator.sessionState.value.queue.items.isEmpty())
        assertFalse(coordinator.sessionState.value.playWhenReady)
        assertTrue(engine.stopCalls > stopsBefore)
        coordinator.close()
    }

    @Test
    fun `Media3 设置空播放列表统一清除意图并停止引擎`() = runTest {
        val engine = FakeEngine()
        val coordinator = coordinator(engine, scope = this)
        coordinator.replaceQueue(listOf(item("a")), "a")
        advanceUntilIdle()
        val stopsBefore = engine.stopCalls

        coordinator.replaceFromMedia3(
            items = emptyList(),
            startIndex = 0,
            startPositionMs = 0L,
        )
        advanceUntilIdle()

        assertTrue(coordinator.sessionState.value.queue.items.isEmpty())
        assertFalse(coordinator.sessionState.value.playWhenReady)
        assertTrue(engine.stopCalls > stopsBefore)
        coordinator.close()
    }

    @Test
    fun `随机队列到末尾清除播放意图并停止引擎`() = runTest {
        val engine = FakeEngine()
        val coordinator = coordinator(engine, scope = this)
        coordinator.setPlaybackMode(PlaybackMode.SHUFFLE)
        coordinator.replaceQueue(listOf(item("a")), "a")
        advanceUntilIdle()
        val stopsBefore = engine.stopCalls

        engine.emit(PlaybackState(status = PlaybackStatus.ENDED))
        advanceUntilIdle()

        assertFalse(coordinator.sessionState.value.playWhenReady)
        assertTrue(engine.stopCalls > stopsBefore)
        coordinator.close()
    }

    @Test
    fun `启动路径冷恢复不触发引擎直到用户播放并只恢复一次位置`() = runTest {
        val engine = FakeEngine()
        val repository = FakeQueueRepository(
            PlaybackQueue(items = listOf(item("a")), currentMediaKey = "a"),
        )
        val positions = FakePositionStore(mapOf("a" to 30_000L))
        val coordinator = coordinator(
            engine = engine,
            repository = repository,
            positions = positions,
            scope = this,
        )

        coordinator.start()
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
    fun `替换队列保留已选择的播放倍速`() = runTest {
        val engine = FakeEngine()
        val coordinator = coordinator(engine, scope = this)

        coordinator.setPlaybackSpeed(1.5f)
        coordinator.replaceQueue(listOf(item("a")), "a")
        advanceUntilIdle()

        assertEquals(1.5f, coordinator.sessionState.value.queue.playbackSpeed)
        coordinator.close()
    }

    @Test
    fun `queue save failure emits a notice and keeps the in-memory queue`() = runTest {
        val repository = FakeQueueRepository(
            saveFailure = IOException("queue disk full"),
        )
        val coordinator = coordinator(FakeEngine(), repository, scope = this)
        val notice = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.notices.first()
        }

        coordinator.append(item("a"))
        advanceUntilIdle()

        assertEquals(
            listOf("a"),
            coordinator.sessionState.value.queue.items.map { it.mediaKey },
        )
        assertEquals(PlaybackNoticeKind.QUEUE_SAVE_FAILED, notice.await().kind)
        assertNull(coordinator.sessionState.value.errorMessage)
        coordinator.close()
    }

    @Test
    fun `position save failure emits once and retry persists the current snapshot`() =
        runTest {
            val repository = FakeQueueRepository()
            val positions = FakePositionStore()
            val coordinator = coordinator(
                FakeEngine(),
                repository = repository,
                positions = positions,
                scope = this,
            )
            coordinator.replaceQueue(listOf(item("a")), "a")
            advanceUntilIdle()
            positions.recordFailure = IOException("position disk full")
            val notice = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.notices.first()
            }

            coordinator.saveCurrentSnapshot()
            advanceUntilIdle()

            assertEquals(
                PlaybackNoticeKind.POSITION_SAVE_FAILED,
                notice.await().kind,
            )
            assertEquals(1, positions.recordCalls)
            assertEquals("a", coordinator.sessionState.value.currentItem?.mediaKey)
            assertNull(coordinator.sessionState.value.errorMessage)

            positions.recordFailure = null
            coordinator.saveCurrentSnapshot()
            advanceUntilIdle()

            assertEquals(2, positions.recordCalls)
            coordinator.close()
        }

    @Test
    fun `notice emitted without a collector is not replayed`() = runTest {
        val repository = FakeQueueRepository(
            saveFailure = IOException("first failure"),
        )
        val coordinator = coordinator(
            FakeEngine(),
            repository = repository,
            scope = this,
        )

        coordinator.append(item("a"))
        advanceUntilIdle()

        repository.saveFailure = IOException("second failure")
        val notice = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.notices.first()
        }
        coordinator.append(item("b"))
        advanceUntilIdle()
        val received = notice.await()

        assertEquals(PlaybackNoticeKind.QUEUE_SAVE_FAILED, received.kind)
        assertEquals("second failure", received.message)
        coordinator.close()
    }

    @Test
    fun `媒体会话暂停立即保存当前项进度`() = runTest {
        val engine = FakeEngine()
        val positions = FakePositionStore()
        val coordinator = coordinator(engine, positions = positions, scope = this)
        coordinator.replaceQueue(listOf(item("a")), "a")
        advanceUntilIdle()
        engine.emit(
            PlaybackState(
                status = PlaybackStatus.PLAYING,
                positionMs = 12_000L,
                durationMs = 60_000L,
            ),
        )
        advanceUntilIdle()

        coordinator.setPlayWhenReadyFromSession(false)

        assertEquals(
            PositionRecord("a", 12_000L, 60_000L, false),
            positions.records.last(),
        )
        coordinator.close()
    }

    @Test
    fun `切换当前项前只把旧引擎位置保存给旧媒体`() = runTest {
        val engine = FakeEngine()
        val positions = FakePositionStore()
        val coordinator = coordinator(engine, positions = positions, scope = this)
        coordinator.replaceQueue(listOf(item("a"), item("b")), "a")
        advanceUntilIdle()
        engine.emit(
            PlaybackState(
                status = PlaybackStatus.PLAYING,
                positionMs = 21_000L,
                durationMs = 80_000L,
            ),
        )
        advanceUntilIdle()

        coordinator.select("b")
        advanceUntilIdle()

        assertTrue(
            positions.records.contains(
                PositionRecord("a", 21_000L, 80_000L, false),
            ),
        )
        assertFalse(
            positions.records.any {
                it.mediaKey == "b" && it.positionMs == 21_000L
            },
        )
        coordinator.close()
    }

    @Test
    fun `自动播放结束在切换下一项前按结束状态保存旧媒体`() = runTest {
        val engine = FakeEngine()
        val positions = FakePositionStore()
        val coordinator = coordinator(engine, positions = positions, scope = this)
        coordinator.replaceQueue(listOf(item("a"), item("b")), "a")
        advanceUntilIdle()

        engine.emit(
            PlaybackState(
                status = PlaybackStatus.ENDED,
                positionMs = 90_000L,
                durationMs = 90_000L,
            ),
        )
        advanceUntilIdle()

        assertTrue(
            positions.records.contains(
                PositionRecord("a", 90_000L, 90_000L, true),
            ),
        )
        assertFalse(
            positions.records.any {
                it.mediaKey == "b" && it.positionMs == 90_000L
            },
        )
        coordinator.close()
    }

    @Test
    fun `后台播放错误刷新端点并在原项原位置恢复且不跳项`() = runTest {
        val engine = FakeEngine()
        val session = FakeSession(
            refreshedEndpoint = SessionEndpoint(
                logicalBaseUrl = "http://media.example:8080",
                requestBaseUrl = "http://10.0.0.10:8080",
                ipv4 = "10.0.0.10",
            ),
        )
        val coordinator = coordinator(engine, session = session, scope = this)
        coordinator.replaceQueue(listOf(item("a"), item("b")), "a")
        advanceUntilIdle()

        engine.emit(
            PlaybackState(
                status = PlaybackStatus.ERROR,
                positionMs = 34_000L,
                durationMs = 100_000L,
                errorMessage = "旧端点失效",
            ),
        )
        advanceUntilIdle()
        engine.emit(
            PlaybackState(
                status = PlaybackStatus.OPENING,
                durationMs = 100_000L,
                isSeekable = true,
            ),
        )
        advanceUntilIdle()

        assertEquals(1, session.refreshCalls)
        assertEquals("a", coordinator.sessionState.value.currentItem?.mediaKey)
        assertEquals(2, coordinator.sessionState.value.queue.items.size)
        assertEquals("http://10.0.0.10:8080/a.mp4", engine.prepareCalls.last())
        assertEquals(34_000L, engine.seekCalls.last())
        coordinator.close()
    }

    @Test
    fun `同一错误风暴只刷新一次而成功播放后允许下一次刷新`() = runTest {
        val engine = FakeEngine()
        val session = FakeSession()
        val coordinator = coordinator(engine, session = session, scope = this)
        coordinator.replaceQueue(listOf(item("a"), item("b")), "a")
        advanceUntilIdle()

        engine.emit(PlaybackState(status = PlaybackStatus.ERROR, errorMessage = "1"))
        advanceUntilIdle()
        engine.emit(PlaybackState(status = PlaybackStatus.OPENING))
        engine.emit(PlaybackState(status = PlaybackStatus.ERROR, errorMessage = "2"))
        advanceUntilIdle()
        assertEquals(1, session.refreshCalls)

        engine.emit(PlaybackState(status = PlaybackStatus.PLAYING))
        advanceUntilIdle()
        engine.emit(PlaybackState(status = PlaybackStatus.ERROR, errorMessage = "3"))
        advanceUntilIdle()

        assertEquals(2, session.refreshCalls)
        assertEquals("a", coordinator.sessionState.value.currentItem?.mediaKey)
        coordinator.close()
    }

    @Test
    fun `后台端点刷新失败显示中文错误且不自动跳项`() = runTest {
        val engine = FakeEngine()
        val session = FakeSession(
            refreshResult = AppResult.Failure(
                AppError.NetworkFailure("离线"),
            ),
        )
        val coordinator = coordinator(engine, session = session, scope = this)
        coordinator.replaceQueue(listOf(item("a"), item("b")), "a")
        advanceUntilIdle()

        engine.emit(PlaybackState(status = PlaybackStatus.ERROR, errorMessage = "failed"))
        advanceUntilIdle()

        assertEquals("网络连接失败：离线", coordinator.sessionState.value.errorMessage)
        assertEquals("a", coordinator.sessionState.value.currentItem?.mediaKey)
        assertEquals(1, engine.prepareCalls.size)
        coordinator.close()
    }

    private fun coordinator(
        engine: FakeEngine,
        repository: FakeQueueRepository = FakeQueueRepository(),
        positions: FakePositionStore = FakePositionStore(),
        session: FakeSession = FakeSession(),
        sourceResolver: PlaybackSourceResolver = PassthroughPlaybackSourceResolver,
        scope: CoroutineScope,
    ) = PlaybackCoordinator(
        engine = engine,
        queueRepository = repository,
        positionStore = positions,
        session = session,
        sourceResolver = sourceResolver,
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
    val prepareSources = mutableListOf<PlaybackSource>()
    val seekCalls = mutableListOf<Long>()
    var playCalls = 0
    var stopCalls = 0

    fun emit(state: PlaybackState) {
        mutableState.value = state
    }

    override fun prepare(url: String) {
        prepareCalls += url
        mutableState.value = PlaybackState(
            status = PlaybackStatus.OPENING,
            playbackSpeed = mutableState.value.playbackSpeed,
        )
    }

    override fun prepare(source: PlaybackSource) {
        prepareSources += source
        prepare(source.url)
    }

    override fun attachVideoOutput(host: ViewGroup) = Unit
    override fun detachVideoOutput() = Unit
    override fun setVideoScaleMode(mode: VideoScaleMode) = Unit
    override fun setPlaybackSpeed(speed: Float) = Unit
    override fun play() {
        playCalls += 1
    }
    override fun pause() = Unit
    override fun stop() {
        stopCalls += 1
    }
    override fun seekTo(positionMs: Long) {
        seekCalls += positionMs
    }
    override fun close() = Unit
}

private class FakeQueueRepository(
    initial: PlaybackQueue = PlaybackQueue(),
    var saveFailure: Throwable? = null,
) : PlaybackQueueRepository {
    private val mutableQueue = MutableStateFlow(initial)
    override val queue: StateFlow<PlaybackQueue> = mutableQueue
    var saveCalls = 0
        private set

    override suspend fun restore(): PlaybackQueue = mutableQueue.value

    override suspend fun save(queue: PlaybackQueue) {
        saveCalls += 1
        saveFailure?.let { throw it }
        mutableQueue.value = queue
    }
}

private class FakePositionStore(
    private val positions: Map<String, Long> = emptyMap(),
    var recordFailure: Throwable? = null,
) : PlaybackPositionStore {
    val records = mutableListOf<PositionRecord>()
    var recordCalls = 0
        private set

    override suspend fun resumePosition(mediaKey: String): Long? = positions[mediaKey]

    override suspend fun record(mediaKey: String, positionMs: Long, durationMs: Long, updatedAtEpochMs: Long, ended: Boolean) {
        recordCalls += 1
        recordFailure?.let { throw it }
        records += PositionRecord(mediaKey, positionMs, durationMs, ended)
    }

    override suspend fun clear(mediaKey: String) = Unit
}

private data class PositionRecord(
    val mediaKey: String,
    val positionMs: Long,
    val durationMs: Long,
    val ended: Boolean,
)

private class FakeSession(
    private val refreshedEndpoint: SessionEndpoint = SessionEndpoint(
        logicalBaseUrl = "http://media.example:8080",
        requestBaseUrl = "http://10.0.0.9:8080",
        ipv4 = "10.0.0.9",
    ),
    private val refreshResult: AppResult<SessionEndpoint>? = null,
) : ServerSessionManager {
    private val endpoint = SessionEndpoint(
        logicalBaseUrl = "http://media.example:8080",
        requestBaseUrl = "http://10.0.0.9:8080",
        ipv4 = "10.0.0.9",
    )
    private val mutableState = MutableStateFlow<ServerSessionState>(
        ServerSessionState.Connected(endpoint, listOf(endpoint.ipv4)),
    )
    override val state: StateFlow<ServerSessionState> = mutableState
    var refreshCalls = 0

    override suspend fun connectSaved() = Unit
    override suspend fun testCandidate(input: String): AppResult<com.local.mediaviewer.network.ConnectionTestResult> = error("unused")
    override suspend fun saveCandidate(result: com.local.mediaviewer.network.ConnectionTestResult) = Unit
    override suspend fun refreshAfterRequestFailure(): AppResult<SessionEndpoint> {
        refreshCalls += 1
        val result = refreshResult ?: AppResult.Success(refreshedEndpoint)
        if (result is AppResult.Success) {
            mutableState.value = ServerSessionState.Connected(
                result.value,
                listOf(result.value.ipv4),
            )
        }
        return result
    }
}
