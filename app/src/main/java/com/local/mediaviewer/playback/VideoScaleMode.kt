package com.local.mediaviewer.playback

import org.videolan.libvlc.MediaPlayer

enum class VideoScaleMode {
    BEST_FIT,
    FILL_CROP,
    STRETCH,
    ORIGINAL,
}

internal fun VideoScaleMode.toLibVlcScaleType():
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
