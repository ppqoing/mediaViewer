package com.local.mediaviewer.ui.browser

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatEntrySize(size: Long, isDirectory: Boolean): String {
    if (isDirectory) return "—"
    if (size < 1024) return "$size B"

    val kibibytes = size / 1024.0
    if (kibibytes < 1024) {
        return String.format(Locale.ROOT, "%.1f KiB", kibibytes)
    }

    val mebibytes = kibibytes / 1024.0
    if (mebibytes < 1024) {
        return String.format(Locale.ROOT, "%.1f MiB", mebibytes)
    }

    return String.format(Locale.ROOT, "%.1f GiB", mebibytes / 1024.0)
}

fun formatModifiedAt(
    instant: Instant,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
    .withZone(zoneId)
    .format(instant)
