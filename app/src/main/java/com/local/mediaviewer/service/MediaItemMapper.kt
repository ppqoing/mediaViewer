package com.local.mediaviewer.service

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.queue.QueueMediaItem

object MediaItemMapper {
    fun fromMedia3(item: MediaItem): QueueMediaItem {
        val logicalUrl = requireNotNull(item.localConfiguration?.uri) {
            "Media3 item must provide a logical URI"
        }.toString()
        val mediaKey = item.mediaId.ifBlank { logicalUrl }
        return QueueMediaItem(
            mediaKey = mediaKey,
            name = item.mediaMetadata.title?.toString()?.takeIf(String::isNotBlank) ?: mediaKey,
            logicalUrl = logicalUrl,
            kind = if (item.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_MUSIC) {
                MediaKind.AUDIO
            } else {
                MediaKind.VIDEO
            },
        )
    }
}

fun QueueMediaItem.toMedia3Item(): MediaItem = MediaItem.Builder()
    .setMediaId(mediaKey)
    .setUri(logicalUrl)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(name)
            .setMediaType(
                if (kind == MediaKind.AUDIO) {
                    MediaMetadata.MEDIA_TYPE_MUSIC
                } else {
                    MediaMetadata.MEDIA_TYPE_VIDEO
                },
            )
            .build(),
    )
    .build()
