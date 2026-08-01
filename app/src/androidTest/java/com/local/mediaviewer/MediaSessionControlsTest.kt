package com.local.mediaviewer

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Parcelable
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.core.os.BundleCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.local.mediaviewer.navigation.ACTION_OPEN_CURRENT_PLAYER
import com.local.mediaviewer.navigation.EXTRA_OPEN_CURRENT_PLAYER
import com.local.mediaviewer.player.Media3PlaybackController
import com.local.mediaviewer.player.VideoOutputConnectionState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.queue.PlaybackMode
import com.local.mediaviewer.queue.PlaybackNoticeKind
import com.local.mediaviewer.service.ACTION_STOP_AND_RELEASE
import com.local.mediaviewer.testing.BackgroundPlaybackTestHarness
import com.local.mediaviewer.ui.player.VlcSurface
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@UnstableApi
@RunWith(AndroidJUnit4::class)
class MediaSessionControlsTest {
    @Test
    fun systemCommandsStayInSyncWithAppControllerAndNotification() {
        BackgroundPlaybackTestHarness().use { harness ->
            val appController =
                harness.container.playbackController as Media3PlaybackController
            harness.connectController().use { systemController ->
                systemController.run {
                    setMediaItems(harness.mediaQueue())
                    prepare()
                    play()
                }
                harness.waitUntil("app controller sees playing first item") {
                    appController.sessionState.value.playback.status ==
                        PlaybackStatus.PLAYING &&
                        appController.sessionState.value.queue.currentIndex == 0
                }

                systemController.run { pause() }
                harness.waitUntil("pause reaches app controller") {
                    appController.sessionState.value.playback.status ==
                        PlaybackStatus.PAUSED
                }
                systemController.run { play() }
                harness.waitUntil("play reaches app controller") {
                    appController.sessionState.value.playback.status ==
                        PlaybackStatus.PLAYING
                }

                systemController.run { seekToNextMediaItem() }
                harness.waitUntil("next reaches app controller") {
                    appController.sessionState.value.queue.currentIndex == 1
                }
                systemController.run { seekToPreviousMediaItem() }
                harness.waitUntil("previous reaches app controller") {
                    appController.sessionState.value.queue.currentIndex == 0
                }
                systemController.run { seekTo(1_000L) }
                harness.waitUntil("seek reaches app controller") {
                    appController.state.value.positionMs >= 750L
                }

                val notification = harness.waitForMediaNotification()
                assertEquals(
                    harness.mediaQueue().first().mediaMetadata.title,
                    notification.extras.getCharSequence(Notification.EXTRA_TITLE),
                )
                assertNotNull(
                    BundleCompat.getParcelable(
                        notification.extras,
                        Notification.EXTRA_MEDIA_SESSION,
                        Parcelable::class.java,
                    ),
                )
            }
        }
    }

    @Test
    fun stopReleasesOnceAndColdControllerRestoresQueuePaused() {
        BackgroundPlaybackTestHarness().use { harness ->
            val appController =
                harness.container.playbackController as Media3PlaybackController
            val first = harness.connectController()
            first.run {
                setMediaItems(harness.mediaQueue())
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                play()
            }
            harness.waitUntil("fixture playback starts") {
                first.read(Player::isPlaying) &&
                    first.read(Player::getDuration) >= 18_000L &&
                    first.read {
                        it.isCommandAvailable(
                            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                        )
                    }
            }
            val currentMediaKey =
                harness.mediaQueue().first().mediaId
            // 无视频输出时模拟器上的 VLC 持续报 vout 失败并提前发出
            // EndReached，REPEAT_ONE 随之从头重播，position 无法维持到
            // 12 秒持久化门槛。挂上真实 Surface（与 BackgroundPlaybackTest
            // 同一模式）后引擎位置可稳定推进。
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity {
                    it.setContent {
                        VlcSurface(
                            controller = appController,
                            keepScreenOn = true,
                        )
                    }
                }
                harness.waitUntil("video output attaches") {
                    appController.videoOutputState.value ==
                        VideoOutputConnectionState.Attached
                }
                first.run { setPlaybackSpeed(1.25f) }
                harness.waitUntil("mode and speed reach the session queue") {
                    appController.sessionState.value.queue.mode ==
                        PlaybackMode.REPEAT_ONE &&
                        appController.sessionState.value.queue.playbackSpeed ==
                            1.25f
                }
                first.run { seekTo(12_000L) }
                harness.waitUntil("position becomes persistable") {
                    appController.sessionState.value.playback.positionMs >=
                        11_500L
                }
                val savedPosition =
                    appController.sessionState.value.playback.positionMs
                first.read {
                    it.sendCustomCommand(
                        SessionCommand(
                            ACTION_STOP_AND_RELEASE,
                            android.os.Bundle.EMPTY,
                        ),
                        android.os.Bundle.EMPTY,
                    )
                }
                harness.waitUntil("service releases its only engine") {
                    harness.container.playbackEngineCloseCount == 1
                }
                first.close()
                assertEquals(1, harness.container.playbackEngineCloseCount)
                val persistedPosition =
                    harness.container.persistedPosition(currentMediaKey)
                assertTrue(
                    "expected persisted position >= 10000, got " +
                        "$persistedPosition (captured=$savedPosition, " +
                        "recordCalls=${harness.snapshotSaveCalls}, " +
                        "last=${harness.lastSnapshotSave})",
                    persistedPosition >= 10_000L,
                )
            }

            // Media3 的 MediaController.release() 把 unbindService 延后到
            // 已释放会话应答或 30 秒 RELEASE_TIMEOUT_MS 超时；旧实例在此
            // 期间仍被绑定而拒绝冷连接（onGetSession 返回 null）。超时
            // 必须覆盖该窗口，5 秒默认值在 1.10.1 上必然超时。
            harness.connectControllerAfterRelease(timeoutMs = 45_000L)
                .use { restored ->
                harness.waitUntil("cold controller restores persistent queue") {
                    restored.read(Player::getMediaItemCount) == 2
                }
                assertFalse(restored.read(Player::getPlayWhenReady))
                assertFalse(restored.read(Player::isPlaying))
                assertEquals(
                    currentMediaKey,
                    restored.read {
                        it.currentMediaItem?.mediaId
                    },
                )
                assertEquals(
                    harness.mediaQueue()[0].mediaId,
                    restored.read { it.getMediaItemAt(0).mediaId },
                )
                assertEquals(
                    harness.mediaQueue()[1].mediaId,
                    restored.read { it.getMediaItemAt(1).mediaId },
                )
                assertEquals(
                    Player.REPEAT_MODE_ONE,
                    restored.read(Player::getRepeatMode),
                )
                assertEquals(
                    1.25f,
                    restored.read { it.playbackParameters.speed },
                    0.001f,
                )
                assertEquals(1, harness.container.playbackEngineCloseCount)
                val savedPosition = harness.container
                    .persistedPosition(currentMediaKey)

                restored.run { play() }
                harness.waitUntil(
                    diagnostic = "first user play applies policy-approved resume",
                    timeoutMs = 5_000L,
                ) {
                    restored.read(Player::getCurrentPosition) >=
                        savedPosition - 1_500L
                }
                val resumedPosition =
                    restored.read(Player::getCurrentPosition)
                assertTrue(
                    "expected resume near $savedPosition, got $resumedPosition",
                    resumedPosition in
                        (savedPosition - 1_500L)..(savedPosition + 6_000L),
                )
                restored.run { pause() }
                harness.waitUntil("user pause clears playback intent") {
                    !restored.read(Player::getPlayWhenReady) &&
                        !restored.read(Player::isPlaying)
                }
            }
        }
    }

    @Test
    fun persistenceNoticeReachesControllerOnceAndRetryKeepsPlaybackState() =
        runBlocking {
            BackgroundPlaybackTestHarness().use { harness ->
                val controller =
                    harness.container.playbackController as Media3PlaybackController
                harness.connectController().use { systemController ->
                    systemController.run {
                        setMediaItems(harness.mediaQueue())
                        prepare()
                        play()
                    }
                    harness.waitUntil("current item is visible to app controller") {
                        controller.sessionState.value.currentItem != null
                    }
                    val tickerBaseline = harness.snapshotSaveCalls
                    harness.waitUntil(
                        diagnostic = "an idle ticker snapshot completes",
                        timeoutMs = 7_000L,
                    ) {
                        harness.snapshotSaveCalls > tickerBaseline
                    }
                    val callsAfterTicker = harness.snapshotSaveCalls
                    val before = controller.sessionState.value
                    val notice = async(start = CoroutineStart.UNDISPATCHED) {
                        // Must finish well before the 5-second ticker can
                        // consume the armed failure.
                        withTimeout(2_000L) {
                            controller.notices.first()
                        }
                    }

                    harness.failNextSnapshotSave()
                    harness.retryPersistence()
                    val received = notice.await()

                    assertEquals(
                        callsAfterTicker + 1,
                        harness.snapshotSaveCalls,
                    )
                    assertEquals(
                        PlaybackNoticeKind.POSITION_SAVE_FAILED,
                        received.kind,
                    )
                    assertEquals(
                        before.currentItem?.mediaKey,
                        controller.sessionState.value.currentItem?.mediaKey,
                    )
                    assertEquals(
                        before.playWhenReady,
                        controller.sessionState.value.playWhenReady,
                    )

                    val callsBeforeRetry = harness.snapshotSaveCalls
                    harness.retryPersistence()
                    withTimeout(2_000L) {
                        while (
                            harness.snapshotSaveCalls <
                            callsBeforeRetry + 1
                        ) {
                            delay(25L)
                        }
                    }
                    assertEquals(
                        callsBeforeRetry + 1,
                        harness.snapshotSaveCalls,
                    )
                    assertNull(
                        withTimeoutOrNull(500L) {
                            controller.notices.first()
                        },
                    )
                }
            }
        }

    @Test
    fun notificationRequestOpensCurrentPlayerOnceAcrossRepeatedIntents() {
        BackgroundPlaybackTestHarness().use { harness ->
            val appController =
                harness.container.playbackController as Media3PlaybackController
            harness.connectController().use { systemController ->
                // 只 prepare 不 play：当前项对通知导航已足够，且避免
                // 播放中的控件自动隐藏与引擎事件干扰导航断言。
                systemController.run {
                    setMediaItems(harness.videoQueue())
                    prepare()
                }
                harness.waitUntil("app controller sees the current item") {
                    appController.sessionState.value.currentItem != null
                }
                val title = requireNotNull(
                    appController.sessionState.value.currentItem?.name,
                )
                val intent = Intent(harness.application, MainActivity::class.java)
                    .setAction(ACTION_OPEN_CURRENT_PLAYER)
                    .putExtra(EXTRA_OPEN_CURRENT_PLAYER, true)
                // Compose 断言只包裹 UI 段：类级 rule 会让本类的
                // Thread.sleep 真实等待饿死 Activity 重组帧（实测
                // attach 20s 超时、videoOutputState 停在 Detached）。
                val compose = createEmptyComposeRule()
                compose.apply(
                    object : Statement() {
                        override fun evaluate() {
                            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                                compose.waitUntil(10_000L) {
                                    compose.onAllNodesWithTag("vlc_surface")
                                        .fetchSemanticsNodes()
                                        .isNotEmpty()
                                }
                                compose.onNodeWithText(title).assertIsDisplayed()

                                // 同一通知再次投递（冷启动 intent 重发）后仍只有一个
                                // Player 返回栈 entry：一次系统返回必须回到 Home。
                                scenario.onActivity { activity ->
                                    InstrumentationRegistry.getInstrumentation()
                                        .callActivityOnNewIntent(activity, intent)
                                }
                                compose.onNodeWithText(title).assertIsDisplayed()
                                compose.onNodeWithTag("vlc_surface").assertExists()

                                scenario.onActivity { activity ->
                                    activity.onBackPressedDispatcher.onBackPressed()
                                }
                                compose.waitUntil(5_000L) {
                                    compose.onAllNodesWithText("MediaViewer")
                                        .fetchSemanticsNodes()
                                        .isNotEmpty()
                                }
                                compose.onNodeWithText("MediaViewer")
                                    .assertIsDisplayed()
                                compose.onNodeWithTag("vlc_surface")
                                    .assertDoesNotExist()
                            }
                        }
                    },
                    Description.createTestDescription(
                        "MediaSessionControlsTest",
                        "notificationRequestOpensCurrentPlayerOnce",
                    ),
                ).evaluate()
            }
        }
    }

    @Test
    fun stalePersistenceNoticeIsNotReplayedAfterColdReconnect() =
        runBlocking {
            BackgroundPlaybackTestHarness().use { harness ->
                val controller =
                    harness.container.playbackController as Media3PlaybackController
                harness.connectController().use { systemController ->
                    systemController.run {
                        setMediaItems(harness.mediaQueue())
                        prepare()
                        play()
                    }
                    harness.waitUntil("current item is visible to app controller") {
                        controller.sessionState.value.currentItem != null
                    }
                    val notice = async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeout(2_000L) {
                            controller.notices.first()
                        }
                    }
                    harness.failNextSnapshotSave()
                    harness.retryPersistence()
                    assertEquals(
                        PlaybackNoticeKind.POSITION_SAVE_FAILED,
                        notice.await().kind,
                    )

                    systemController.read {
                        it.sendCustomCommand(
                            SessionCommand(
                                ACTION_STOP_AND_RELEASE,
                                android.os.Bundle.EMPTY,
                            ),
                            android.os.Bundle.EMPTY,
                        )
                    }
                    harness.waitUntil("service releases its only engine") {
                        harness.container.playbackEngineCloseCount == 1
                    }

                    // 与 stopReleases 相同：超时需覆盖 Media3 的 30 秒
                    // 延迟解绑窗口。
                    harness.connectControllerAfterRelease(timeoutMs = 45_000L)
                        .use { restored ->
                        harness.waitUntil(
                            "cold controller sees the restored queue",
                        ) {
                            restored.read(Player::getMediaItemCount) == 2
                        }
                        assertNull(
                            withTimeoutOrNull(1_000L) {
                                controller.notices.first()
                            },
                        )
                    }
                }
            }
        }

    @Test
    fun pausedSeekStaysPausedAndExplicitPlayResumesAtTarget() {
        BackgroundPlaybackTestHarness().use { harness ->
            val appController =
                harness.container.playbackController as Media3PlaybackController
            harness.connectController().use { systemController ->
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    systemController.run {
                        setMediaItems(harness.videoQueue())
                        prepare()
                        play()
                    }
                    harness.waitUntil("fixture video starts") {
                        systemController.read(Player::isPlaying) &&
                            systemController.read(Player::getDuration) >= 18_000L
                    }
                    // 与 stopReleases 用例同理：真实 Surface 保证模拟器上
                    // VLC 引擎位置稳定可断言。
                    scenario.onActivity {
                        it.setContent {
                            VlcSurface(
                                controller = appController,
                                keepScreenOn = true,
                            )
                        }
                    }
                    harness.waitUntil("video output attaches") {
                        appController.videoOutputState.value ==
                            VideoOutputConnectionState.Attached
                    }

                    systemController.run { pause() }
                    harness.waitUntil("player is fully paused") {
                        !systemController.read(Player::isPlaying) &&
                            !systemController.read(Player::getPlayWhenReady)
                    }
                    systemController.run { seekTo(6_000L) }
                    harness.waitUntil("paused seek lands at the target") {
                        systemController.read(Player::getCurrentPosition) >=
                            5_500L
                    }
                    Thread.sleep(500L)
                    assertFalse(systemController.read(Player::isPlaying))
                    assertFalse(systemController.read(Player::getPlayWhenReady))
                    val settled =
                        systemController.read(Player::getCurrentPosition)
                    assertTrue(
                        "paused position should hold near 6000, got $settled",
                        settled in 5_000L..7_500L,
                    )

                    systemController.run { play() }
                    harness.waitUntil("explicit play resumes from the target") {
                        systemController.read(Player::isPlaying) &&
                            systemController.read(Player::getCurrentPosition) >=
                                settled
                    }
                    assertTrue(
                        "resume should start near $settled",
                        systemController.read(Player::getCurrentPosition) <
                            settled + 4_000L,
                    )
                }
            }
        }
    }

    private fun BackgroundPlaybackTestHarness.waitForMediaNotification():
        Notification {
        val manager = application.getSystemService(
            Context.NOTIFICATION_SERVICE,
        ) as NotificationManager
        var notification: Notification? = null
        waitUntil("active MediaStyle notification") {
            notification = manager.activeNotifications
                .map { it.notification }
                .firstOrNull {
                    it.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
                }
            notification != null
        }
        return requireNotNull(notification)
    }
}
