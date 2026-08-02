package com.local.mediaviewer.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.queue.PlaybackMode
import com.local.mediaviewer.queue.PlaybackQueue
import com.local.mediaviewer.queue.QueueMediaItem
import com.local.mediaviewer.ui.components.MediaBottomSheet
import com.local.mediaviewer.ui.icons.MediaIconImage
import com.local.mediaviewer.ui.icons.MediaIcons
import com.local.mediaviewer.ui.theme.LocalPlayerColors
import com.local.mediaviewer.ui.theme.MediaTheme
import com.local.mediaviewer.ui.theme.surfacePlayerColors
import java.net.URI
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    onRemoveRequest: ((QueueMediaItem, Int) -> Unit)? = null,
    navigationBarsInsets: WindowInsets = WindowInsets.navigationBars,
) {
    var removalCandidate by remember { mutableStateOf<QueueMediaItem?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var moveAnnouncement by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val nextMediaKey = queue.nextMediaKeyForLabel()
    val hapticFeedback = LocalHapticFeedback.current
    val rowExtentPx = with(LocalDensity.current) { MediaTheme.sizing.listRowMinHeight.toPx() }
    val windowRootHeightPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    var dragSession by remember { mutableStateOf<QueueDragSession?>(null) }
    var previewItems by remember { mutableStateOf(queue.items) }
    var listBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var lastPointerRootY by remember { mutableFloatStateOf(Float.NaN) }
    val latestItems = rememberUpdatedState(queue.items)
    val latestOnMove = rememberUpdatedState(onMove)
    val gestureScope = rememberCoroutineScope()

    // 拖拽中外部队列发生变化：取消当前 session，preview 跟随最新队列，
    // 不把旧 index 应用到新队列
    LaunchedEffect(queue.items) {
        dragSession = null
        previewItems = queue.items
    }

    fun applyDragDelta(deltaPx: Float) {
        val session = dragSession ?: return
        val update = session.advance(
            deltaPx = deltaPx,
            rowExtentPx = rowExtentPx,
            lastIndex = previewItems.lastIndex,
        )
        dragSession = update.session
        if (update.crossedIndices.isNotEmpty()) {
            val reordered = previewItems.toMutableList()
            val movedItem = reordered.removeAt(session.currentIndex)
            reordered.add(update.session.currentIndex, movedItem)
            previewItems = reordered
        }
    }

    // 手势挂在 LazyColumn 上：行节点在滚动中会被 LazyList 复用/移出组合，
    // 行级 pointerInput 会因此被 reset；列表节点在组合中始终存活。
    // 拖动会话激活期间 userScrollEnabled=false，避免列表自身 scrollable 抢走事件流。
    val handleBandPx = with(LocalDensity.current) {
        // 行内布局：Surface 水平 12dp + Row 尾部 4dp + 删除 48dp，手柄为其前 48dp
        (12.dp + 4.dp + MediaTheme.sizing.minimumTouchTarget * 2).toPx()
    }
    val handleWidthPx = with(LocalDensity.current) {
        MediaTheme.sizing.minimumTouchTarget.toPx()
    }

    fun findHandleItem(offset: Offset): QueueMediaItem? {
        val layoutInfo = listState.layoutInfo
        val listWidth = listBoundsInRoot.width
        if (listWidth <= 0f) return null
        val handleLeft = listWidth - handleBandPx
        val handleRight = handleLeft + handleWidthPx
        val hit = layoutInfo.visibleItemsInfo.firstOrNull { info ->
            offset.y >= info.offset && offset.y <= info.offset + info.size
        } ?: return null
        if (offset.x < handleLeft || offset.x > handleRight) return null
        val key = hit.key as? String ?: return null
        return previewItems.firstOrNull { it.mediaKey == key }
    }

    val listDragModifier = Modifier.pointerInput(Unit) {
        var autoScrollJob: Job? = null
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                val hitItem = findHandleItem(offset)
                if (hitItem != null) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    dragSession = QueueDragSession(
                        mediaKey = hitItem.mediaKey,
                        startIndex = previewItems
                            .indexOfFirst { it.mediaKey == hitItem.mediaKey }
                            .coerceAtLeast(0),
                    )
                    autoScrollJob = gestureScope.launch {
                        listState.scroll(androidx.compose.foundation.MutatePriority.UserInput) {
                            while (isActive) {
                                val pointerY = lastPointerRootY
                                if (!pointerY.isNaN()) {
                                    val edgeZonePx = rowExtentPx * 2.5f
                                    val viewportTop = listBoundsInRoot.top
                                    val viewportBottom = windowRootHeightPx
                                    val delta = when {
                                        viewportBottom > 0f &&
                                            pointerY > viewportBottom - edgeZonePx ->
                                            (pointerY - (viewportBottom - edgeZonePx)) * 0.06f
                                                .coerceIn(0f, 14f)
                                        pointerY < viewportTop + edgeZonePx ->
                                            -(viewportTop + edgeZonePx - pointerY) * 0.06f
                                                .coerceIn(0f, 14f)
                                        else -> 0f
                                    }
                                    if (delta != 0f) {
                                        val consumed = scrollBy(delta)
                                        if (consumed != 0f) {
                                            applyDragDelta(consumed)
                                        }
                                    }
                                }
                                delay(16L)
                            }
                        }
                    }
                }
            },
            onDrag = { change, dragAmount ->
                if (dragSession != null) {
                    change.consume()
                    lastPointerRootY = listBoundsInRoot.top + change.position.y
                    applyDragDelta(dragAmount.y)
                }
            },
            onDragEnd = {
                autoScrollJob?.cancel()
                dragSession?.finish()?.let { drop ->
                    latestOnMove.value(drop.mediaKey, drop.toIndex)
                }
                dragSession = null
                lastPointerRootY = Float.NaN
                previewItems = latestItems.value
            },
            onDragCancel = {
                autoScrollJob?.cancel()
                dragSession = null
                lastPointerRootY = Float.NaN
                previewItems = latestItems.value
            },
        )
    }

    MediaBottomSheet(
        title = "播放队列 · ${queue.items.size} 项",
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("queue_sheet"),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        actions = {
            Text(
                text = queue.mode.label(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(end = 4.dp),
            )
            TextButton(
                onClick = onClearExceptCurrent,
                enabled = queue.items.size > 1,
            ) { Text("清空其他") }
            IconButton(onClick = { menuExpanded = true }) {
                MediaIconImage(
                    icon = MediaIcons.More,
                    contentDescription = "更多队列操作",
                    tint = androidx.compose.material3.LocalContentColor.current,
                )
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
            IconButton(onClick = onDismiss) {
                MediaIconImage(
                    icon = MediaIcons.Close,
                    contentDescription = "关闭播放队列",
                    tint = androidx.compose.material3.LocalContentColor.current,
                )
            }
        },
    ) {
        if (queue.items.isEmpty()) {
            Text(
                text = "播放队列为空",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            )
        } else {
            Text(
                text = moveAnnouncement,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .testTag("queue_move_announcement")
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .height(0.dp),
            )
            LazyColumn(
                state = listState,
                userScrollEnabled = dragSession == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .testTag("queue_list")
                    .then(listDragModifier)
                    .windowInsetsPadding(navigationBarsInsets)
                    .onGloballyPositioned { coordinates ->
                        listBoundsInRoot = coordinates.boundsInRoot()
                    },
            ) {
                itemsIndexed(
                    previewItems,
                    key = { _, item -> item.mediaKey },
                ) { index, item ->
                    val isCurrent = item.mediaKey == queue.currentMediaKey
                    val isDragging = dragSession?.mediaKey == item.mediaKey
                    QueueItemRow(
                        item = item,
                        index = index,
                        isCurrent = isCurrent,
                        isNext = !isCurrent && item.mediaKey == nextMediaKey,
                        canMoveUp = index > 0,
                        canMoveDown = index < previewItems.lastIndex,
                        isDragging = isDragging,
                        dragOffsetPx = if (isDragging) {
                            dragSession?.residualPx ?: 0f
                        } else {
                            0f
                        },
                        onSelect = { onSelect(item.mediaKey) },
                        onMove = { destination ->
                            onMove(item.mediaKey, destination)
                            moveAnnouncement = "已移动到第 ${destination + 1} 项"
                        },
                        onRemove = {
                            if (isCurrent) {
                                removalCandidate = item
                            } else if (onRemoveRequest != null) {
                                onRemoveRequest(
                                    item,
                                    queue.items.indexOfFirst { queued ->
                                        queued.mediaKey == item.mediaKey
                                    },
                                )
                            } else {
                                onRemove(item.mediaKey)
                            }
                        },
                    )
                }
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
    isDragging: Boolean,
    dragOffsetPx: Float,
    onSelect: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val rowDescription = when {
        isCurrent -> "队列项 ${item.name}，正在播放"
        isNext -> "队列项 ${item.name}，即将播放"
        else -> "队列项 ${item.name}"
    }
    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.02f else 1f,
        animationSpec = tween(MediaTheme.motion.stateMillis),
        label = "queueRowDragScale",
    )
    val dragElevation by animateDpAsState(
        targetValue = if (isDragging) {
            MediaTheme.elevation.surface4
        } else {
            MediaTheme.elevation.surface0
        },
        animationSpec = tween(MediaTheme.motion.stateMillis),
        label = "queueRowDragElevation",
    )
    // 队列浮层是普通主题 surface（surface4），行控件配色跟随
    // ColorScheme，不能使用黑底 PlayerColors（规格 §6.1/§8.8）。
    // staticCompositionLocalOf 要求提供值稳定，remember 固定实例。
    val colorScheme = MaterialTheme.colorScheme
    val surfaceColors = remember(colorScheme) {
        surfacePlayerColors(colorScheme)
    }
    CompositionLocalProvider(
        LocalPlayerColors provides surfaceColors,
    ) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MediaTheme.sizing.listRowMinHeight)
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                scaleX = dragScale
                scaleY = dragScale
                translationY = dragOffsetPx
            }
            .testTag("queue_row:${item.mediaKey}"),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 3.dp)
                .semantics {
                    contentDescription = rowDescription
                    if (isCurrent) {
                        selected = true
                    }
                    if (isNext) {
                        stateDescription = "即将播放"
                    }
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
                .clickable(onClick = onSelect),
            shape = MaterialTheme.shapes.medium,
            color = if (isCurrent) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
            },
            border = if (isCurrent) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            } else {
                null
            },
            shadowElevation = dragElevation,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                                color = MaterialTheme.colorScheme.tertiary,
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
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(MediaTheme.sizing.minimumTouchTarget)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "拖动排序 ${item.name}"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    NeonPlayerIcon(
                        icon = PlayerIcons.Drag,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
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
