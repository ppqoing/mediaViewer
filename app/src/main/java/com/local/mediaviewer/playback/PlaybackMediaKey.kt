package com.local.mediaviewer.playback

import okhttp3.HttpUrl.Companion.toHttpUrl

object PlaybackMediaKey {
    fun fromLogicalUrl(logicalUrl: String): String =
        logicalUrl.toHttpUrl()
            .newBuilder()
            .fragment(null)
            .build()
            .toString()
}
