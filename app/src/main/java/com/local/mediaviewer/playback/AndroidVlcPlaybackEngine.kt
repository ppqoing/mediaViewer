package com.local.mediaviewer.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class AndroidVlcPlaybackEngine(
    context: Context,
) : PlaybackEngine {
    private val closed = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val libVlc = LibVLC(
        context.applicationContext,
        arrayListOf("--network-caching=1500"),
    )
    private val mediaPlayer = MediaPlayer(libVlc)
    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    private val interruptions = PlaybackInterruptions(context, ::pause)
    private var videoHost: ViewGroup? = null
    private var videoLayout: VLCVideoLayout? = null
    private var videoScaleMode = VideoScaleMode.BEST_FIT

    init {
        mediaPlayer.setEventListener(::onVlcEvent)
    }

    override fun prepare(url: String) {
        check(!closed.get()) {
            "PlaybackEngine is closed"
        }
        mutableState.value = PlaybackState(
            status = PlaybackStatus.OPENING,
        )
        val media = Media(libVlc, Uri.parse(url))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":network-caching=1500")
        mediaPlayer.media = media
        media.release()
    }

    override fun attachVideoOutput(host: ViewGroup) {
        check(!closed.get()) {
            "PlaybackEngine is closed"
        }
        requireMainThread("绑定")
        detachVideoOutputInternal()

        val layout = VLCVideoLayout(host.context)
        host.removeAllViews()
        host.addView(
            layout,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        mediaPlayer.attachViews(
            layout,
            null,
            false,
            false,
        )
        videoHost = host
        videoLayout = layout
        mediaPlayer.setVideoScale(
            videoScaleMode.toLibVlcScaleType(),
        )
    }

    override fun detachVideoOutput() {
        if (closed.get()) return
        requireMainThread("解绑")
        detachVideoOutputInternal()
    }

    override fun setVideoScaleMode(mode: VideoScaleMode) {
        check(!closed.get()) {
            "PlaybackEngine is closed"
        }
        requireMainThread("设置画面模式")
        videoScaleMode = mode
        if (videoLayout != null) {
            mediaPlayer.setVideoScale(
                mode.toLibVlcScaleType(),
            )
        }
    }

    override fun play() {
        if (closed.get()) return
        if (interruptions.start()) {
            mediaPlayer.play()
        }
    }

    override fun pause() {
        if (!closed.get() && mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        }
    }

    override fun seekTo(positionMs: Long) {
        val playbackState = mutableState.value
        if (closed.get() || !playbackState.isSeekable) return

        mediaPlayer.time = if (playbackState.durationMs > 0L) {
            positionMs.coerceIn(0L, playbackState.durationMs)
        } else {
            positionMs.coerceAtLeast(0L)
        }
    }

    private fun onVlcEvent(event: MediaPlayer.Event) {
        if (closed.get()) return
        val mapped = when (event.type) {
            MediaPlayer.Event.Opening -> EngineEvent.Opening
            MediaPlayer.Event.Buffering ->
                EngineEvent.Buffering(event.buffering)
            MediaPlayer.Event.Playing -> EngineEvent.Playing
            MediaPlayer.Event.Paused -> EngineEvent.Paused
            MediaPlayer.Event.TimeChanged ->
                EngineEvent.TimeChanged(event.timeChanged)
            MediaPlayer.Event.LengthChanged ->
                EngineEvent.DurationChanged(event.lengthChanged)
            MediaPlayer.Event.SeekableChanged ->
                EngineEvent.SeekableChanged(event.seekable)
            MediaPlayer.Event.EndReached -> EngineEvent.EndReached
            MediaPlayer.Event.EncounteredError ->
                EngineEvent.Error("VLC 无法播放此媒体")
            MediaPlayer.Event.Vout -> {
                mediaPlayer.updateVideoSurfaces()
                return
            }

            else -> return
        }
        mutableState.value = EngineEventReducer.reduce(
            state = mutableState.value,
            event = mapped,
        )
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        interruptions.close()
        mediaPlayer.setEventListener(null)
        runOnMainThread {
            detachVideoOutputInternal()
        }
        mediaPlayer.stop()
        mediaPlayer.release()
        libVlc.release()
    }

    private fun detachVideoOutputInternal() {
        if (videoLayout == null) return
        mediaPlayer.detachViews()
        videoHost?.removeAllViews()
        videoLayout = null
        videoHost = null
    }

    private fun requireMainThread(operation: String) {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "视频输出必须在主线程$operation"
        }
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
            "无法向主线程提交视频输出操作"
        }
        val finished = try {
            completed.await(
                MAIN_THREAD_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            )
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException(
                "等待视频输出主线程操作时被中断",
                error,
            )
        }
        check(finished) {
            "视频输出主线程操作超时"
        }
        failure?.let { throw it }
    }

    private companion object {
        const val MAIN_THREAD_TIMEOUT_SECONDS = 10L
    }
}

private fun VideoScaleMode.toLibVlcScaleType():
    MediaPlayer.ScaleType =
    when (this) {
        VideoScaleMode.BEST_FIT ->
            MediaPlayer.ScaleType.SURFACE_BEST_FIT
        VideoScaleMode.FILL_CROP ->
            MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
        VideoScaleMode.STRETCH ->
            MediaPlayer.ScaleType.SURFACE_FILL
        VideoScaleMode.ORIGINAL ->
            MediaPlayer.ScaleType.SURFACE_ORIGINAL
    }
