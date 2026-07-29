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
import com.local.mediaviewer.service.ACTION_STOP_AND_RELEASE
import com.local.mediaviewer.testing.BackgroundPlaybackTestHarness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
            val first = harness.connectController()
            first.run {
                setMediaItems(harness.mediaQueue())
                prepare()
                play()
            }
            harness.waitUntil("fixture playback starts") {
                first.read(Player::isPlaying)
            }
            first.run { seekTo(1_000L) }
            harness.waitUntil("position becomes persistable") {
                first.read(Player::getCurrentPosition) >= 750L
            }
            val currentMediaKey =
                harness.mediaQueue().first().mediaId
            val stopResult = first.read {
                it.sendCustomCommand(
                    SessionCommand(ACTION_STOP_AND_RELEASE, android.os.Bundle.EMPTY),
                    android.os.Bundle.EMPTY,
                )
            }
            stopResult.get()
            first.close()

            harness.waitUntil("service releases its only engine") {
                harness.container.playbackEngineCloseCount == 1
            }
            assertEquals(1, harness.container.playbackEngineCloseCount)
            assertTrue(
                harness.container.persistedPosition(currentMediaKey) >= 750L,
            )

            harness.connectController().use { restored ->
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
