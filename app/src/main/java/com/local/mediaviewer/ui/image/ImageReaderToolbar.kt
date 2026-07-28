package com.local.mediaviewer.ui.image

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.local.mediaviewer.image.ImageReaderMode
import com.local.mediaviewer.image.ImageSortOrder

fun imageSortLabel(order: ImageSortOrder): String =
    when (order) {
        ImageSortOrder.NAME_ASC -> "文件名升序"
        ImageSortOrder.NAME_DESC -> "文件名降序"
        ImageSortOrder.MODIFIED_ASC -> "修改时间升序"
        ImageSortOrder.MODIFIED_DESC -> "修改时间降序"
        ImageSortOrder.SIZE_ASC -> "文件大小升序"
        ImageSortOrder.SIZE_DESC -> "文件大小降序"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageReaderToolbar(
    title: String,
    mode: ImageReaderMode,
    sortOrder: ImageSortOrder,
    onModeChanged: (ImageReaderMode) -> Unit,
    onSortChanged: (ImageSortOrder) -> Unit,
    onBack: () -> Unit,
) {
    var sortExpanded by remember {
        mutableStateOf(false)
    }
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector =
                        Icons.AutoMirrored.Default.ArrowBack,
                    contentDescription = "返回",
                )
            }
        },
        actions = {
            IconButton(
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
                modifier =
                    Modifier.testTag(
                        "reader_mode_toggle",
                    ),
            ) {
                Icon(
                    imageVector = Icons.Default.ViewStream,
                    contentDescription =
                        if (
                            mode ==
                            ImageReaderMode.COMIC
                        ) {
                            "切换到单图"
                        } else {
                            "切换到条漫"
                        },
                )
            }
            Box {
                IconButton(
                    onClick = { sortExpanded = true },
                    modifier =
                        Modifier.testTag(
                            "image_sort_menu",
                        ),
                ) {
                    Icon(
                        imageVector =
                            Icons.AutoMirrored.Default.Sort,
                        contentDescription =
                            "排序：${imageSortLabel(sortOrder)}",
                    )
                }
                DropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = {
                        sortExpanded = false
                    },
                ) {
                    ImageSortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = {
                                Text(imageSortLabel(order))
                            },
                            onClick = {
                                sortExpanded = false
                                onSortChanged(order)
                            },
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Black,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White,
        ),
    )
}
