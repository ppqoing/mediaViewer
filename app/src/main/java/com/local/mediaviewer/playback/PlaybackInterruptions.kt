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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PlaybackInterruptions(
    context: Context,
    private val onPauseRequested: () -> Unit,
) : AutoCloseable, DefaultLifecycleObserver {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager =
        appContext.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(
        AudioManager.AUDIOFOCUS_GAIN,
    )
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build(),
        )
        .setOnAudioFocusChangeListener { change ->
            if (
                change == AudioManager.AUDIOFOCUS_LOSS ||
                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
            ) {
                onPauseRequested()
            }
        }
        .build()
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                onPauseRequested()
            }
        }
    }
    private var started = false

    @Synchronized
    fun start(): Boolean {
        if (!started) {
            runOnMainThread {
                ContextCompat.registerReceiver(
                    appContext,
                    noisyReceiver,
                    IntentFilter(
                        AudioManager.ACTION_AUDIO_BECOMING_NOISY,
                    ),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                try {
                    ProcessLifecycleOwner.get()
                        .lifecycle
                        .addObserver(this)
                } catch (error: Throwable) {
                    appContext.unregisterReceiver(noisyReceiver)
                    throw error
                }
            }
            started = true
        }
        return audioManager.requestAudioFocus(focusRequest) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun onStop(owner: LifecycleOwner) {
        onPauseRequested()
    }

    @Synchronized
    override fun close() {
        if (!started) return
        runOnMainThread {
            ProcessLifecycleOwner.get()
                .lifecycle
                .removeObserver(this)
            appContext.unregisterReceiver(noisyReceiver)
        }
        audioManager.abandonAudioFocusRequest(focusRequest)
        started = false
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
            "无法向主线程提交播放生命周期操作"
        }
        val finished = try {
            completed.await(
                MAIN_THREAD_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            )
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException(
                "等待播放生命周期主线程操作时被中断",
                error,
            )
        }
        check(finished) {
            "播放生命周期主线程操作超时"
        }
        failure?.let { throw it }
    }

    private companion object {
        const val MAIN_THREAD_TIMEOUT_SECONDS = 10L
    }
}
