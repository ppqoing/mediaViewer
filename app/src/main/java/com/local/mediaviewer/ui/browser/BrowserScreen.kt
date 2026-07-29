package com.local.mediaviewer.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.browser.BrowserPage
import com.local.mediaviewer.browser.BrowserPlaybackAction
import com.local.mediaviewer.browser.BrowserUiState
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.ui.components.AppErrorPanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    state: BrowserUiState,
    onEntryClick: (DirectoryEntry) -> Unit,
    onBreadcrumbClick: (Int) -> Unit,
    onPlaybackAction: (BrowserPlaybackAction, DirectoryEntry) -> Unit = { _, _ -> },
    snackbarHostState: SnackbarHostState? = null,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        snackbarHost = {
            snackbarHostState?.let { SnackbarHost(it) }
        },
        topBar = {
            TopAppBar(
                title = { Text(currentTitle(state)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (state) {
                BrowserUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                is BrowserUiState.Error -> {
                    Box(modifier = Modifier.align(Alignment.Center)) {
                        AppErrorPanel(
                            message = state.error.userMessage,
                            onRetry = onRetry,
                        )
                    }
                }

                is BrowserUiState.Empty -> {
                    BrowserPageContent(
                        page = state.page,
                        onEntryClick = onEntryClick,
                        onBreadcrumbClick = onBreadcrumbClick,
                        onPlaybackAction = onPlaybackAction,
                    )
                    Text(
                        text = "此目录为空",
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                is BrowserUiState.Content -> {
                    BrowserPageContent(
                        page = state.page,
                        onEntryClick = onEntryClick,
                        onBreadcrumbClick = onBreadcrumbClick,
                        onPlaybackAction = onPlaybackAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserPageContent(
    page: BrowserPage,
    onEntryClick: (DirectoryEntry) -> Unit,
    onBreadcrumbClick: (Int) -> Unit,
    onPlaybackAction: (BrowserPlaybackAction, DirectoryEntry) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(
                horizontal = 12.dp,
                vertical = 6.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(page.breadcrumbs) { index, breadcrumb ->
                TextButton(
                    onClick = { onBreadcrumbClick(index) },
                    modifier = Modifier.testTag("breadcrumb_$index"),
                ) {
                    Text(breadcrumb.label)
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(
                items = page.entries,
                key = DirectoryEntry::logicalUrl,
            ) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEntryClick(entry) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = kindIcon(entry.kind),
                        contentDescription = contentDescription(entry.kind),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(entry.name)
                        Text(
                            text =
                                "${formatEntrySize(entry.size, entry.isDirectory)} · " +
                                    formatModifiedAt(entry.modifiedAt),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (entry.kind.isPlayable) {
                        PlaybackActionsMenu(
                            entry = entry,
                            onPlaybackAction = onPlaybackAction,
                        )
                    }
                }
            }
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
        IconButton(
            onClick = { expanded = true },
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "更多播放操作",
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            BrowserPlaybackAction.entries.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        expanded = false
                        onPlaybackAction(action, entry)
                    },
                )
            }
        }
    }
}

private fun currentTitle(state: BrowserUiState): String =
    when (state) {
        BrowserUiState.Loading -> "目录"
        is BrowserUiState.Error -> "目录"
        is BrowserUiState.Content -> state.page.breadcrumbs.last().label
        is BrowserUiState.Empty -> state.page.breadcrumbs.last().label
    }

private fun kindIcon(kind: MediaKind): ImageVector =
    when (kind) {
        MediaKind.DIRECTORY -> Icons.Default.Folder
        MediaKind.VIDEO -> Icons.Default.Movie
        MediaKind.AUDIO -> Icons.Default.AudioFile
        MediaKind.IMAGE -> Icons.Default.Image
        MediaKind.UNKNOWN -> Icons.AutoMirrored.Default.InsertDriveFile
    }

private fun contentDescription(kind: MediaKind): String =
    when (kind) {
        MediaKind.DIRECTORY -> "文件夹"
        MediaKind.VIDEO -> "视频"
        MediaKind.AUDIO -> "音频"
        MediaKind.IMAGE -> "图片"
        MediaKind.UNKNOWN -> "文件"
    }

private val MediaKind.isPlayable: Boolean
    get() = this == MediaKind.VIDEO ||
        this == MediaKind.AUDIO ||
        this == MediaKind.UNKNOWN

private val BrowserPlaybackAction.label: String
    get() = when (this) {
        BrowserPlaybackAction.PLAY_DIRECTORY -> "立即播放"
        BrowserPlaybackAction.PLAY_NEXT -> "下一项播放"
        BrowserPlaybackAction.ADD_TO_QUEUE -> "添加到队列"
    }
