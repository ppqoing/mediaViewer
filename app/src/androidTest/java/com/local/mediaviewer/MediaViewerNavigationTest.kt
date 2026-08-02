package com.local.mediaviewer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.test.espresso.Espresso
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.local.mediaviewer.app.MediaViewerApp
import com.local.mediaviewer.image.ImageReaderMode
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.navigation.CurrentPlayerNavigationRequests
import com.local.mediaviewer.navigation.PLAYER_ENTRY_WAIT_TIMEOUT_MS
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.queue.PlaybackQueue
import com.local.mediaviewer.queue.PlaybackSessionState
import com.local.mediaviewer.queue.QueueMediaItem
import com.local.mediaviewer.session.ServerSessionState
import com.local.mediaviewer.testing.FakeAppContainer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaViewerNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var container: FakeAppContainer
    private lateinit var currentPlayerRequests: CurrentPlayerNavigationRequests

    @Before
    fun setUp() {
        container = FakeAppContainer(
            ApplicationProvider.getApplicationContext(),
            initialHasShownVideoGestures = true,
        )
        currentPlayerRequests = CurrentPlayerNavigationRequests()
        rule.setContent {
            MediaViewerApp(container, currentPlayerRequests)
        }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("MiddleDir")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @After
    fun tearDown() {
        container.close()
    }

    @Test
    fun homeOpensNestedVideo() {
        openNestedDirectory()
        rule.onNodeWithTag("bottom_nav_sources").assertDoesNotExist()
        rule.onNodeWithTag("bottom_nav_settings").assertDoesNotExist()
        rule.onNodeWithText("样例.mp4").performClick()
        rule.onNodeWithText("样例.mp4").assertIsDisplayed()
        rule.onNodeWithTag("vlc_surface").assertExists()
        rule.onNodeWithTag("bottom_nav_sources").assertDoesNotExist()
        rule.onNodeWithTag("bottom_nav_settings").assertDoesNotExist()
    }

    @Test
    fun homeOpensNestedAudio() {
        openNestedDirectory()
        rule.onNodeWithText("样例.wav").performClick()
        rule.onNodeWithText("样例.wav").assertIsDisplayed()
        rule.onNodeWithTag("playback_timeline").assertExists()
    }

    @Test
    fun homeOpensNestedImage() {
        openNestedDirectory()
        rule.onNodeWithText("样例.png").performClick()
        rule.onAllNodesWithText("样例.png")
            .onFirst()
            .assertIsDisplayed()
        rule.onNodeWithTag("comic_reader").assertExists()
        rule.onNodeWithTag("bottom_nav_sources").assertDoesNotExist()
        rule.onNodeWithTag("bottom_nav_settings").assertDoesNotExist()
    }

    @Test
    fun bottomNavigationSwitchesOnlyBetweenTopLevelDestinations() {
        rule.onNodeWithTag("bottom_nav_sources").assertIsSelected()
        rule.onNodeWithTag("bottom_nav_settings").performClick()
        rule.onNodeWithText("服务器设置").assertIsDisplayed()
        rule.onNodeWithTag("bottom_nav_settings").assertIsSelected()

        rule.onNodeWithTag("bottom_nav_sources").performClick()
        rule.onAllNodesWithText("媒体源").onFirst().assertIsDisplayed()
        rule.onNodeWithTag("bottom_nav_sources").assertIsSelected()
    }

    @Test
    fun homeUsesConfiguredSingleImageMode() {
        runBlocking {
            container.readerPreferencesRepository
                .setDefaultMode(ImageReaderMode.SINGLE)
        }
        openNestedDirectory()
        rule.onNodeWithText("样例.png").performClick()
        rule.onAllNodesWithText("样例.png")
            .onFirst()
            .assertIsDisplayed()
        rule.onNodeWithTag("media_image").assertExists()
    }

    @Test
    fun homeShowsMiniPlayerForCurrentQueueItem() {
        container.playbackController.replaceQueue(
            listOf(
                QueueMediaItem(
                    mediaKey = "queue-song",
                    name = "队列歌曲.mp3",
                    logicalUrl = "http://media.test/queue-song.mp3",
                    kind = MediaKind.AUDIO,
                ),
            ),
            "queue-song",
        )

        rule.onNodeWithText("队列歌曲.mp3").assertIsDisplayed()
        rule.onNodeWithTag("queue_entry_mini").assertIsDisplayed()
    }

    @Test
    fun app_scope_connects_once_and_navigation_does_not_connect_again() {
        rule.waitUntil(5_000) { container.sessionConnectCalls == 1 }
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithText("服务器设置").assertIsDisplayed()
        assertEquals(1, container.sessionConnectCalls)
    }

    @Test
    fun browser_remains_visible_during_global_reconnect() {
        openNestedDirectory()
        rule.onNodeWithText("样例.mp4").assertIsDisplayed()

        container.emitServerSession(ServerSessionState.Connecting)
        rule.waitForIdle()

        rule.onNodeWithText("样例.mp4").assertIsDisplayed()
        rule.onNodeWithText("正在重新连接").assertIsDisplayed()
    }

    @Test
    fun failed_player_has_reconnect_and_back_without_an_infinite_spinner() {
        val item = QueueMediaItem(
            mediaKey = "video-a",
            name = "video-a",
            logicalUrl = "http://media.test/video-a",
            kind = MediaKind.VIDEO,
        )
        container.fakePlaybackController.emitSessionState(
            PlaybackSessionState(
                queue = PlaybackQueue(listOf(item), currentMediaKey = item.mediaKey),
                currentItem = item,
            ),
        )
        currentPlayerRequests.requestOpenCurrentPlayer()
        rule.onNodeWithText("video-a").assertIsDisplayed()
        container.fakePlaybackController.emitSessionState(
            PlaybackSessionState(errorMessage = "服务连接失败"),
        )

        rule.onNodeWithText("服务连接失败").assertIsDisplayed()
        rule.onNodeWithText("重连播放器").performClick()
        assertEquals(1, container.fakePlaybackController.reconnectCalls)
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithText("MediaViewer").assertIsDisplayed()
    }

    @Test
    fun browser_player_back_returns_to_the_same_directory() {
        openNestedDirectory()
        rule.onNodeWithText("样例.mp4").performClick()
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithTag("breadcrumb_1").assertIsDisplayed()
        rule.onNode(
            hasText("样例.mp4") and
                hasAnyAncestor(hasTestTag("browser_list")),
        ).assertIsDisplayed()
    }

    @Test
    fun videoBackgroundDefaultsOffAndBackClearsQueue() {
        openNestedDirectory()
        rule.onNodeWithText("样例.mp4").performClick()
        rule.onNodeWithContentDescription("更多播放设置")
            .performClick()
        rule.onNodeWithTag("video_background_playback")
            .assert(
                androidx.compose.ui.test.SemanticsMatcher.expectValue(
                    SemanticsProperties.ToggleableState,
                    ToggleableState.Off,
                ),
            )

        Espresso.pressBack()
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithTag("browser_list").assertIsDisplayed()
        rule.runOnIdle {
            assertTrue(
                container.fakePlaybackController
                    .sessionState.value.queue.items.isEmpty(),
            )
        }
    }

    @Test
    fun videoBackgroundOptInPreservesQueueAndNewSessionResetsOff() {
        openNestedDirectory()
        rule.onNodeWithText("样例.mp4").performClick()
        rule.onNodeWithContentDescription("更多播放设置")
            .performClick()
        rule.onNodeWithTag("video_background_playback")
            .performClick()
            .assert(
                androidx.compose.ui.test.SemanticsMatcher.expectValue(
                    SemanticsProperties.ToggleableState,
                    ToggleableState.On,
                ),
            )

        Espresso.pressBack()
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithTag("browser_list").assertIsDisplayed()
        rule.runOnIdle {
            assertTrue(
                container.fakePlaybackController
                    .sessionState.value.queue.items.isNotEmpty(),
            )
        }

        currentPlayerRequests.requestOpenCurrentPlayer()
        rule.onNodeWithText("样例.mp4").assertIsDisplayed()
        rule.onNodeWithContentDescription("更多播放设置")
            .performClick()
        rule.onNodeWithTag("video_background_playback")
            .assert(
                androidx.compose.ui.test.SemanticsMatcher.expectValue(
                    SemanticsProperties.ToggleableState,
                    ToggleableState.Off,
                ),
            )
    }

    @Test
    fun audioBackKeepsQueueForExistingBackgroundBehavior() {
        openNestedDirectory()
        rule.onNodeWithText("样例.wav").performClick()
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithTag("browser_list").assertIsDisplayed()
        rule.runOnIdle {
            val current = container.fakePlaybackController
                .sessionState.value.currentItem
            assertEquals(MediaKind.AUDIO, current?.kind)
        }
    }

    @Test
    fun notification_request_from_browser_returns_home_and_empty_queue_exits_once() {
        val item = QueueMediaItem(
            mediaKey = "video-a",
            name = "video-a",
            logicalUrl = "http://media.test/video-a",
            kind = MediaKind.VIDEO,
        )
        container.fakePlaybackController.emitSessionState(
            PlaybackSessionState(
                queue = PlaybackQueue(listOf(item), currentMediaKey = item.mediaKey),
                currentItem = item,
            ),
        )
        openNestedDirectory()

        currentPlayerRequests.requestOpenCurrentPlayer()
        rule.onNodeWithText("video-a").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithText("MediaViewer").assertIsDisplayed()
        rule.onNodeWithTag("breadcrumb_1").assertDoesNotExist()

        // 视频会话默认不后台播放，返回已清空队列；
        // 重新注入当前项后再验证第二次通知导航。
        container.fakePlaybackController.emitSessionState(
            PlaybackSessionState(
                queue = PlaybackQueue(
                    listOf(item),
                    currentMediaKey = item.mediaKey,
                ),
                currentItem = item,
            ),
        )
        currentPlayerRequests.requestOpenCurrentPlayer()
        rule.onNodeWithText("video-a").assertIsDisplayed()
        container.fakePlaybackController.emitSessionState(PlaybackSessionState())
        rule.waitForIdle()
        // CQ-F1 修复后：已呈现播放器页遇到空队列先经过有限等待，
        // 等待到期后单次安全退出回首页，不再立即弹走。
        rule.mainClock.advanceTimeBy(PLAYER_ENTRY_WAIT_TIMEOUT_MS + 1_000L)
        rule.waitForIdle()
        rule.onNodeWithText("MediaViewer").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(1_000L)
        rule.onNodeWithText("MediaViewer").assertIsDisplayed()
    }

    @Test
    fun unverified_settings_edit_back_leaves_without_confirmation() {
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithText("服务器设置").assertIsDisplayed()
        rule.onNodeWithTag("server_url")
            .performClick()
            .performTextInput("/edited")
        rule.onNodeWithContentDescription("返回").performClick()
        // 规格 §8.3/§10：普通未验证输入不拦截返回、不出现放弃确认。
        rule.onNodeWithText("放弃未保存的服务器更改？").assertDoesNotExist()
        rule.onNodeWithText("MediaViewer").assertIsDisplayed()
    }

    @Test
    fun player_connecting_back_does_not_reopen_after_timeout() {
        val item = QueueMediaItem(
            mediaKey = "video-a",
            name = "video-a",
            logicalUrl = "http://media.test/video-a",
            kind = MediaKind.VIDEO,
        )
        // 生产路径：通知请求被消费后、Player 目的地首帧前控制器尚未
        // 回报当前项（异步服务恢复窗口）。测试以冻结时钟复现该窗口：
        // 第一帧消费请求并导航，清掉当前项后第二帧才组成 Player。
        rule.mainClock.autoAdvance = false
        currentPlayerRequests.requestOpenCurrentPlayer()
        container.fakePlaybackController.emitSessionState(
            PlaybackSessionState(
                queue = PlaybackQueue(listOf(item), currentMediaKey = item.mediaKey),
                currentItem = item,
            ),
        )
        rule.mainClock.advanceTimeByFrame()
        container.fakePlaybackController.emitSessionState(
            PlaybackSessionState(
                playback = PlaybackState(status = PlaybackStatus.OPENING),
            ),
        )
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.autoAdvance = true

        rule.onNodeWithText("正在连接播放器").assertIsDisplayed()
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithText("MediaViewer").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(PLAYER_ENTRY_WAIT_TIMEOUT_MS + 1_000L)
        rule.onNodeWithText("MediaViewer").assertIsDisplayed()
        rule.onNodeWithText("正在连接播放器").assertDoesNotExist()
    }

    @Test
    fun browser_deep_reconnect_retains_current_breadcrumbs() {
        openNestedDirectory()
        rule.onNodeWithText("样例.mp4").assertIsDisplayed()

        container.emitServerSession(ServerSessionState.Connecting)
        rule.waitForIdle()
        rule.onNodeWithText("样例.mp4").assertIsDisplayed()
        rule.onNodeWithText("正在重新连接").assertIsDisplayed()

        // 重连完成后仍锚定原目录：面包屑与内容不跳根。
        runBlocking { container.sessionManager.connectSaved() }
        rule.waitForIdle()
        rule.onNodeWithText("正在重新连接").assertDoesNotExist()
        rule.onNodeWithTag("breadcrumb_1").assertIsDisplayed()
        rule.onNode(
            hasText("样例.mp4") and
                hasAnyAncestor(hasTestTag("browser_list")),
        ).assertIsDisplayed()

        // Back 先消费 Browser 内层目录，而不是直接 pop 回 Home。
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithTag("browser_list").assertIsDisplayed()
        rule.onNodeWithText("示例目录").assertIsDisplayed()
        rule.onNodeWithTag("breadcrumb_1").assertDoesNotExist()

        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithText("MediaViewer").assertIsDisplayed()
        rule.onNodeWithTag("browser_list").assertDoesNotExist()
    }

    @Test
    fun nowPlayingDockLeavesTheBrowserTailReachable() {
        container.playbackController.replaceQueue(
            items = listOf(
                QueueMediaItem(
                    mediaKey = "playing",
                    name = "正在播放.mp3",
                    logicalUrl = "http://media.test/playing.mp3",
                    kind = MediaKind.AUDIO,
                ),
            ),
            startMediaKey = "playing",
        )
        openNestedDirectory()
        rule.onNodeWithTag("browser_list")
            .performScrollToNode(hasText("样例.wav"))

        val dockTop = rule.onNodeWithTag("now_playing_bar")
            .fetchSemanticsNode().boundsInRoot.top
        val tailBottom = rule.onNodeWithText("样例.wav")
            .fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(tailBottom <= dockTop)
    }

    private fun openNestedDirectory() {
        rule.onNodeWithText("MiddleDir").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("示例目录")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        rule.onNodeWithText("示例目录").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("样例.mp4")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        rule.onNodeWithText("MiddleDir").assertExists()
        rule.onNodeWithTag("breadcrumb_1").assertExists()
    }
}
