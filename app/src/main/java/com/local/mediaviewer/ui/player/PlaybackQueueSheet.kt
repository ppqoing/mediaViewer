package com.local.mediaviewer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.queue.PlaybackQueue
import com.local.mediaviewer.queue.QueueMediaItem

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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("播放队列")
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
                QueueItemRow(
                    item = item,
                    isCurrent = item.mediaKey == queue.currentMediaKey,
                    canMoveUp = index > 0,
                    canMoveDown = index < queue.items.lastIndex,
                    onSelect = { onSelect(item.mediaKey) },
                    onMoveUp = { onMove(item.mediaKey, index - 1) },
                    onMoveDown = { onMove(item.mediaKey, index + 1) },
                    onRemove = {
                        if (item.mediaKey == queue.currentMediaKey) removalCandidate = item
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
    isCurrent: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelect: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    ListItem(
        modifier = Modifier.heightIn(min = 56.dp).clickable(onClick = onSelect),
        headlineContent = { Text(item.name) },
        leadingContent = {
            if (isCurrent) Icon(Icons.Default.GraphicEq, contentDescription = "正在播放：${item.name}")
        },
        trailingContent = {
            Row {
                MoveHandle(
                    name = item.name,
                    onMoveUp = onMoveUp.takeIf { canMoveUp },
                    onMoveDown = onMoveDown.takeIf { canMoveDown },
                )
                if (canMoveUp) IconButton(onClick = onMoveUp) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移 ${item.name}")
                }
                if (canMoveDown) IconButton(onClick = onMoveDown) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移 ${item.name}")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "删除 ${item.name}")
                }
            }
        },
    )
}

@Composable
private fun MoveHandle(
    name: String,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    val dragThresholdPx = with(LocalDensity.current) { 24.dp.toPx() }
    IconButton(
        onClick = {},
        modifier = Modifier
            .semantics {
                contentDescription = "拖动排序 $name"
                customActions = buildList {
                    onMoveUp?.let { move ->
                        add(CustomAccessibilityAction("上移") { move(); true })
                    }
                    onMoveDown?.let { move ->
                        add(CustomAccessibilityAction("下移") { move(); true })
                    }
                }
            }
            .pointerInput(dragThresholdPx, onMoveUp, onMoveDown) {
                var accumulatedDrag = 0f
                var moveTriggered = false
                detectVerticalDragGestures(
                    onDragStart = {
                        accumulatedDrag = 0f
                        moveTriggered = false
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (moveTriggered) {
                            change.consume()
                        } else {
                            accumulatedDrag += dragAmount
                            val move = when {
                                accumulatedDrag <= -dragThresholdPx -> onMoveUp
                                accumulatedDrag >= dragThresholdPx -> onMoveDown
                                else -> null
                            }
                            if (move != null) {
                                move()
                                accumulatedDrag = 0f
                                moveTriggered = true
                                change.consume()
                            }
                        }
                    },
                )
            },
    ) {
        Icon(Icons.Default.DragHandle, contentDescription = null)
    }
}
