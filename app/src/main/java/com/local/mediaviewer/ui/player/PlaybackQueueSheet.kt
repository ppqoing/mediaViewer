package com.local.mediaviewer.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.queue.PlaybackMode
import com.local.mediaviewer.queue.PlaybackQueue
import com.local.mediaviewer.queue.QueueMediaItem
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackQueueSheet(
    queue: PlaybackQueue,
    onSelect: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    onClearExceptCurrent: () -> Unit,
    onStopAndClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var removalCandidate by remember { mutableStateOf<QueueMediaItem?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val currentIndex = queue.currentIndex
    val nextMediaKey = queue.nextMediaKeyForLabel()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = "播放队列 · ${queue.items.size} 项",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = queue.mode.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row {
                TextButton(onClick = onClearExceptCurrent) { Text("清空其他") }
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多队列操作")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("停止并清空全部") },
                        onClick = {
                            menuExpanded = false
                            confirmClearAll = true
                        },
                    )
                }
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            itemsIndexed(queue.items, key = { _, item -> item.mediaKey }) { index, item ->
                val isCurrent = index == currentIndex
                QueueItemRow(
                    item = item,
                    index = index,
                    isCurrent = isCurrent,
                    isNext = !isCurrent && item.mediaKey == nextMediaKey,
                    canMoveUp = index > 0,
                    canMoveDown = index < queue.items.lastIndex,
                    onSelect = { onSelect(item.mediaKey) },
                    onMove = { destination -> onMove(item.mediaKey, destination) },
                    onRemove = {
                        if (isCurrent) removalCandidate = item
                        else onRemove(item.mediaKey)
                    },
                )
            }
        }
    }
    removalCandidate?.let { item ->
        AlertDialog(
            onDismissRequest = { removalCandidate = null },
            title = { Text("删除正在播放的项目？") },
            text = { Text("删除后将自动切换到下一项。") },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(item.mediaKey)
                    removalCandidate = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { removalCandidate = null }) { Text("取消") }
            },
        )
    }
    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("停止并清空全部？") },
            text = { Text("播放将停止，队列中的所有项目都会被移除。") },
            confirmButton = {
                TextButton(onClick = {
                    onStopAndClear()
                    confirmClearAll = false
                    onDismiss()
                }) { Text("停止并清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun QueueItemRow(
    item: QueueMediaItem,
    index: Int,
    isCurrent: Boolean,
    isNext: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val rowDescription = when {
        isCurrent -> "队列项 ${item.name}，正在播放"
        isNext -> "队列项 ${item.name}，即将播放"
        else -> "队列项 ${item.name}"
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .semantics {
                contentDescription = rowDescription
                stateDescription = "拖动排序 ${item.name}"
                customActions = buildList {
                    if (canMoveUp) {
                        add(CustomAccessibilityAction("上移") {
                            onMove(index - 1)
                            true
                        })
                    }
                    if (canMoveDown) {
                        add(CustomAccessibilityAction("下移") {
                            onMove(index + 1)
                            true
                        })
                    }
                    add(CustomAccessibilityAction("删除") {
                        onRemove()
                        true
                    })
                }
            }
            .queueDragModifier(
                item = item,
                index = index,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                onMove = onMove,
            )
            .clickable(onClick = onSelect)
            .heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (isCurrent) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
        },
        border = if (isCurrent) BorderStroke(1.dp, NeonPurple) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            if (isCurrent) {
                NeonPlayerIcon(
                    icon = PlayerIcons.Playing,
                    contentDescription = null,
                    active = true,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isNext) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "即将播放",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonPurple,
                        )
                    }
                }
                Text(
                    text = item.compactSubtitle(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRemove) {
                NeonPlayerIcon(
                    icon = PlayerIcons.Delete,
                    contentDescription = "删除 ${item.name}",
                )
            }
        }
    }
}

private fun Modifier.queueDragModifier(
    item: QueueMediaItem,
    index: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
): Modifier = composed {
    val dragThresholdPx = with(LocalDensity.current) { 24.dp.toPx() }
    pointerInput(item.mediaKey, index, dragThresholdPx) {
        var accumulatedDrag = 0f
        var moveTriggered = false
        detectDragGesturesAfterLongPress(
            onDragStart = {
                accumulatedDrag = 0f
                moveTriggered = false
            },
            onDrag = { change, dragAmount ->
                if (moveTriggered) {
                    change.consume()
                    return@detectDragGesturesAfterLongPress
                }
                accumulatedDrag += dragAmount.y
                val destination = when {
                    accumulatedDrag <= -dragThresholdPx && canMoveUp -> index - 1
                    accumulatedDrag >= dragThresholdPx && canMoveDown -> index + 1
                    else -> null
                }
                if (destination != null) {
                    onMove(destination)
                    moveTriggered = true
                    change.consume()
                }
            },
        )
    }
}

private fun PlaybackQueue.nextMediaKeyForLabel(): String? = when (mode) {
    PlaybackMode.SEQUENTIAL -> items.getOrNull(currentIndex + 1)?.mediaKey
    PlaybackMode.REPEAT_ALL ->
        items.getOrNull(currentIndex + 1)?.mediaKey
            ?: items.firstOrNull()?.mediaKey
    PlaybackMode.REPEAT_ONE -> items.getOrNull(currentIndex + 1)?.mediaKey
    PlaybackMode.SHUFFLE -> shuffleOrder.getOrNull(shuffleCursor + 1)
}

private fun QueueMediaItem.compactSubtitle(): String {
    val kindLabel = when (kind) {
        MediaKind.DIRECTORY -> "文件夹"
        MediaKind.VIDEO -> "视频"
        MediaKind.AUDIO -> "音频"
        MediaKind.IMAGE -> "图片"
        MediaKind.UNKNOWN -> "文件"
    }
    val finalPathSegment = runCatching {
        URI(logicalUrl).path
            ?.trimEnd('/')
            ?.substringAfterLast('/')
            ?.takeIf(String::isNotBlank)
    }.getOrNull()
    return if (finalPathSegment == null) kindLabel else "$kindLabel · $finalPathSegment"
}
