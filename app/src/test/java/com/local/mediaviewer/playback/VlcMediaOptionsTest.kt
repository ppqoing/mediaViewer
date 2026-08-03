package com.local.mediaviewer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VlcMediaOptionsTest {
    @Test
    fun `avformat strategy adds only avformat demux option`() {
        val options = VlcMediaOptions.forSource(
            PlaybackSource(
                url = "http://media.example/movie.mp4",
                demuxStrategy = PlaybackDemuxStrategy.AVFORMAT,
            ),
        )

        assertEquals(listOf(":demux=avformat"), options)
    }

    @Test
    fun `default strategy never forces avformat`() {
        val options = VlcMediaOptions.forSource(
            PlaybackSource("http://media.example/flat.mp4"),
        )

        assertEquals(emptyList<String>(), options)
        assertFalse(options.contains(":demux=avformat"))
    }
}
