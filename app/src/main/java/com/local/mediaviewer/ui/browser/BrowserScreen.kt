package com.local.mediaviewer.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.browser.BrowserPage
import com.local.mediaviewer.browser.BrowserPlaybackAction
import com.local.mediaviewer.browser.BrowserUiState
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.ui.components.MediaAction
import com.local.mediaviewer.ui.components.MediaIconButton
import com.local.mediaviewer.ui.components.MediaScreenScaffold
import com.local.mediaviewer.ui.components.MediaStateKind
import com.local.mediaviewer.ui.components.MediaStatePanel
import com.local.mediaviewer.ui.components.WarmPaperCard
import com.local.mediaviewer.ui.icons.MediaIcon
import com.local.mediaviewer.ui.icons.MediaIconImage
import com.local.mediaviewer.ui.icons.MediaIcons
import com.local.mediaviewer.ui.theme.MediaPillShape
import com.local.mediaviewer.ui.theme.MediaTheme

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
    val visiblePage = visiblePage(state)
    val currentPath = visiblePage?.logicalDirectoryUrl
    var selectedFilter by rememberSaveable(currentPath) {
        mutableStateOf(BrowserFilter.ALL)
    }
    MediaScreenScaffold(
        title = currentTitle(state),
        onBack = onBack,
        actions = {
            // 规格 §8.2：顶栏提供返回和一个刷新入口。
            MediaIconButton(
                icon = MediaIcons.Refresh,
                contentDescription = "刷新",
                onClick = onRetry,
            )
        },
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (visiblePage == null) {
                when (state) {
                    is BrowserUiState.Loading -> MediaStatePanel(
                        kind = MediaStateKind.LOADING,
                        title = "正在加载目录",
                        modifier = Modifier.align(Alignment.Center),
                    )

                    is BrowserUiState.Error -> MediaStatePanel(
                        kind = MediaStateKind.ERROR,
                        title = "目录加载失败",
                        message = state.error.userMessage,
                        primaryAction = MediaAction("重试", onRetry),
                        modifier = Modifier.align(Alignment.Center),
                    )

                    is BrowserUiState.Content,
                    is BrowserUiState.Empty,
                    -> Unit
                }
            } else {
                BrowserPageContent(
                    page = visiblePage,
                    onEntryClick = onEntryClick,
                    onBreadcrumbClick = onBreadcrumbClick,
                    onPlaybackAction = onPlaybackAction,
                    selectedFilter = selectedFilter,
                    onFilterSelected = { selectedFilter = it },
                    isEmpty = state is BrowserUiState.Empty,
                    statusContent = when (state) {
                        is BrowserUiState.Loading -> {
                            { BrowserLoadingStatus() }
                        }
                        is BrowserUiState.Error -> {
                            {
                                BrowserErrorStatus(
                                    message = state.error.userMessage,
                                    onRetry = onRetry,
                                )
                            }
                        }

                        is BrowserUiState.Content,
                        is BrowserUiState.Empty,
                        -> null
                    },
                )
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
    selectedFilter: BrowserFilter,
    onFilterSelected: (BrowserFilter) -> Unit,
    isEmpty: Boolean,
    statusContent: (@Composable () -> Unit)? = null,
) {
    val visibleEntries = remember(page.entries, selectedFilter) {
        page.entries.filter(selectedFilter::accepts)
    }
    Column(Modifier.fillMaxSize()) {
        MediaBreadcrumbs(
            breadcrumbs = page.breadcrumbs,
            onBreadcrumbClick = onBreadcrumbClick,
            modifier = Modifier.padding(
                horizontal = MediaTheme.spacing.md,
                vertical = MediaTheme.spacing.xs,
            ),
        )
        BrowserFilterChips(
            selectedFilter = selectedFilter,
            onFilterSelected = onFilterSelected,
        )
        statusContent?.invoke()
        if (isEmpty) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("browser_list"),
            ) {
                MediaStatePanel(
                    kind = MediaStateKind.EMPTY,
                    title = "空文件夹",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("browser_empty_state"),
                )
            }
        } else {
            WarmPaperCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        start = MediaTheme.spacing.md,
                        end = MediaTheme.spacing.md,
                        top = MediaTheme.spacing.xs,
                        bottom = MediaTheme.spacing.md,
                    )
                    .testTag("browser_list"),
            ) {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(
                        items = visibleEntries,
                        key = { _, entry -> entry.logicalUrl },
                    ) { index, entry ->
                        MediaFileRow(
                            entry = entry,
                            onEntryClick = onEntryClick,
                            onPlaybackAction = onPlaybackAction,
                        )
                        if (index < visibleEntries.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(
                                    horizontal = MediaTheme.spacing.md,
                                ),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(
                                    alpha = 0.72f,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserFilterChips(
    selectedFilter: BrowserFilter,
    onFilterSelected: (BrowserFilter) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("browser_filter_list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = MediaTheme.spacing.md,
        ),
        horizontalArrangement = Arrangement.spacedBy(MediaTheme.spacing.xs),
    ) {
        items(
            items = BrowserFilter.entries,
            key = { it.name },
        ) { filter ->
            val selected = filter == selectedFilter
            FilterChip(
                selected = selected,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                leadingIcon = filter.icon?.let { icon ->
                    {
                        MediaIconImage(
                            icon = icon,
                            contentDescription = null,
                            tint = LocalContentColor.current,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                modifier = Modifier
                    .heightIn(min = MediaTheme.sizing.minimumTouchTarget)
                    .testTag(filter.testTag),
                shape = MediaPillShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

private val BrowserFilter.label: String
    get() = when (this) {
        BrowserFilter.ALL -> "全部"
        BrowserFilter.FOLDERS -> "文件夹"
        BrowserFilter.VIDEO -> "视频"
        BrowserFilter.AUDIO -> "音频"
        BrowserFilter.IMAGE -> "图片"
        BrowserFilter.GIF -> "动图"
        BrowserFilter.PDF -> "PDF"
    }

private val BrowserFilter.icon: MediaIcon?
    get() = when (this) {
        BrowserFilter.ALL -> null
        BrowserFilter.FOLDERS -> MediaIcons.Folder
        BrowserFilter.VIDEO -> MediaIcons.Video
        BrowserFilter.AUDIO -> MediaIcons.Audio
        BrowserFilter.IMAGE -> MediaIcons.Image
        BrowserFilter.GIF -> MediaIcons.Gif
        BrowserFilter.PDF -> MediaIcons.Pdf
    }

private val BrowserFilter.testTag: String
    get() = when (this) {
        BrowserFilter.ALL -> "browser_filter_all"
        BrowserFilter.FOLDERS -> "browser_filter_folders"
        BrowserFilter.VIDEO -> "browser_filter_video"
        BrowserFilter.AUDIO -> "browser_filter_audio"
        BrowserFilter.IMAGE -> "browser_filter_image"
        BrowserFilter.GIF -> "browser_filter_gif"
        BrowserFilter.PDF -> "browser_filter_pdf"
    }

@Composable
private fun BrowserLoadingStatus() {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("browser_refreshing"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
        )
        Text("正在加载子目录", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BrowserErrorStatus(
    message: String,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.testTag("browser_error"),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text("加载子目录失败", style = MaterialTheme.typography.labelLarge)
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}

private fun visiblePage(state: BrowserUiState): BrowserPage? = when (state) {
    is BrowserUiState.Content -> state.page
    is BrowserUiState.Empty -> state.page
    is BrowserUiState.Loading -> state.previous
    is BrowserUiState.Error -> state.previous
}

private fun currentTitle(state: BrowserUiState): String =
    visiblePage(state)?.breadcrumbs?.lastOrNull()?.label ?: "目录"
