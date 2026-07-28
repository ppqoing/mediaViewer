package com.local.mediaviewer.playback

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class AndroidVlcPlaybackEngine(
    context: Context,
) : PlaybackEngine {
    private val closed = AtomicBoolean(false)
    private val libVlc = LibVLC(
        context.applicationContext,
        arrayListOf("--network-caching=1500"),
    )
    private val mediaPlayer = MediaPlayer(libVlc)
    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    private val interruptions = PlaybackInterruptions(context, ::pause)

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

    override fun attachVideoSurface(surfaceView: SurfaceView) {
        check(!closed.get()) {
            "PlaybackEngine is closed"
        }
        val videoOutput = mediaPlayer.vlcVout
        if (videoOutput.areViewsAttached()) {
            videoOutput.detachViews()
        }
        videoOutput.setVideoView(surfaceView)
        videoOutput.attachViews()
    }

    override fun detachVideoSurface() {
        if (closed.get()) return
        val videoOutput = mediaPlayer.vlcVout
        if (videoOutput.areViewsAttached()) {
            videoOutput.detachViews()
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
        if (mediaPlayer.vlcVout.areViewsAttached()) {
            mediaPlayer.vlcVout.detachViews()
        }
        mediaPlayer.stop()
        mediaPlayer.release()
        libVlc.release()
    }
}
