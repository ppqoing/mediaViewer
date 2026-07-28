package com.local.mediaviewer.playback

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.videolan.libvlc.MediaPlayer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class VideoScaleModeTest {
    @Test
    fun `四种项目模式精确映射 LibVLC`() {
        assertEquals(
            MediaPlayer.ScaleType.SURFACE_BEST_FIT,
            VideoScaleMode.BEST_FIT.toLibVlcScaleType(),
        )
        assertEquals(
            MediaPlayer.ScaleType.SURFACE_FIT_SCREEN,
            VideoScaleMode.FILL_CROP.toLibVlcScaleType(),
        )
        assertEquals(
            MediaPlayer.ScaleType.SURFACE_FILL,
            VideoScaleMode.STRETCH.toLibVlcScaleType(),
        )
        assertEquals(
            MediaPlayer.ScaleType.SURFACE_ORIGINAL,
            VideoScaleMode.ORIGINAL.toLibVlcScaleType(),
        )
    }
}
