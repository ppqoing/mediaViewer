package com.local.mediaviewer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.local.mediaviewer.app.AppContainer
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.queue.PlaybackNotice
import com.local.mediaviewer.queue.PlaybackNoticeAction
import com.local.mediaviewer.queue.PlaybackNoticeKind
import com.local.mediaviewer.queue.PlaybackQueue
import com.local.mediaviewer.queue.PlaybackSessionState
import com.local.mediaviewer.queue.QueueMediaItem
import com.local.mediaviewer.testing.FakeAppContainer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppActivityRecreationTest {
    private class FakeContainerRule : ExternalResource() {
        private val application =
            ApplicationProvider.getApplicationContext<MediaViewerApplication>()
        private lateinit var original: AppContainer
        lateinit var container: FakeAppContainer
            private set

        override fun before() {
            original = application.container
            container = FakeAppContainer(application)
            application.container = container
        }

        override fun after() {
            container.close()
            application.container = original
        }
    }

    private val containerRule = FakeContainerRule()
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule(containerRule)
        .around(compose)

    @Test
    fun activity_recreation_reuses_pdf_route_view_model() {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("MiddleDir")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("MiddleDir").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("示例目录")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("示例目录").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("manual.pdf")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("manual.pdf").performClick()
        compose.onNodeWithTag("pdf_reader_root").assertIsDisplayed()
        compose.onNodeWithText("manual.pdf").assertIsDisplayed()
        assertEquals(1, containerRule.container.pdfAcquireCalls)

        compose.activityRule.scenario.recreate()

        compose.onNodeWithTag("pdf_reader_root").assertIsDisplayed()
        compose.onNodeWithText("manual.pdf").assertIsDisplayed()
        assertEquals(1, containerRule.container.pdfAcquireCalls)
    }

    @Test
    fun activity_recreation_restores_player_route_and_service_owned_item() {
        val item = QueueMediaItem(
            mediaKey = "http://media.test/video-b",
            name = "video-b",
            logicalUrl = "http://media.test/video-b",
            kind = MediaKind.VIDEO,
        )
        containerRule.container.fakePlaybackController.emitSessionState(
            PlaybackSessionState(
                playback = PlaybackState(
                    status = PlaybackStatus.PAUSED,
                    positionMs = 12_345L,
                    durationMs = 60_000L,
                ),
                queue = PlaybackQueue(
                    items = listOf(item),
                    currentMediaKey = item.mediaKey,
                ),
                currentItem = item,
            ),
        )

        compose.onNodeWithContentDescription("打开播放器：video-b").performClick()
        compose.onNodeWithText("video-b").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("video-b").assertIsDisplayed()
        assertEquals(
            12_345L,
            containerRule.container.fakePlaybackController
                .sessionState.value.playback.positionMs,
        )
        assertEquals(
            1,
            containerRule.container.fakePlaybackController
                .sessionState.value.queue.items.size,
        )

        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("MediaViewer").assertIsDisplayed()
    }

    @Test
    fun defaultVideoSessionClearsQueueWhenActivityReallyStops() {
        val item = QueueMediaItem(
            mediaKey = "http://media.test/video-background-off",
            name = "video-background-off",
            logicalUrl = "http://media.test/video-background-off",
            kind = MediaKind.VIDEO,
        )
        containerRule.container.fakePlaybackController.emitSessionState(
            PlaybackSessionState(
                playback = PlaybackState(
                    status = PlaybackStatus.PAUSED,
                    durationMs = 60_000L,
                ),
                queue = PlaybackQueue(
                    items = listOf(item),
                    currentMediaKey = item.mediaKey,
                ),
                currentItem = item,
            ),
        )

        compose.onNodeWithContentDescription(
            "打开播放器：video-background-off",
        ).performClick()
        compose.onNodeWithText("video-background-off").assertIsDisplayed()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        assertEquals(
            0,
            containerRule.container.fakePlaybackController
                .sessionState.value.queue.items.size,
        )
    }

    @Test
    fun audioSessionKeepsQueueWhenActivityStops() {
        val item = QueueMediaItem(
            mediaKey = "http://media.test/audio-background",
            name = "audio-background",
            logicalUrl = "http://media.test/audio-background",
            kind = MediaKind.AUDIO,
        )
        containerRule.container.fakePlaybackController.emitSessionState(
            PlaybackSessionState(
                playback = PlaybackState(
                    status = PlaybackStatus.PAUSED,
                    durationMs = 60_000L,
                ),
                queue = PlaybackQueue(
                    items = listOf(item),
                    currentMediaKey = item.mediaKey,
                ),
                currentItem = item,
            ),
        )

        compose.onNodeWithContentDescription(
            "打开播放器：audio-background",
        ).performClick()
        compose.onNodeWithText("audio-background").assertIsDisplayed()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        assertEquals(
            MediaKind.AUDIO,
            containerRule.container.fakePlaybackController
                .sessionState.value.currentItem?.kind,
        )
    }

    @Test
    fun recreation_does_not_replay_an_old_persistence_notice() {
        val controller = containerRule.container.fakePlaybackController
        compose.onNodeWithText("MediaViewer").assertIsDisplayed()
        controller.emitNotice(
            PlaybackNotice(
                id = 100L,
                kind = PlaybackNoticeKind.QUEUE_SAVE_FAILED,
                message = "播放队列保存失败",
                action = PlaybackNoticeAction.RETRY_PERSISTENCE,
            ),
        )
        compose.onNodeWithText("播放队列保存失败").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("播放队列保存失败").assertDoesNotExist()
    }
}
