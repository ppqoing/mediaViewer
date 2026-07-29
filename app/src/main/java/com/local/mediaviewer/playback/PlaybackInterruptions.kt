package com.local.mediaviewer.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

sealed interface PlaybackInterruption {
    data object TransientLoss : PlaybackInterruption
    data object PermanentLoss : PlaybackInterruption
    data object FocusGained : PlaybackInterruption
    data object BecomingNoisy : PlaybackInterruption
}

class PlaybackInterruptions(
    context: Context,
    private val onEvent: (PlaybackInterruption) -> Unit,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build(),
        )
        .setOnAudioFocusChangeListener(::onAudioFocusChange)
        .build()
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                onEvent(PlaybackInterruption.BecomingNoisy)
            }
        }
    }
    private var receiverRegistered = false
    private var focusHeld = false

    @Synchronized
    fun acquireFocus(): Boolean {
        if (focusHeld) return true
        registerNoisyReceiver()
        focusHeld = audioManager.requestAudioFocus(focusRequest) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!focusHeld) unregisterNoisyReceiver()
        return focusHeld
    }

    @Synchronized
    fun abandonFocus() {
        if (focusHeld) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            focusHeld = false
        }
        unregisterNoisyReceiver()
    }

    @Synchronized
    override fun close() {
        abandonFocus()
    }

    private fun onAudioFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                onEvent(PlaybackInterruption.TransientLoss)
            AudioManager.AUDIOFOCUS_LOSS ->
                onEvent(PlaybackInterruption.PermanentLoss)
            AudioManager.AUDIOFOCUS_GAIN ->
                onEvent(PlaybackInterruption.FocusGained)
        }
    }

    private fun registerNoisyReceiver() {
        if (receiverRegistered) return
        runOnMainThread {
            ContextCompat.registerReceiver(
                appContext,
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        receiverRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        if (!receiverRegistered) return
        runOnMainThread {
            appContext.unregisterReceiver(noisyReceiver)
        }
        receiverRegistered = false
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
            return
        }

        val completed = CountDownLatch(1)
        var failure: Throwable? = null
        check(
            mainHandler.post {
                try {
                    action()
                } catch (error: Throwable) {
                    failure = error
                } finally {
                    completed.countDown()
                }
            },
        ) {
            "无法向主线程提交音频焦点操作"
        }
        val finished = try {
            completed.await(MAIN_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("等待音频焦点主线程操作时被中断", error)
        }
        check(finished) { "音频焦点主线程操作超时" }
        failure?.let { throw it }
    }

    private companion object {
        const val MAIN_THREAD_TIMEOUT_SECONDS = 10L
    }
}
