package com.local.mediaviewer.ui.browser

import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class BrowserFilter {
    ALL,
    FOLDERS,
    VIDEO,
    AUDIO,
    IMAGE,
    GIF,
    ;

    fun accepts(entry: DirectoryEntry): Boolean = when (this) {
        ALL -> true
        FOLDERS -> entry.kind == MediaKind.DIRECTORY
        VIDEO -> entry.kind == MediaKind.VIDEO
        AUDIO -> entry.kind == MediaKind.AUDIO
        IMAGE -> entry.kind == MediaKind.IMAGE && !entry.isGif()
        GIF -> entry.kind == MediaKind.IMAGE && entry.isGif()
    }
}

fun DirectoryEntry.isGif(): Boolean =
    !isDirectory && name.substringAfterLast('.', "").equals("gif", ignoreCase = true)

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
