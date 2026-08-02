package com.local.mediaviewer.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.local.mediaviewer.browser.BrowserPlaybackAction
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.ui.components.MediaGlyph
import com.local.mediaviewer.ui.components.MediaIconButton
import com.local.mediaviewer.ui.components.MediaOption
import com.local.mediaviewer.ui.components.MediaOptionMenu
import com.local.mediaviewer.ui.icons.MediaIcons
import com.local.mediaviewer.ui.icons.MediaIcon
import com.local.mediaviewer.ui.theme.MediaTheme

@Composable
fun MediaFileRow(
    entry: DirectoryEntry,
    onEntryClick: (DirectoryEntry) -> Unit,
    onPlaybackAction: (BrowserPlaybackAction, DirectoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MediaTheme.sizing.listRowMinHeight)
            .padding(horizontal = MediaTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MediaTheme.spacing.sm),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable { onEntryClick(entry) }
                .padding(vertical = MediaTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MediaTheme.spacing.sm),
        ) {
            MediaGlyph(
                icon = entry.icon,
                contentDescription = entry.contentDescription,
                tint = entry.kind.tint,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatEntrySize(entry.size, entry.isDirectory)} · " +
                        formatModifiedAt(entry.modifiedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (entry.kind.isPlayable) {
            PlaybackActionsMenu(
                entry = entry,
                onPlaybackAction = onPlaybackAction,
            )
        }
    }
}

@Composable
private fun PlaybackActionsMenu(
    entry: DirectoryEntry,
    onPlaybackAction: (BrowserPlaybackAction, DirectoryEntry) -> Unit,
) {
    var expanded by remember(entry.logicalUrl) { mutableStateOf(false) }
    Box {
        MediaIconButton(
            icon = MediaIcons.More,
            contentDescription = "更多播放操作：${entry.name}",
            onClick = { expanded = true },
        )
        MediaOptionMenu(
            expanded = expanded,
            options = BrowserPlaybackAction.entries.map { action ->
                MediaOption(
                    key = action,
                    label = action.label,
                    icon = action.icon,
                )
            },
            selectedKey = null,
            onSelect = { action ->
                expanded = false
                onPlaybackAction(action, entry)
            },
            onDismissRequest = { expanded = false },
        )
    }
}

private val DirectoryEntry.icon: MediaIcon
    get() = if (isGif()) {
        MediaIcons.Gif
    } else {
        when (kind) {
            MediaKind.DIRECTORY -> MediaIcons.Folder
            MediaKind.VIDEO -> MediaIcons.Video
            MediaKind.AUDIO -> MediaIcons.Audio
            MediaKind.IMAGE -> MediaIcons.Image
            MediaKind.UNKNOWN -> MediaIcons.UnknownFile
        }
    }

private val DirectoryEntry.contentDescription: String
    get() = if (isGif()) {
        "动图"
    } else {
        when (kind) {
            MediaKind.DIRECTORY -> "文件夹"
            MediaKind.VIDEO -> "视频"
            MediaKind.AUDIO -> "音频"
            MediaKind.IMAGE -> "图片"
            MediaKind.UNKNOWN -> "文件"
        }
    }

private val MediaKind.tint: Color
    @Composable get() = when (this) {
        MediaKind.DIRECTORY -> MediaTheme.extendedColors.folder
        MediaKind.VIDEO -> MediaTheme.extendedColors.video
        MediaKind.AUDIO -> MediaTheme.extendedColors.audio
        MediaKind.IMAGE -> MediaTheme.extendedColors.image
        MediaKind.UNKNOWN -> MediaTheme.extendedColors.unknown
    }

private val MediaKind.isPlayable: Boolean
    get() = this == MediaKind.VIDEO || this == MediaKind.AUDIO || this == MediaKind.UNKNOWN

private val BrowserPlaybackAction.label: String
    get() = when (this) {
        BrowserPlaybackAction.PLAY_DIRECTORY -> "立即播放"
        BrowserPlaybackAction.PLAY_NEXT -> "下一项播放"
        BrowserPlaybackAction.ADD_TO_QUEUE -> "添加到队列"
    }

private val BrowserPlaybackAction.icon: MediaIcon
    get() = when (this) {
        BrowserPlaybackAction.PLAY_DIRECTORY -> MediaIcons.PlayNow
        BrowserPlaybackAction.PLAY_NEXT -> MediaIcons.PlayNext
        BrowserPlaybackAction.ADD_TO_QUEUE -> MediaIcons.AddQueue
    }
