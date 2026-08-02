package com.local.mediaviewer.network

import com.local.mediaviewer.model.MediaKind
import java.util.Locale

object MediaClassifier {
    private val videos =
        setOf("mp4", "mkv", "avi", "mov", "webm", "m4v", "ts", "m2ts", "wmv", "flv")
    private val audio = setOf("mp3", "flac", "aac", "m4a", "ogg", "opus", "wav", "wma", "ape")
    private val images = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "heic", "heif")
    private val pdf = setOf("pdf")

    fun classify(name: String, isDirectory: Boolean): MediaKind {
        if (isDirectory) return MediaKind.DIRECTORY
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (extension) {
            in videos -> MediaKind.VIDEO
            in audio -> MediaKind.AUDIO
            in images -> MediaKind.IMAGE
            in pdf -> MediaKind.PDF
            else -> MediaKind.UNKNOWN
        }
    }
}
