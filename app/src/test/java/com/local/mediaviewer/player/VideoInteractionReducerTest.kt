package com.local.mediaviewer.player

import com.local.mediaviewer.playback.PlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoInteractionReducerTest {
    @Test
    fun `暂停结束错误菜单拖动和反馈都阻止普通自动隐藏`() {
        val blocked = listOf(
            PlaybackStatus.PAUSED to VideoInteractionState(),
            PlaybackStatus.ENDED to VideoInteractionState(),
            PlaybackStatus.ERROR to VideoInteractionState(),
            PlaybackStatus.PLAYING to VideoInteractionState(menuExpanded = true),
            PlaybackStatus.PLAYING to VideoInteractionState(scrubbing = true),
            PlaybackStatus.PLAYING to VideoInteractionState(
                feedback = PlayerGestureFeedback.Volume(50, false),
            ),
        )

        blocked.forEach { (status, interaction) ->
            assertFalse(VideoInteractionReducer.canAutoHide(status, interaction))
        }
    }

    @Test
    fun `播放中且没有交互时允许自动隐藏`() {
        assertTrue(
            VideoInteractionReducer.canAutoHide(
                PlaybackStatus.PLAYING,
                VideoInteractionState(),
            ),
        )
    }

    @Test
    fun `锁定后点击不能切换控制层且解锁恢复可见`() {
        val locked = VideoInteractionReducer.lock(
            VideoInteractionState(
                controlsVisible = true,
                menuExpanded = true,
                feedback = PlayerGestureFeedback.Seek(10_000L, 2_000L),
            ),
        )

        assertFalse(locked.controlsVisible)
        assertFalse(locked.menuExpanded)
        assertEquals(null, locked.feedback)
        assertEquals(locked, VideoInteractionReducer.toggleControls(locked))

        val unlocked = VideoInteractionReducer.unlock(locked)
        assertFalse(unlocked.controlsLocked)
        assertTrue(unlocked.controlsVisible)
    }
}
