package com.local.mediaviewer.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.browser.BrowserPage
import com.local.mediaviewer.browser.BrowserPlaybackAction
import com.local.mediaviewer.browser.BrowserUiState
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.ui.components.MediaAction
import com.local.mediaviewer.ui.components.MediaScreenScaffold
import com.local.mediaviewer.ui.components.MediaStateKind
import com.local.mediaviewer.ui.components.MediaStatePanel

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
    MediaScreenScaffold(
        title = currentTitle(state),
        onBack = onBack,
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
    ) { padding ->
        val visiblePage = visiblePage(state)
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
    isEmpty: Boolean,
    statusContent: (@Composable () -> Unit)? = null,
) {
    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        MediaBreadcrumbs(
            breadcrumbs = page.breadcrumbs,
            onBreadcrumbClick = onBreadcrumbClick,
        )
        statusContent?.invoke()
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("browser_list"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isEmpty) {
                item {
                    MediaStatePanel(
                        kind = MediaStateKind.EMPTY,
                        title = "此目录为空",
                    )
                }
            } else {
                items(
                    items = page.entries,
                    key = DirectoryEntry::logicalUrl,
                ) { entry ->
                    MediaFileRow(
                        entry = entry,
                        onEntryClick = onEntryClick,
                        onPlaybackAction = onPlaybackAction,
                    )
                }
            }
        }
    }
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
