package com.local.mediaviewer.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoScaleModeTest {
    @Test
    fun `四种项目模式保持固定顺序`() {
        assertEquals(
            listOf(
                VideoScaleMode.BEST_FIT,
                VideoScaleMode.FILL_CROP,
                VideoScaleMode.STRETCH,
                VideoScaleMode.ORIGINAL,
            ),
            VideoScaleMode.entries,
        )
    }
}
