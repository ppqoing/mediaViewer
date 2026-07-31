package com.local.mediaviewer.ui.player

import com.local.mediaviewer.playback.PlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPrimaryActionTest {
    @Test
    fun `every real playback status has one approved primary action`() {
        val actions = PlaybackStatus.entries.associateWith(
            ::playbackPrimaryAction,
        )

        assertEquals(PlaybackPrimaryCommand.PLAY, actions.getValue(PlaybackStatus.IDLE).command)
        assertEquals("播放", actions.getValue(PlaybackStatus.IDLE).contentDescription)
        assertEquals(PlaybackPrimaryCommand.PLAY, actions.getValue(PlaybackStatus.PAUSED).command)
        assertEquals(PlaybackPrimaryCommand.PAUSE, actions.getValue(PlaybackStatus.PLAYING).command)
        assertEquals("暂停", actions.getValue(PlaybackStatus.PLAYING).contentDescription)
        assertEquals(PlaybackPrimaryCommand.PAUSE, actions.getValue(PlaybackStatus.BUFFERING).command)
        assertEquals("正在缓冲，可暂停", actions.getValue(PlaybackStatus.BUFFERING).stateDescription)
        assertEquals(PlaybackPrimaryCommand.REPLAY, actions.getValue(PlaybackStatus.ENDED).command)
        assertEquals("重新播放", actions.getValue(PlaybackStatus.ENDED).contentDescription)

        val opening = actions.getValue(PlaybackStatus.OPENING)
        assertEquals(PlaybackPrimaryCommand.NONE, opening.command)
        assertEquals("正在打开", opening.contentDescription)
        assertEquals("正在打开媒体", opening.stateDescription)
        assertFalse(opening.enabled)
        assertTrue(opening.loading)

        val error = actions.getValue(PlaybackStatus.ERROR)
        assertEquals(PlaybackPrimaryCommand.NONE, error.command)
        assertEquals("播放不可用", error.contentDescription)
        assertEquals("播放错误", error.stateDescription)
        assertFalse(error.enabled)
        assertFalse(error.loading)
    }
}
