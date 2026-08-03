package com.local.mediaviewer.playback

internal object VlcMediaOptions {
    fun forSource(source: PlaybackSource): List<String> =
        when (source.demuxStrategy) {
            PlaybackDemuxStrategy.DEFAULT -> emptyList()
            PlaybackDemuxStrategy.AVFORMAT -> listOf(":demux=avformat")
        }
}
