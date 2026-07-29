package com.local.mediaviewer.player

import android.view.ViewGroup
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.network.ConnectionTestResult
import com.local.mediaviewer.playback.PlaybackPositionStore
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.playback.VideoScaleMode
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
        val controller = FakePlaybackController()
        val store = FakeStore(resume = 30_000)
        val viewModel = PlayerViewModel(
            request(),
            controller,
            store,
            FakePlayerSession(),
            clock = { 123L },
        )
        runCurrent()
        assertEquals(listOf(request().requestUrl), controller.preparedUrls)
        assertEquals(1, controller.playCalls)

        controller.emit(
            PlaybackState(
                status = PlaybackStatus.PLAYING,
                durationMs = 100_000,
                positionMs = 1_000,
                isSeekable = true,
            ),
        )
        runCurrent()

        assertEquals(listOf(30_000L), controller.seekCalls)
        assertEquals(30_000L, viewModel.uiState.value.resumedFromMs)
        viewModel.leave {}
        runCurrent()
    }

    @Test
    fun `恢复提示显示后由视图模型清除`() = runTest(dispatcher) {
        val controller = FakePlaybackController()
        val viewModel = PlayerViewModel(
            request(),
            controller,
            FakeStore(resume = 30_000L),
            FakePlayerSession(),
        )
        runCurrent()
        controller.emit(
            PlaybackState(
                status = PlaybackStatus.PLAYING,
                durationMs = 100_000L,
                isSeekable = true,
            ),
        )
        runCurrent()

        viewModel.onResumeHintShown()

        assertEquals(null, viewModel.uiState.value.resumedFromMs)
        viewModel.leave {}
        runCurrent()
    }

    @Test
    fun `重试使用当前请求重新准备并播放`() = runTest(dispatcher) {
        val controller = FakePlaybackController()
        val viewModel = PlayerViewModel(
            request(),
            controller,
            FakeStore(),
            FakePlayerSession(),
        )
        runCurrent()
        val preparesBefore = controller.preparedUrls.size
        val playsBefore = controller.playCalls

        viewModel.retry()

        assertEquals(preparesBefore + 1, controller.preparedUrls.size)
        assertEquals(request().requestUrl, controller.preparedUrls.last())
        assertEquals(playsBefore + 1, controller.playCalls)
        viewModel.leave {}
        runCurrent()
    }

    @Test
    fun `每五秒暂停和结束使用当前快照写入`() = runTest(dispatcher) {
        val controller = FakePlaybackController()
        val store = FakeStore()
        val viewModel = PlayerViewModel(
            request(),
            controller,
            store,
            FakePlayerSession(),
            clock = { 456L },
        )
        runCurrent()
        controller.emit(
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

        controller.emit(
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
        val controller = FakePlaybackController()
        val session = FakePlayerSession(
            refreshed = SessionEndpoint(
                "http://media.example:8080",
                "http://192.0.2.2:8080",
                "192.0.2.2",
            ),
        )
        val viewModel = PlayerViewModel(
            request(),
            controller,
            FakeStore(),
            session,
            clock = { 1L },
        )
        runCurrent()

        controller.emit(
            PlaybackState(
                status = PlaybackStatus.ERROR,
                errorMessage = "第一次失败",
            ),
        )
        runCurrent()
        assertEquals(1, session.refreshCalls)
        assertTrue(
            controller.preparedUrls.last().startsWith(
                "http://192.0.2.2:8080/",
            ),
        )

        controller.emit(
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
    fun `端点刷新后从故障时位置继续播放`() =
        runTest(dispatcher) {
            val controller = FakePlaybackController()
            val viewModel = PlayerViewModel(
                request(),
                controller,
                FakeStore(resume = 30_000),
                FakePlayerSession(
                    refreshed = SessionEndpoint(
                        "http://media.example:8080",
                        "http://192.0.2.2:8080",
                        "192.0.2.2",
                    ),
                ),
            )
            runCurrent()
            controller.emit(
                PlaybackState(
                    status = PlaybackStatus.PLAYING,
                    positionMs = 1_000,
                    durationMs = 100_000,
                    isSeekable = true,
                ),
            )
            runCurrent()
            assertEquals(listOf(30_000L), controller.seekCalls)

            controller.emit(
                PlaybackState(
                    status = PlaybackStatus.PLAYING,
                    positionMs = 40_000,
                    durationMs = 100_000,
                    isSeekable = true,
                ),
            )
            controller.emit(
                PlaybackState(
                    status = PlaybackStatus.ERROR,
                    positionMs = 40_000,
                    durationMs = 100_000,
                    errorMessage = "端点失效",
                ),
            )
            runCurrent()
            controller.emit(
                PlaybackState(
                    status = PlaybackStatus.PLAYING,
                    positionMs = 0,
                    durationMs = 100_000,
                    isSeekable = true,
                ),
            )
            runCurrent()

            assertEquals(
                listOf(30_000L, 40_000L),
                controller.seekCalls,
            )
            viewModel.leave {}
            runCurrent()
        }

    @Test
    fun `后台和离开保存快照且重复离开只完成一次`() =
        runTest(dispatcher) {
            val controller = FakePlaybackController()
            val store = FakeStore()
            val viewModel = PlayerViewModel(
                request(),
                controller,
                store,
                FakePlayerSession(),
                clock = { 789L },
            )
            runCurrent()
            controller.emit(
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
            assertEquals(1, controller.pauseCalls)
            assertEquals(40_000L, store.records.last().positionMs)

            var leaveCallbacks = 0
            viewModel.leave { leaveCallbacks += 1 }
            viewModel.leave { leaveCallbacks += 1 }
            runCurrent()

            assertEquals(1, controller.closeCalls)
            assertEquals(1, leaveCallbacks)
            assertTrue(store.records.size >= 2)
        }

    @Test
    fun `画面模式只更新当前播放器且不重启媒体`() =
        runTest(dispatcher) {
            val controller = FakePlaybackController()
            val viewModel = PlayerViewModel(
                request(),
                controller,
                FakeStore(),
                FakePlayerSession(),
            )
            runCurrent()
            val preparesBefore = controller.preparedUrls.size
            val playsBefore = controller.playCalls

            viewModel.setVideoScaleMode(
                VideoScaleMode.STRETCH,
            )

            assertEquals(
                VideoScaleMode.STRETCH,
                viewModel.uiState.value.videoScaleMode,
            )
            assertEquals(
                listOf(VideoScaleMode.STRETCH),
                controller.scaleModes,
            )
            assertEquals(
                preparesBefore,
                controller.preparedUrls.size,
            )
            assertEquals(playsBefore, controller.playCalls)

            val second = PlayerViewModel(
                request().copy(mediaKey = "second"),
                FakePlaybackController(),
                FakeStore(),
                FakePlayerSession(),
            )
            assertEquals(
                VideoScaleMode.BEST_FIT,
                second.uiState.value.videoScaleMode,
            )

            viewModel.leave {}
            second.leave {}
            runCurrent()
        }

    @Test
    fun `拖动期间不 seek 且结束时只提交一次目标位置`() =
        runTest(dispatcher) {
            val controller = FakePlaybackController()
            val viewModel = PlayerViewModel(
                request(),
                controller,
                FakeStore(),
                FakePlayerSession(),
            )
            runCurrent()
            controller.emit(
                PlaybackState(
                    status = PlaybackStatus.PLAYING,
                    positionMs = 10_000L,
                    durationMs = 60_000L,
                    isSeekable = true,
                ),
            )
            runCurrent()

            viewModel.beginScrub()
            viewModel.previewScrub(20_000L)
            viewModel.previewScrub(30_000L)
            viewModel.previewScrub(40_000L)

            assertEquals(40_000L, viewModel.uiState.value.displayedPositionMs)
            assertTrue(controller.seekCalls.isEmpty())

            viewModel.commitScrub()

            assertEquals(listOf(40_000L), controller.seekCalls)
            viewModel.leave {}
            runCurrent()
        }

    @Test
    fun `快退快进按十秒且截断到媒体边界`() = runTest(dispatcher) {
        val controller = FakePlaybackController()
        val viewModel = PlayerViewModel(
            request(),
            controller,
            FakeStore(),
            FakePlayerSession(),
        )
        runCurrent()
        controller.emit(
            PlaybackState(
                status = PlaybackStatus.PLAYING,
                positionMs = 3_000L,
                durationMs = 60_000L,
                isSeekable = true,
            ),
        )
        runCurrent()

        viewModel.seekBack()
        controller.emit(
            controller.state.value.copy(positionMs = 58_000L),
        )
        runCurrent()
        viewModel.seekForward()

        assertEquals(listOf(0L, 60_000L), controller.seekCalls)
        viewModel.leave {}
        runCurrent()
    }

    @Test
    fun `重播从零开始并继续播放`() = runTest(dispatcher) {
        val controller = FakePlaybackController()
        val viewModel = PlayerViewModel(
            request(),
            controller,
            FakeStore(),
            FakePlayerSession(),
        )
        runCurrent()
        val playsBefore = controller.playCalls

        viewModel.replay()

        assertEquals(listOf(0L), controller.seekCalls)
        assertEquals(playsBefore + 1, controller.playCalls)
        viewModel.leave {}
        runCurrent()
    }

    @Test
    fun `倍速命令委托给控制器`() = runTest(dispatcher) {
        val controller = FakePlaybackController()
        val viewModel = PlayerViewModel(
            request(),
            controller,
            FakeStore(),
            FakePlayerSession(),
        )
        runCurrent()

        viewModel.setPlaybackSpeed(1.5f)

        assertEquals(listOf(1.5f), controller.playbackSpeeds)
        viewModel.leave {}
        runCurrent()
    }
}

private fun request() = PlayerRequest(
    name = "movie.mp4",
    logicalUrl = "http://media.example:8080/middle/movie.mp4",
    requestUrl = "http://192.0.2.1:8080/middle/movie.mp4",
    mediaKey = "http://media.example:8080/middle/movie.mp4",
    kind = MediaKind.VIDEO,
)

private class FakePlaybackController : PlaybackController {
    private val mutable = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutable
    val preparedUrls = mutableListOf<String>()
    val seekCalls = mutableListOf<Long>()
    val playbackSpeeds = mutableListOf<Float>()
    val scaleModes = mutableListOf<VideoScaleMode>()
    var playCalls = 0
    var pauseCalls = 0
    var closeCalls = 0

    override fun prepare(url: String) {
        preparedUrls += url
    }

    override fun attachVideoOutput(host: ViewGroup) = Unit

    override fun detachVideoOutput() = Unit

    override fun setVideoScaleMode(mode: VideoScaleMode) {
        scaleModes += mode
    }

    override fun setPlaybackSpeed(speed: Float) {
        playbackSpeeds += speed
    }

    override fun play() {
        playCalls += 1
    }

    override fun pause() {
        pauseCalls += 1
    }

    override fun stop() = Unit

    override fun seekTo(positionMs: Long) {
        seekCalls += positionMs
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
