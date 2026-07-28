package com.local.mediaviewer.ui.player

import java.util.Locale

fun formatPlaybackTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(
            Locale.ROOT,
            "%d:%02d:%02d",
            hours,
            minutes,
            seconds,
        )
    } else {
        String.format(
            Locale.ROOT,
            "%02d:%02d",
            minutes,
            seconds,
        )
    }
}
