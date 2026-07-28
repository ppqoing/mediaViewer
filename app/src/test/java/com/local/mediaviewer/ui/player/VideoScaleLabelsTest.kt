package com.local.mediaviewer.ui.player

import com.local.mediaviewer.playback.VideoScaleMode
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoScaleLabelsTest {
    @Test
    fun `四种模式使用固定中文名称`() {
        assertEquals(
            listOf(
                "等比适应",
                "裁剪铺满",
                "强制拉伸",
                "原始尺寸",
            ),
            VideoScaleMode.entries.map(::videoScaleLabel),
        )
    }
}
