package com.local.mediaviewer.ui.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
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
import com.local.mediaviewer.ui.components.MediaTopAppBar
import com.local.mediaviewer.ui.icons.MediaIcons
import com.local.mediaviewer.ui.theme.MediaTheme

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
            windowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top,
            ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top,
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
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = playerColors.control.copy(
                        alpha = 0.78f,
                    ),
                )
            }
            MediaIconButton(
                icon = MediaIcons.ReaderMode,
                contentDescription = "阅读模式",
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
                    icon = MediaIcons.Sort,
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
