package com.local.mediaviewer.player

import android.view.SurfaceView
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.network.ConnectionTestResult
import com.local.mediaviewer.playback.PlaybackEngine
import com.local.mediaviewer.playback.PlaybackPositionStore
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun before() = Dispatchers.setMain(dispatcher)

    @After
    fun after() = Dispatchers.resetMain()

    @Test
    fun `准备播放并在获得时长后恢复位置`() = runTest(dispatcher) {
        val engine = FakeEngine()
        val store = FakeStore(resume = 30_000)
        val viewModel = PlayerViewModel(
            request(),
            engine,
            store,
            FakePlayerSession(),
            clock = { 123L },
        )
        runCurrent()
        assertEquals(listOf(request().requestUrl), engine.preparedUrls)
        assertEquals(1, engine.playCalls)

        engine.emit(
            PlaybackState(
                status = PlaybackStatus.PLAYING,
                durationMs = 100_000,
                positionMs = 1_000,
                isSeekable = true,
            ),
        )
        runCurrent()

        assertEquals(listOf(30_000L), engine.seeks)
        assertEquals(30_000L, viewModel.uiState.value.resumedFromMs)
        viewModel.leave {}
        runCurrent()
    }

    @Test
    fun `每五秒暂停和结束使用当前快照写入`() = runTest(dispatcher) {
        val engine = FakeEngine()
        val store = FakeStore()
        val viewModel = PlayerViewModel(
            request(),
            engine,
            store,
            FakePlayerSession(),
            clock = { 456L },
        )
        runCurrent()
        engine.emit(
            PlaybackState(
                PlaybackStatus.PLAYING,
                positionMs = 20_000,
                durationMs = 100_000,
                isSeekable = true,
            ),
        )
        advanceTimeBy(5_001)
        assertTrue(store.records.any { it.positionMs == 20_000L })

        viewModel.pause()
        runCurrent()
        assertTrue(store.records.size >= 2)

        engine.emit(
            PlaybackState(
                PlaybackStatus.ENDED,
                positionMs = 100_000,
                durationMs = 100_000,
            ),
        )
        runCurrent()
        assertTrue(store.records.last().ended)
        viewModel.leave {}
        runCurrent()
        assertTrue(store.records.last().ended)
    }

    @Test
    fun `第一次错误刷新 IPv4 并只重试一次`() = runTest(dispatcher) {
        val engine = FakeEngine()
        val session = FakePlayerSession(
            refreshed = SessionEndpoint(
                "http://media.example:8080",
                "http://192.0.2.2:8080",
                "192.0.2.2",
            ),
        )
        val viewModel = PlayerViewModel(
            request(),
            engine,
            FakeStore(),
            session,
            clock = { 1L },
        )
        runCurrent()

        engine.emit(
            PlaybackState(
                status = PlaybackStatus.ERROR,
                errorMessage = "第一次失败",
            ),
        )
        runCurrent()
        assertEquals(1, session.refreshCalls)
        assertTrue(
            engine.preparedUrls.last().startsWith(
                "http://192.0.2.2:8080/",
            ),
        )

        engine.emit(
            PlaybackState(
                status = PlaybackStatus.ERROR,
                errorMessage = "仍然失败",
            ),
        )
        runCurrent()
        assertEquals(1, session.refreshCalls)
        assertEquals("仍然失败", viewModel.uiState.value.errorMessage)
        viewModel.leave {}
        runCurrent()
    }

    @Test
    fun `后台和离开保存快照且重复离开只完成一次`() =
        runTest(dispatcher) {
            val engine = FakeEngine()
            val store = FakeStore()
            val viewModel = PlayerViewModel(
                request(),
                engine,
                store,
                FakePlayerSession(),
                clock = { 789L },
            )
            runCurrent()
            engine.emit(
                PlaybackState(
                    status = PlaybackStatus.PLAYING,
                    positionMs = 40_000,
                    durationMs = 100_000,
                    isSeekable = true,
                ),
            )
            runCurrent()

            viewModel.onBackgrounded()
            runCurrent()
            assertEquals(1, engine.pauseCalls)
            assertEquals(40_000L, store.records.last().positionMs)

            var leaveCallbacks = 0
            viewModel.leave { leaveCallbacks += 1 }
            viewModel.leave { leaveCallbacks += 1 }
            runCurrent()

            assertEquals(1, engine.closeCalls)
            assertEquals(1, leaveCallbacks)
            assertTrue(store.records.size >= 2)
        }
}

private fun request() = PlayerRequest(
    name = "movie.mp4",
    logicalUrl = "http://media.example:8080/middle/movie.mp4",
    requestUrl = "http://192.0.2.1:8080/middle/movie.mp4",
    mediaKey = "http://media.example:8080/middle/movie.mp4",
    kind = MediaKind.VIDEO,
)

private class FakeEngine : PlaybackEngine {
    private val mutable = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutable
    val preparedUrls = mutableListOf<String>()
    val seeks = mutableListOf<Long>()
    var playCalls = 0
    var pauseCalls = 0
    var closeCalls = 0

    override fun prepare(url: String) {
        preparedUrls += url
    }

    override fun attachVideoSurface(surfaceView: SurfaceView) = Unit

    override fun detachVideoSurface() = Unit

    override fun play() {
        playCalls += 1
    }

    override fun pause() {
        pauseCalls += 1
    }

    override fun seekTo(positionMs: Long) {
        seeks += positionMs
    }

    override fun close() {
        closeCalls += 1
    }

    fun emit(state: PlaybackState) {
        mutable.value = state
    }
}

private data class SavedRecord(
    val positionMs: Long,
    val durationMs: Long,
    val ended: Boolean,
)

private class FakeStore(
    private val resume: Long? = null,
) : PlaybackPositionStore {
    val records = mutableListOf<SavedRecord>()

    override suspend fun resumePosition(mediaKey: String) = resume

    override suspend fun record(
        mediaKey: String,
        positionMs: Long,
        durationMs: Long,
        updatedAtEpochMs: Long,
        ended: Boolean,
    ) {
        records += SavedRecord(positionMs, durationMs, ended)
    }

    override suspend fun clear(mediaKey: String) = Unit
}

private class FakePlayerSession(
    private val refreshed: SessionEndpoint = SessionEndpoint(
        "http://media.example:8080",
        "http://192.0.2.1:8080",
        "192.0.2.1",
    ),
) : ServerSessionManager {
    private val mutable = MutableStateFlow<ServerSessionState>(
        ServerSessionState.Connected(refreshed, listOf(refreshed.ipv4)),
    )
    override val state: StateFlow<ServerSessionState> = mutable
    var refreshCalls = 0

    override suspend fun connectSaved() = Unit

    override suspend fun testCandidate(input: String) =
        error("not used")

    override suspend fun saveCandidate(result: ConnectionTestResult) = Unit

    override suspend fun refreshAfterRequestFailure():
        AppResult<SessionEndpoint> {
        refreshCalls += 1
        return AppResult.Success(refreshed)
    }
}
