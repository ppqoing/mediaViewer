package com.local.mediaviewer.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.queue.PlaybackSessionState
import com.local.mediaviewer.queue.QueueMediaItem
import com.local.mediaviewer.ui.components.MediaGlyph
import com.local.mediaviewer.ui.components.PlayerIconButton
import com.local.mediaviewer.ui.icons.MediaIcons
import com.local.mediaviewer.ui.icons.MediaIcon
import com.local.mediaviewer.ui.theme.LocalPlayerColors
import com.local.mediaviewer.ui.theme.MediaTheme
import com.local.mediaviewer.ui.theme.surfacePlayerColors

@Composable
fun NowPlayingBar(
    state: PlaybackSessionState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onReplay: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = state.currentItem ?: return
    if (state.queue.items.isEmpty()) return
    val action = playbackPrimaryAction(state.playback.status)
    // 迷你播放器位于普通主题 surface 上（surface3），
    // 不能使用黑底 PlayerColors（规格 §6.1 浅色对比度）。
    // staticCompositionLocalOf 要求提供值稳定，remember 固定实例。
    val colorScheme = MaterialTheme.colorScheme
    val surfaceColors = remember(colorScheme) {
        surfacePlayerColors(colorScheme)
    }
    CompositionLocalProvider(
        LocalPlayerColors provides surfaceColors,
    ) {
        NowPlayingBarContent(
            item = item,
            action = action,
            playback = state.playback,
            canSkipNext = state.canSkipNext,
            onPrimaryAction = {
                action.command.invoke(onPlay, onPause, onReplay)
            },
            onNext = onNext,
            onOpenQueue = onOpenQueue,
            onOpenPlayer = onOpenPlayer,
            modifier = modifier,
        )
    }
}

@Deprecated("Flow Task 7 switches the root to the volume-free overload")
@Composable
fun NowPlayingBar(
    state: PlaybackSessionState,
    volumeState: VolumeState,
    onVolumeRefresh: () -> Unit,
    onToggleMute: () -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) = NowPlayingBar(
    state = state,
    onPlay = onToggle,
    onPause = onToggle,
    onReplay = onToggle,
    onNext = onNext,
    onOpenQueue = onOpenQueue,
    onOpenPlayer = onOpenPlayer,
    modifier = modifier,
)

@Composable
private fun NowPlayingBarContent(
    item: QueueMediaItem,
    action: PlaybackPrimaryAction,
    playback: PlaybackState,
    canSkipNext: Boolean,
    onPrimaryAction: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier,
) {
    val progressFraction = if (playback.durationMs > 0L) {
        (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(MediaTheme.sizing.miniPlayerHeight)
            .padding(horizontal = MediaTheme.spacing.sm, vertical = 4.dp)
            .testTag("now_playing_warm_paper"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f),
        ),
        shadowElevation = MediaTheme.elevation.surface4,
    ) {
        Column(Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MediaTheme.sizing.miniPlayerProgressHeight)
                    .testTag("mini_player_progress"),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                val compact = maxWidth < 360.dp || LocalDensity.current.fontScale >= 2f
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = MediaTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = MediaTheme.sizing.minimumTouchTarget)
                            .semantics(mergeDescendants = true) {
                                contentDescription = "打开播放器：${item.name}"
                            }
                            .clickable(onClick = onOpenPlayer),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MediaTheme.spacing.xs),
                    ) {
                        MediaGlyph(
                            icon = item.kind.glyph,
                            contentDescription = null,
                            tint = item.kind.glyphTint,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item.kind.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Box(contentAlignment = Alignment.Center) {
                        PlayerIconButton(
                            icon = action.icon,
                            contentDescription = action.contentDescription,
                            stateDescription = action.stateDescription,
                            enabled = action.enabled,
                            loading = action.loading,
                            onClick = onPrimaryAction,
                        )
                        if (playback.status == PlaybackStatus.BUFFERING) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp)
                                    .size(14.dp)
                                    .testTag("mini_buffering_ring"),
                            ) {
                                Box(Modifier.fillMaxSize().clearAndSetSemantics { }) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                    }
                    if (!compact) {
                        PlayerIconButton(
                            icon = PlayerIcons.Next,
                            contentDescription = "下一项",
                            enabled = canSkipNext,
                            onClick = onNext,
                        )
                    }
                    PlayerIconButton(
                        icon = PlayerIcons.Queue,
                        contentDescription = "打开播放队列",
                        modifier = Modifier.testTag("queue_entry_mini"),
                        onClick = onOpenQueue,
                    )
                }
            }
        }
    }
}

private val MediaKind.glyph: MediaIcon
    get() = when (this) {
        MediaKind.DIRECTORY -> MediaIcons.Folder
        MediaKind.VIDEO -> MediaIcons.Video
        MediaKind.AUDIO -> MediaIcons.Audio
        MediaKind.IMAGE -> MediaIcons.Image
        MediaKind.UNKNOWN -> MediaIcons.UnknownFile
    }

private val MediaKind.label: String
    get() = when (this) {
        MediaKind.DIRECTORY -> "文件夹"
        MediaKind.VIDEO -> "视频"
        MediaKind.AUDIO -> "音频"
        MediaKind.IMAGE -> "图片"
        MediaKind.UNKNOWN -> "文件"
    }

private val MediaKind.glyphTint: Color
    @Composable get() = when (this) {
        MediaKind.DIRECTORY -> MediaTheme.extendedColors.folder
        MediaKind.VIDEO -> MediaTheme.extendedColors.video
        MediaKind.AUDIO -> MediaTheme.extendedColors.audio
        MediaKind.IMAGE -> MediaTheme.extendedColors.image
        MediaKind.UNKNOWN -> MediaTheme.extendedColors.unknown
    }
