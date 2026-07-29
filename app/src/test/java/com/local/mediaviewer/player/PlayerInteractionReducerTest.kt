package com.local.mediaviewer.player

import com.local.mediaviewer.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerInteractionReducerTest {
    @Test
    fun `快退快进截断到媒体边界`() {
        assertEquals(
            0L,
            PlayerInteractionReducer.seekTarget(3_000, 60_000, -10_000),
        )
        assertEquals(
            60_000L,
            PlayerInteractionReducer.seekTarget(58_000, 60_000, 10_000),
        )
    }

    @Test
    fun `拖动更新只返回预览且结束返回一次提交值`() {
        val initial = playerState(positionMs = 10_000, durationMs = 60_000)
        val preview = PlayerInteractionReducer.updateScrub(
            PlayerInteractionReducer.beginScrub(initial),
            34_000,
        )
        assertEquals(34_000L, preview.displayedPositionMs)

        val (finished, commit) = PlayerInteractionReducer.finishScrub(preview)
        assertEquals(34_000L, commit)
        assertNull(finished.previewPositionMs)
    }
}

private fun playerState(
    positionMs: Long,
    durationMs: Long,
) = PlayerUiState(
    name = "movie.mp4",
    kind = MediaKind.VIDEO,
    positionMs = positionMs,
    durationMs = durationMs,
)
