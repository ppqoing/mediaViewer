package com.local.mediaviewer.playback

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
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
    fun `播放获取焦点暂停释放且下次播放重新请求`() {
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
            onPauseRequested = {},
        )

        assertTrue(interruptions.start())
        val firstRequest = requireNotNull(
            shadowAudioManager
                .lastAudioFocusRequest
                .audioFocusRequest,
        )
        interruptions.close()
        assertSame(
            firstRequest,
            shadowAudioManager
                .lastAbandonedAudioFocusRequest,
        )

        shadowAudioManager.setNextFocusRequestResponse(
            AudioManager.AUDIOFOCUS_REQUEST_FAILED,
        )
        assertFalse(interruptions.start())
        interruptions.close()

        shadowAudioManager.setNextFocusRequestResponse(
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
        )
        assertTrue(interruptions.start())
        interruptions.close()
    }

    @Test
    fun `播放结束和错误释放焦点`() {
        val application =
            ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions(
            "${application.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )
        val context: Context = application
        val audioManager =
            context.getSystemService(AudioManager::class.java)
        val shadowAudioManager = shadowOf(audioManager)

        listOf(
            EngineEvent.EndReached,
            EngineEvent.Error("无法播放"),
        ).forEach { event ->
            val interruptions = PlaybackInterruptions(
                context = context,
                onPauseRequested = {},
            )
            assertTrue(interruptions.start())
            val request = requireNotNull(
                shadowAudioManager
                    .lastAudioFocusRequest
                    .audioFocusRequest,
            )

            interruptions.onPlaybackEvent(event)

            assertSame(
                request,
                shadowAudioManager
                    .lastAbandonedAudioFocusRequest,
            )
            interruptions.close()
        }
    }

    @Test
    fun `耳机断开广播触发暂停且关闭后不再接收`() {
        val application =
            ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions(
            "${application.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )
        val context: Context = application
        var pauses = 0
        val interruptions = PlaybackInterruptions(
            context = context,
            onPauseRequested = { pauses += 1 },
        )

        assertTrue(interruptions.start())
        assertTrue(interruptions.start())
        context.sendBroadcast(
            Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, pauses)

        interruptions.close()
        interruptions.close()
        context.sendBroadcast(
            Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, pauses)
    }

    @Test
    fun `进程进入后台触发暂停`() {
        var pauses = 0
        val interruptions = PlaybackInterruptions(
            context = ApplicationProvider.getApplicationContext(),
            onPauseRequested = { pauses += 1 },
        )

        interruptions.onStop(TestLifecycleOwner())

        assertEquals(1, pauses)
    }
}

private class TestLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = registry
}
