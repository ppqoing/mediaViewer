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
import com.local.mediaviewer.queue.PlaybackMode
import com.local.mediaviewer.queue.PlaybackQueue
import com.local.mediaviewer.queue.PlaybackSessionState
import com.local.mediaviewer.queue.QueueMediaItem
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
import org.junit.Assert.assertNull
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
    fun `paused video refreshes output immediately before user play`() =
        runTest(dispatcher) {
            val controller = FakePlaybackController()
            val viewModel = PlayerViewModel(
                initialRequest = request(),
                controller = controller,
                positionStore = FakeStore(),
                session = FakePlayerSession(),
                autoStart = false,
            )
            controller.emit(playback(PlaybackStatus.PAUSED, 10_000L))
            runCurrent()

            viewModel.play()

            assertEquals(listOf("refresh", "play"), controller.playCommands)
            assertEquals(1, controller.refreshVideoOutputCalls)
        }

    @Test
    fun `paused audio plays without refreshing video output`() =
        runTest(dispatcher) {
            val controller = FakePlaybackController()
            val viewModel = PlayerViewModel(
                initialRequest = request().copy(kind = MediaKind.AUDIO),
                controller = controller,
                positionStore = FakeStore(),
                session = FakePlayerSession(),
                autoStart = false,
            )
            controller.emit(playback(PlaybackStatus.PAUSED, 10_000L))
            runCurrent()

            viewModel.play()

            assertEquals(listOf("play"), controller.playCommands)
            assertEquals(0, controller.refreshVideoOutputCalls)
        }

    @Test
    fun `暂停和结束使用当前快照写入`() = runTest(dispatcher) {
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
        viewModel.pause()
        runCurrent()
        assertTrue(store.records.any { it.positionMs == 20_000L })

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
    fun `队列错误由后台协调器恢复而视图模型不重复刷新`() =
        runTest(dispatcher) {
            val first = queueItem("a", "A.mp4")
            val second = queueItem("b", "B.mp4")
            val controller = FakeQueuePlaybackController(
                items = listOf(first, second),
                currentMediaKey = second.mediaKey,
            )
            val before = controller.sessionState.value.queue
            val session = FakePlayerSession(
                refreshed = SessionEndpoint(
                    "http://media.example:8080",
                    "http://192.0.2.2:8080",
                    "192.0.2.2",
                ),
            )
            val viewModel = PlayerViewModel(
                initialRequest = request().copy(mediaKey = first.mediaKey),
                controller = controller,
                positionStore = FakeStore(),
                session = session,
                autoStart = false,
            )
            runCurrent()

            controller.emitPlayback(
                PlaybackState(
                    status = PlaybackStatus.ERROR,
                    positionMs = 22_000L,
                    durationMs = 90_000L,
                    errorMessage = "端点失效",
                ),
            )
            runCurrent()

            assertTrue(controller.preparedUrls.isEmpty())
            assertEquals(0, session.refreshCalls)
            assertEquals(0, controller.reloadCalls)
            assertTrue(controller.selectCalls.isEmpty())
            assertEquals(before, controller.sessionState.value.queue)
            assertEquals(second, controller.sessionState.value.currentItem)
            viewModel.leave {}
            runCurrent()
        }

    @Test
    fun `队列切到下一项后离开只保存当前项进度`() =
        runTest(dispatcher) {
            val first = queueItem("a", "A.mp4")
            val second = queueItem("b", "B.mp4")
            val controller = FakeQueuePlaybackController(
                items = listOf(first, second),
                currentMediaKey = first.mediaKey,
            )
            val store = FakeStore()
            val viewModel = PlayerViewModel(
                initialRequest = request().copy(mediaKey = first.mediaKey),
                controller = controller,
                positionStore = store,
                session = FakePlayerSession(),
                autoStart = false,
            )
            runCurrent()
            controller.selectCurrent(
                mediaKey = second.mediaKey,
                playback = PlaybackState(
                    status = PlaybackStatus.PLAYING,
                    positionMs = 33_000L,
                    durationMs = 100_000L,
                    isSeekable = true,
                ),
            )
            runCurrent()

            viewModel.leave {}
            runCurrent()

            assertEquals(listOf(second.mediaKey), store.records.map { it.mediaKey })
            assertEquals(33_000L, store.records.single().positionMs)
            assertEquals(0, controller.closeCalls)
        }

    @Test
    fun `队列结束切项中间态不由视图模型把旧结束位置保存到新当前项`() =
        runTest(dispatcher) {
            val first = queueItem("a", "A.mp4")
            val second = queueItem("b", "B.mp4")
            val controller = FakeQueuePlaybackController(
                items = listOf(first, second),
                currentMediaKey = first.mediaKey,
            )
            val store = FakeStore()
            PlayerViewModel(
                initialRequest = request().copy(mediaKey = first.mediaKey),
                controller = controller,
                positionStore = store,
                session = FakePlayerSession(),
                autoStart = false,
            )
            runCurrent()

            controller.emitEndedThenSelectCurrent(
                endedPlayback = PlaybackState(
                    status = PlaybackStatus.ENDED,
                    positionMs = 90_000L,
                    durationMs = 90_000L,
                ),
                selectedMediaKey = second.mediaKey,
            )
            runCurrent()

            assertTrue(store.records.isEmpty())
        }

    @Test
    fun `队列当前项从音频切到视频后页面元数据跟随会话`() =
        runTest(dispatcher) {
            val audio = queueItem("a", "A.mp3", MediaKind.AUDIO)
            val video = queueItem("b", "B.mp4", MediaKind.VIDEO)
            val controller = FakeQueuePlaybackController(
                items = listOf(audio, video),
                currentMediaKey = audio.mediaKey,
            )
            val viewModel = PlayerViewModel(
                initialRequest = request().copy(
                    name = audio.name,
                    mediaKey = audio.mediaKey,
                    kind = audio.kind,
                ),
                controller = controller,
                positionStore = FakeStore(),
                session = FakePlayerSession(),
                autoStart = false,
            )
            runCurrent()

            controller.selectCurrent(
                mediaKey = video.mediaKey,
                playback = PlaybackState(status = PlaybackStatus.PLAYING),
            )
            runCurrent()

            assertEquals(video.mediaKey, viewModel.uiState.value.currentMediaKey)
            assertEquals("B.mp4", viewModel.uiState.value.name)
            assertEquals(MediaKind.VIDEO, viewModel.uiState.value.kind)
        }

    @Test
    fun `删除路由对应旧项后页面继续显示新的当前项`() =
        runTest(dispatcher) {
            val first = queueItem("a", "A.mp3", MediaKind.AUDIO)
            val second = queueItem("b", "B.mp4", MediaKind.VIDEO)
            val controller = FakeQueuePlaybackController(
                items = listOf(first, second),
                currentMediaKey = first.mediaKey,
            )
            val viewModel = PlayerViewModel(
                initialRequest = request().copy(
                    name = first.name,
                    mediaKey = first.mediaKey,
                    kind = first.kind,
                ),
                controller = controller,
                positionStore = FakeStore(),
                session = FakePlayerSession(),
                autoStart = false,
            )
            runCurrent()

            controller.removeAndSelect(first.mediaKey, second.mediaKey)
            runCurrent()

            assertEquals(second.mediaKey, viewModel.uiState.value.currentMediaKey)
            assertEquals("B.mp4", viewModel.uiState.value.name)
            assertEquals(MediaKind.VIDEO, viewModel.uiState.value.kind)
        }

    @Test
    fun `clearing queue cancels deferred play for pending seek`() =
        runTest(dispatcher) {
            val item = queueItem("a", "A.mp3", MediaKind.AUDIO)
            val controller = FakeQueuePlaybackController(
                items = listOf(item),
                currentMediaKey = item.mediaKey,
            )
            val viewModel = PlayerViewModel(
                initialRequest = request().copy(
                    name = item.name,
                    mediaKey = item.mediaKey,
                    kind = item.kind,
                ),
                controller = controller,
                positionStore = FakeStore(),
                session = FakePlayerSession(),
                autoStart = false,
            )
            runCurrent()
            controller.emitPlayback(
                playback(PlaybackStatus.PAUSED, 10_000L),
            )
            runCurrent()

            viewModel.beginScrub()
            viewModel.previewScrub(34_000L)
            viewModel.commitScrub()
            viewModel.play()
            controller.clearQueue()
            runCurrent()

            assertNull(viewModel.uiState.value.seekSync.pending)

            advanceTimeBy(1_501L)
            runCurrent()

            assertEquals(0, controller.playCalls)
        }

    @Test
    fun `离开保存快照但不释放应用级控制器且重复离开只完成一次`() =
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

            var leaveCallbacks = 0
            viewModel.leave { leaveCallbacks += 1 }
            viewModel.leave { leaveCallbacks += 1 }
            runCurrent()

            assertEquals(0, controller.closeCalls)
            assertEquals(1, leaveCallbacks)
            assertEquals(40_000L, store.records.last().positionMs)
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
    fun `paused scrub defers play until engine confirms target`() =
        runTest(dispatcher) {
            val controller = FakePlaybackController()
            val viewModel = PlayerViewModel(
                initialRequest = request(),
                controller = controller,
                positionStore = FakeStore(),
                session = FakePlayerSession(),
                autoStart = false,
            )
            controller.emit(playback(PlaybackStatus.PAUSED, 10_000L))
            runCurrent()

            viewModel.beginScrub()
            viewModel.previewScrub(34_000L)
            viewModel.commitScrub()
            viewModel.play()

            assertEquals(listOf(34_000L), controller.seekCalls)
            assertEquals(0, controller.playCalls)
            assertEquals(0, controller.refreshVideoOutputCalls)
            assertEquals(
                34_000L,
                viewModel.uiState.value.displayedPositionMs,
            )

            controller.emit(playback(PlaybackStatus.PAUSED, 33_500L))
            runCurrent()

            assertEquals(1, controller.playCalls)
            assertEquals(listOf("refresh", "play"), controller.playCommands)
            assertNull(viewModel.uiState.value.seekSync.pending)
        }

    @Test
    fun `pending seek play falls back after timeout`() =
        runTest(dispatcher) {
            val controller = FakePlaybackController()
            val viewModel = PlayerViewModel(
                initialRequest = request(),
                controller = controller,
                positionStore = FakeStore(),
                session = FakePlayerSession(),
                autoStart = false,
            )
            controller.emit(playback(PlaybackStatus.PAUSED, 10_000L))
            runCurrent()
            viewModel.beginScrub()
            viewModel.previewScrub(34_000L)
            viewModel.commitScrub()
            viewModel.play()

            advanceTimeBy(1_501L)
            runCurrent()

            assertEquals(1, controller.playCalls)
            assertNull(viewModel.uiState.value.seekSync.pending)
        }

    @Test
    fun `paused seek without play falls back to actual position after timeout`() =
        runTest(dispatcher) {
            val controller = FakePlaybackController()
            val viewModel = PlayerViewModel(
                initialRequest = request(),
                controller = controller,
                positionStore = FakeStore(),
                session = FakePlayerSession(),
                autoStart = false,
            )
            controller.emit(playback(PlaybackStatus.PAUSED, 10_000L))
            runCurrent()

            viewModel.beginScrub()
            viewModel.previewScrub(34_000L)
            viewModel.commitScrub()

            advanceTimeBy(1_501L)
            runCurrent()

            assertNull(viewModel.uiState.value.seekSync.pending)
            assertEquals(10_000L, viewModel.uiState.value.displayedPositionMs)
            assertTrue(
                viewModel.uiState.value.errorMessage?.isNotBlank() == true,
            )
            assertEquals(0, controller.playCalls)
        }

    @Test
    fun `play reuses timeout started by seek commit`() =
        runTest(dispatcher) {
            val controller = FakePlaybackController()
            val viewModel = PlayerViewModel(
                initialRequest = request(),
                controller = controller,
                positionStore = FakeStore(),
                session = FakePlayerSession(),
                autoStart = false,
            )
            controller.emit(playback(PlaybackStatus.PAUSED, 10_000L))
            runCurrent()
            viewModel.beginScrub()
            viewModel.previewScrub(34_000L)
            viewModel.commitScrub()

            advanceTimeBy(1_000L)
            viewModel.play()
            advanceTimeBy(501L)
            runCurrent()

            assertNull(viewModel.uiState.value.seekSync.pending)
            assertEquals(1, controller.playCalls)
        }

    @Test
    fun `new scrub prevents stale seek timeout from clearing or playing it`() =
        runTest(dispatcher) {
            val controller = FakePlaybackController()
            val viewModel = PlayerViewModel(
                initialRequest = request(),
                controller = controller,
                positionStore = FakeStore(),
                session = FakePlayerSession(),
                autoStart = false,
            )
            controller.emit(playback(PlaybackStatus.PAUSED, 10_000L))
            runCurrent()
            viewModel.beginScrub()
            viewModel.previewScrub(34_000L)
            viewModel.commitScrub()
            viewModel.play()

            advanceTimeBy(1_000L)
            viewModel.beginScrub()
            viewModel.previewScrub(42_000L)
            viewModel.commitScrub()
            advanceTimeBy(501L)
            runCurrent()

            assertEquals(
                42_000L,
                viewModel.uiState.value.seekSync.pending?.targetMs,
            )
            assertEquals(42_000L, viewModel.uiState.value.displayedPositionMs)
            assertEquals(0, controller.playCalls)

            controller.emit(playback(PlaybackStatus.PAUSED, 41_500L))
            runCurrent()

            assertNull(viewModel.uiState.value.seekSync.pending)
            assertEquals(0, controller.playCalls)
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

private fun playback(
    status: PlaybackStatus,
    positionMs: Long,
) = PlaybackState(
    status = status,
    positionMs = positionMs,
    durationMs = 60_000L,
    isSeekable = true,
)

private fun queueItem(
    mediaKey: String,
    name: String,
    kind: MediaKind = MediaKind.VIDEO,
) = QueueMediaItem(
    mediaKey = mediaKey,
    name = name,
    logicalUrl = "http://media.example:8080/middle/$name",
    kind = kind,
)

private class FakePlaybackController : PlaybackController {
    private val mutable = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutable
    val preparedUrls = mutableListOf<String>()
    val seekCalls = mutableListOf<Long>()
    val playbackSpeeds = mutableListOf<Float>()
    val scaleModes = mutableListOf<VideoScaleMode>()
    val playCommands = mutableListOf<String>()
    var playCalls = 0
    var refreshVideoOutputCalls = 0
    var pauseCalls = 0
    var closeCalls = 0

    override fun prepare(url: String) {
        preparedUrls += url
    }

    override fun attachVideoOutput(host: ViewGroup) = Unit

    override fun detachVideoOutput() = Unit

    override fun refreshVideoOutput() {
        refreshVideoOutputCalls += 1
        playCommands += "refresh"
    }

    override fun setVideoScaleMode(mode: VideoScaleMode) {
        scaleModes += mode
    }

    override fun setPlaybackSpeed(speed: Float) {
        playbackSpeeds += speed
    }

    override fun play() {
        playCalls += 1
        playCommands += "play"
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
    val mediaKey: String,
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
        records += SavedRecord(mediaKey, positionMs, durationMs, ended)
    }

    override suspend fun clear(mediaKey: String) = Unit
}

private class FakeQueuePlaybackController(
    items: List<QueueMediaItem>,
    currentMediaKey: String,
) : QueuePlaybackController {
    private val playback = MutableStateFlow(PlaybackState())
    private val mutableSession = MutableStateFlow(
        PlaybackSessionState(
            queue = PlaybackQueue(
                items = items,
                currentMediaKey = currentMediaKey,
            ),
            currentItem = items.first { it.mediaKey == currentMediaKey },
        ),
    )
    override val state: StateFlow<PlaybackState> = playback
    override val sessionState: StateFlow<PlaybackSessionState> = mutableSession
    val preparedUrls = mutableListOf<String>()
    val selectCalls = mutableListOf<String>()
    var playCalls = 0
    var reloadCalls = 0
    var closeCalls = 0

    override fun prepare(url: String) {
        preparedUrls += url
        val replacement = queueItem(url, url)
        mutableSession.value = mutableSession.value.copy(
            queue = PlaybackQueue(
                items = listOf(replacement),
                currentMediaKey = replacement.mediaKey,
            ),
            currentItem = replacement,
        )
    }

    override fun play() {
        playCalls += 1
    }
    override fun pause() = Unit
    override fun stop() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun setPlaybackSpeed(speed: Float) = Unit
    override fun attachVideoOutput(host: ViewGroup) = Unit
    override fun detachVideoOutput() = Unit
    override fun setVideoScaleMode(mode: VideoScaleMode) = Unit
    override fun replaceQueue(items: List<QueueMediaItem>, startMediaKey: String) = Unit
    override fun playNext(item: QueueMediaItem) = Unit
    override fun append(item: QueueMediaItem) = Unit

    override fun select(mediaKey: String) {
        selectCalls += mediaKey
    }

    override fun reloadCurrent() {
        reloadCalls += 1
    }

    override fun skipPrevious() = Unit
    override fun skipNext() = Unit
    override fun move(mediaKey: String, toIndex: Int) = Unit
    override fun remove(mediaKey: String) = Unit
    override fun clearExceptCurrent() = Unit
    override fun clearAll() = Unit
    override fun setPlaybackMode(mode: PlaybackMode) = Unit

    override fun close() {
        closeCalls += 1
    }

    fun emitPlayback(value: PlaybackState) {
        playback.value = value
        mutableSession.value = mutableSession.value.copy(playback = value)
    }

    fun selectCurrent(
        mediaKey: String,
        playback: PlaybackState,
    ) {
        val queue = mutableSession.value.queue.copy(currentMediaKey = mediaKey)
        this.playback.value = playback
        mutableSession.value = mutableSession.value.copy(
            playback = playback,
            queue = queue,
            currentItem = queue.currentItem,
        )
    }

    fun removeAndSelect(
        removedMediaKey: String,
        selectedMediaKey: String,
    ) {
        val items = mutableSession.value.queue.items.filterNot {
            it.mediaKey == removedMediaKey
        }
        val queue = mutableSession.value.queue.copy(
            items = items,
            currentMediaKey = selectedMediaKey,
        )
        mutableSession.value = mutableSession.value.copy(
            queue = queue,
            currentItem = queue.currentItem,
        )
    }

    fun clearQueue() {
        mutableSession.value = mutableSession.value.copy(
            queue = PlaybackQueue(),
            currentItem = null,
        )
    }

    fun emitEndedThenSelectCurrent(
        endedPlayback: PlaybackState,
        selectedMediaKey: String,
    ) {
        playback.value = endedPlayback
        val queue = mutableSession.value.queue.copy(
            currentMediaKey = selectedMediaKey,
        )
        mutableSession.value = mutableSession.value.copy(
            queue = queue,
            currentItem = queue.currentItem,
        )
    }
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
