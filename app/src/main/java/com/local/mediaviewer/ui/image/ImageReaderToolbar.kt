package com.local.mediaviewer.ui.image

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.image.ImageReaderMode
import com.local.mediaviewer.image.ImageSortOrder
import com.local.mediaviewer.ui.components.MediaIconButton
import com.local.mediaviewer.ui.components.MediaOption
import com.local.mediaviewer.ui.components.MediaOptionMenu
import com.local.mediaviewer.ui.components.MediaSegmentedControl
import com.local.mediaviewer.ui.components.MediaTopAppBar
import com.local.mediaviewer.ui.components.PlayerIconButton
import com.local.mediaviewer.ui.components.SegmentItem
import com.local.mediaviewer.ui.icons.MediaIcons
import com.local.mediaviewer.ui.theme.MediaTheme
import kotlin.math.roundToInt

fun imageSortLabel(order: ImageSortOrder): String =
    when (order) {
        ImageSortOrder.NAME_ASC -> "文件名升序"
        ImageSortOrder.NAME_DESC -> "文件名降序"
        ImageSortOrder.MODIFIED_ASC -> "修改时间升序"
        ImageSortOrder.MODIFIED_DESC -> "修改时间降序"
        ImageSortOrder.SIZE_ASC -> "文件大小升序"
        ImageSortOrder.SIZE_DESC -> "文件大小降序"
    }

@Composable
fun ImageReaderToolbar(
    title: String,
    currentIndex: Int,
    totalCount: Int,
    mode: ImageReaderMode,
    sortOrder: ImageSortOrder,
    onModeChanged: (ImageReaderMode) -> Unit,
    onSortChanged: (ImageSortOrder) -> Unit,
    onBack: () -> Unit,
    safeDrawingInsets: WindowInsets =
        WindowInsets.safeDrawing,
) {
    var sortExpanded by remember {
        mutableStateOf(false)
    }
    val playerColors = MediaTheme.playerColors
    val sortOptions = remember(sortOrder) {
        ImageSortOrder.entries.map { order ->
            MediaOption(
                key = order,
                label = imageSortLabel(order),
                icon = MediaIcons.Connected.takeIf {
                    order == sortOrder
                },
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        playerColors.topScrimStart,
                        playerColors.topScrimEnd,
                    ),
                ),
            )
            .testTag("image_reader_scrim"),
    ) {
        MediaTopAppBar(
            title = "",
            containerColor = Color.Transparent,
            contentColor = playerColors.control,
            windowInsets = safeDrawingInsets.only(
                WindowInsetsSides.Top +
                    WindowInsetsSides.Horizontal,
            ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    safeDrawingInsets.only(
                        WindowInsetsSides.Top +
                            WindowInsetsSides.Horizontal,
                    ),
                )
                .heightIn(min = 64.dp)
                .padding(horizontal = MediaTheme.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaIconButton(
                icon = MediaIcons.Back,
                contentDescription = "返回",
                onClick = onBack,
                modifier = Modifier.testTag(
                    "image_reader_back",
                ),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = MediaTheme.spacing.xxs),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = playerColors.control,
                )
                Text(
                    text = "${currentIndex + 1} / $totalCount",
                    modifier = Modifier.testTag(
                        "image_reader_toolbar_progress",
                    ),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = playerColors.control.copy(
                        alpha = 0.78f,
                    ),
                )
            }
            MediaIconButton(
                icon = MediaIcons.Grid,
                contentDescription = "网格/阅读模式",
                stateDescription = when (mode) {
                    ImageReaderMode.COMIC -> "条漫"
                    ImageReaderMode.SINGLE -> "单图"
                },
                onClick = {
                    onModeChanged(
                        when (mode) {
                            ImageReaderMode.COMIC ->
                                ImageReaderMode.SINGLE

                            ImageReaderMode.SINGLE ->
                                ImageReaderMode.COMIC
                        },
                    )
                },
                modifier = Modifier.testTag(
                    "reader_mode_toggle",
                ),
            )
            Box {
                MediaIconButton(
                    icon = MediaIcons.More,
                    contentDescription =
                        "排序：${imageSortLabel(sortOrder)}",
                    onClick = {
                        sortExpanded = true
                    },
                    modifier = Modifier.testTag(
                        "image_sort_menu",
                    ),
                )
                MediaOptionMenu(
                    expanded = sortExpanded,
                    options = sortOptions,
                    selectedKey = sortOrder,
                    onSelect = { order ->
                        sortExpanded = false
                        onSortChanged(order)
                    },
                    onDismissRequest = {
                        sortExpanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun ImageReaderOverlayControls(
    currentIndex: Int,
    comicDisplayIndex: Int,
    totalCount: Int,
    currentItemName: String,
    mode: ImageReaderMode,
    scale: Float,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onFitScreen: () -> Unit,
    onComicProgressChanged: (Float) -> Unit,
    onComicProgressFinished: () -> Unit,
    onModeChanged: (ImageReaderMode) -> Unit,
    hasStaticImages: Boolean,
    hasAnimatedGifs: Boolean,
    onSingleContentTypeSelected: (Boolean) -> Unit,
    safeDrawingInsets: WindowInsets =
        WindowInsets.safeDrawing,
) {
    val playerColors = MediaTheme.playerColors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(safeDrawingInsets),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("image_reader_controls"),
        ) {
            if (
                mode == ImageReaderMode.SINGLE &&
                currentIndex > 0
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterStart),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = playerColors.topScrimStart,
                    contentColor = playerColors.control,
                ) {
                    PlayerIconButton(
                        icon = MediaIcons.Back,
                        contentDescription = "上一张",
                        onClick = onPrevious,
                    )
                }
            }
            if (
                mode == ImageReaderMode.SINGLE &&
                currentIndex < totalCount - 1
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterEnd),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = playerColors.topScrimStart,
                    contentColor = playerColors.control,
                ) {
                    PlayerIconButton(
                        icon = MediaIcons.Forward,
                        contentDescription = "下一张",
                        onClick = onNext,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = MediaTheme.spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(
                    MediaTheme.spacing.xs,
                ),
            ) {
                if (mode == ImageReaderMode.SINGLE) {
                    Surface(
                        modifier = Modifier.testTag(
                            "image_zoom_toolbar",
                        ),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = playerColors.topScrimStart,
                        contentColor = playerColors.control,
                    ) {
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically,
                        ) {
                            PlayerIconButton(
                                icon = MediaIcons.ZoomOut,
                                contentDescription = "缩小",
                                onClick = onZoomOut,
                                enabled = scale > 1.001f,
                            )
                            Text(
                                text =
                                    "${(scale * 100f).roundToInt()}%",
                                modifier = Modifier.padding(
                                    horizontal =
                                        MediaTheme.spacing.xs,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = playerColors.control,
                            )
                            PlayerIconButton(
                                icon = MediaIcons.ZoomIn,
                                contentDescription = "放大",
                                onClick = onZoomIn,
                                enabled = scale < 4.999f,
                            )
                            PlayerIconButton(
                                icon = MediaIcons.FitScreen,
                                contentDescription = "适合屏幕",
                                onClick = onFitScreen,
                                enabled = scale > 1.001f,
                            )
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal =
                                    MediaTheme.spacing.md,
                            ),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = playerColors.topScrimStart,
                        contentColor = playerColors.control,
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal =
                                    MediaTheme.spacing.md,
                                vertical =
                                    MediaTheme.spacing.xs,
                            ),
                        ) {
                            Text(
                                text =
                                    "${comicDisplayIndex + 1} / $totalCount",
                                modifier = Modifier.testTag(
                                    "comic_progress_label",
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = playerColors.control,
                            )
                            Slider(
                                value =
                                    comicDisplayIndex + 1f,
                                onValueChange =
                                    onComicProgressChanged,
                                onValueChangeFinished =
                                    onComicProgressFinished,
                                valueRange =
                                    1f..
                                        totalCount
                                            .coerceAtLeast(1)
                                            .toFloat(),
                                steps =
                                    (totalCount - 2)
                                        .coerceAtLeast(0),
                                enabled = totalCount > 1,
                                modifier = Modifier.testTag(
                                    "comic_progress_slider",
                                ),
                            )
                        }
                    }
                }
                val selectedMode = when {
                    mode == ImageReaderMode.COMIC -> "comic"
                    isAnimatedGifName(currentItemName) -> "gif"
                    else -> "image"
                }
                val segments = buildList {
                    if (hasStaticImages) {
                        add(imageModeSegment)
                    }
                    if (hasAnimatedGifs) {
                        add(gifModeSegment)
                    }
                    add(comicModeSegment)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState(),
                        )
                        .testTag("image_reader_modes"),
                    horizontalArrangement =
                        Arrangement.Center,
                ) {
                    MediaSegmentedControl(
                        items = segments,
                        selectedId = selectedMode,
                        onSelected = { selected ->
                            when (selected) {
                                "comic" -> onModeChanged(
                                    ImageReaderMode.COMIC,
                                )

                                "gif" ->
                                    onSingleContentTypeSelected(true)

                                "image" ->
                                    onSingleContentTypeSelected(false)
                            }
                        },
                    )
                }
            }
        }
    }
}

private val imageModeSegment = SegmentItem(
    id = "image",
    label = "图片",
    icon = MediaIcons.ImageMode,
)

private val gifModeSegment = SegmentItem(
    id = "gif",
    label = "动图",
    icon = MediaIcons.GifMode,
)

private val comicModeSegment = SegmentItem(
    id = "comic",
    label = "漫画",
    icon = MediaIcons.ComicMode,
)
