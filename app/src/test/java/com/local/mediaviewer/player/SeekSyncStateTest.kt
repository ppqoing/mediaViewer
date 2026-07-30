package com.local.mediaviewer.player

import com.local.mediaviewer.playback.PlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SeekSyncStateTest {
    @Test
    fun `new scrub clears the previous pending target`() {
        val next = SeekSyncState(
            pending = PendingSeek("movie", 34_000L),
        ).begin(actualMs = 12_000L)

        assertEquals(12_000L, next.previewMs)
        assertNull(next.pending)
    }

    @Test
    fun `commit keeps target visible until matching engine position arrives`() {
        val preview = SeekSyncState()
            .begin(actualMs = 10_000L)
            .preview(targetMs = 34_000L, durationMs = 60_000L)
        val (pending, command) = preview.commit(mediaKey = "movie")

        assertEquals(34_000L, command)
        assertEquals(34_000L, pending.displayedPosition(actualMs = 10_500L))
        assertNotNull(pending.pending)

        val stale = pending.reconcile(
            mediaKey = "movie",
            actualMs = 10_700L,
            status = PlaybackStatus.PAUSED,
        )
        assertEquals(34_000L, stale.displayedPosition(actualMs = 10_700L))

        val confirmed = stale.reconcile(
            mediaKey = "movie",
            actualMs = 33_400L,
            status = PlaybackStatus.PAUSED,
        )
        assertNull(confirmed.pending)
        assertEquals(33_400L, confirmed.displayedPosition(actualMs = 33_400L))
    }

    @Test
    fun `media switch error and end clear pending target`() {
        val pending = SeekSyncState(
            pending = PendingSeek("movie-a", 40_000L),
        )
        assertNull(
            pending.reconcile("movie-b", 0L, PlaybackStatus.OPENING).pending,
        )
        assertNull(
            pending.reconcile("movie-a", 10_000L, PlaybackStatus.ERROR).pending,
        )
        assertNull(
            pending.reconcile("movie-a", 60_000L, PlaybackStatus.ENDED).pending,
        )
    }
}
