package com.local.mediaviewer.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoBackgroundPlaybackPolicyTest {
    @Test
    fun `默认关闭时导航和后台清空但配置重建保留`() {
        assertTrue(
            VideoBackgroundPlaybackPolicy.shouldStopAndClear(
                enabled = false,
                reason = VideoSessionExitReason.NAVIGATE_AWAY,
            ),
        )
        assertTrue(
            VideoBackgroundPlaybackPolicy.shouldStopAndClear(
                enabled = false,
                reason = VideoSessionExitReason.APP_BACKGROUND,
            ),
        )
        assertFalse(
            VideoBackgroundPlaybackPolicy.shouldStopAndClear(
                enabled = false,
                reason = VideoSessionExitReason.CONFIGURATION_CHANGE,
            ),
        )
    }

    @Test
    fun `当前会话开启后台播放时所有退出原因均保留`() {
        VideoSessionExitReason.entries.forEach { reason ->
            assertFalse(
                VideoBackgroundPlaybackPolicy.shouldStopAndClear(
                    enabled = true,
                    reason = reason,
                ),
            )
        }
    }
}
