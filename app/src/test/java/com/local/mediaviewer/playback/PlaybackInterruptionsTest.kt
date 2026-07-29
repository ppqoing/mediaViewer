package com.local.mediaviewer.playback

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlaybackInterruptionsTest {
    @Test
    fun `获取和放弃焦点且下次获取会重新请求`() {
        val application =
            ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions(
            "${application.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )
        val context: Context = application
        val audioManager =
            context.getSystemService(AudioManager::class.java)
        val shadowAudioManager = shadowOf(audioManager)
        val interruptions = PlaybackInterruptions(
            context = context,
            onEvent = {},
        )

        assertTrue(interruptions.acquireFocus())
        val firstRequest = requireNotNull(
            shadowAudioManager
                .lastAudioFocusRequest
                .audioFocusRequest,
        )
        interruptions.abandonFocus()
        assertSame(
            firstRequest,
            shadowAudioManager
                .lastAbandonedAudioFocusRequest,
        )

        shadowAudioManager.setNextFocusRequestResponse(
            AudioManager.AUDIOFOCUS_REQUEST_FAILED,
        )
        assertFalse(interruptions.acquireFocus())
        interruptions.abandonFocus()

        shadowAudioManager.setNextFocusRequestResponse(
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
        )
        assertTrue(interruptions.acquireFocus())
        interruptions.close()
    }

    @Test
    fun `焦点暂失后恢复发出可恢复事件`() {
        val context = testContext()
        val events = mutableListOf<PlaybackInterruption>()
        val interruptions = PlaybackInterruptions(context, events::add)

        assertTrue(interruptions.acquireFocus())
        val focusListener = requireNotNull(
            shadowOf(context.getSystemService(AudioManager::class.java))
                .lastAudioFocusRequest
                .listener,
        )
        focusListener.onAudioFocusChange(
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
        )
        focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        assertEquals(
            listOf(
                PlaybackInterruption.TransientLoss,
                PlaybackInterruption.FocusGained,
            ),
            events,
        )
        interruptions.close()
    }

    @Test
    fun `焦点 duck 发出暂失事件`() {
        val context = testContext()
        val events = mutableListOf<PlaybackInterruption>()
        val interruptions = PlaybackInterruptions(context, events::add)

        assertTrue(interruptions.acquireFocus())
        val focusListener = requireNotNull(
            shadowOf(context.getSystemService(AudioManager::class.java))
                .lastAudioFocusRequest
                .listener,
        )
        focusListener.onAudioFocusChange(
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
        )

        assertEquals(listOf(PlaybackInterruption.TransientLoss), events)
        interruptions.close()
    }

    @Test
    fun `永久失焦和耳机断开不发出可恢复事件`() {
        val context = testContext()
        val events = mutableListOf<PlaybackInterruption>()
        val interruptions = PlaybackInterruptions(context, events::add)

        assertTrue(interruptions.acquireFocus())
        val focusListener = requireNotNull(
            shadowOf(context.getSystemService(AudioManager::class.java))
                .lastAudioFocusRequest
                .listener,
        )
        focusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        context.sendBroadcast(
            Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            listOf(
                PlaybackInterruption.PermanentLoss,
                PlaybackInterruption.BecomingNoisy,
            ),
            events,
        )
        assertFalse(events.contains(PlaybackInterruption.FocusGained))
        interruptions.close()
    }

    private fun testContext(): Context {
        val application =
            ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions(
            "${application.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )
        return application
    }
}
