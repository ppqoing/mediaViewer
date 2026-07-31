package com.local.mediaviewer

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Parcelable
import androidx.core.os.BundleCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.local.mediaviewer.player.Media3PlaybackController
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.queue.PlaybackNoticeKind
import com.local.mediaviewer.service.ACTION_STOP_AND_RELEASE
import com.local.mediaviewer.testing.BackgroundPlaybackTestHarness
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
import org.junit.runner.RunWith

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
            first.run { seekTo(12_000L) }
            harness.waitUntil("position becomes persistable") {
                appController.sessionState.value.playback.positionMs >= 11_500L
            }
            val savedPosition =
                appController.sessionState.value.playback.positionMs
            val currentMediaKey =
                harness.mediaQueue().first().mediaId
            first.read {
                it.sendCustomCommand(
                    SessionCommand(ACTION_STOP_AND_RELEASE, android.os.Bundle.EMPTY),
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

            harness.connectControllerAfterRelease().use { restored ->
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
                assertEquals(1, harness.container.playbackEngineCloseCount)

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
