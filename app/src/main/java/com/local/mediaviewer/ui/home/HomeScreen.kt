package com.local.mediaviewer.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.home.HomeUiState
import com.local.mediaviewer.model.ServerShare
import com.local.mediaviewer.ui.components.MediaAction
import com.local.mediaviewer.ui.components.MediaBottomNavigation
import com.local.mediaviewer.ui.components.MediaGlyph
import com.local.mediaviewer.ui.components.MediaIconButton
import com.local.mediaviewer.ui.components.MediaOption
import com.local.mediaviewer.ui.components.MediaOptionMenu
import com.local.mediaviewer.ui.components.MediaScreenScaffold
import com.local.mediaviewer.ui.components.MediaStateKind
import com.local.mediaviewer.ui.components.MediaStatePanel
import com.local.mediaviewer.ui.components.TopLevelDestination
import com.local.mediaviewer.ui.components.WarmPaperCard
import com.local.mediaviewer.ui.icons.MediaIcons
import com.local.mediaviewer.ui.theme.MediaTheme

/**
 * 展示服务器连接状态和可浏览共享入口。
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenShare: (ServerShare) -> Unit,
) {
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var moreMenuVisible by rememberSaveable { mutableStateOf(false) }
    val connectedShares = (state as? HomeUiState.Connected)?.shares.orEmpty()
    val filteredShares = if (searchQuery.isBlank()) {
        connectedShares
    } else {
        connectedShares.filter { share ->
            share.displayName.contains(searchQuery, ignoreCase = true) ||
                share.urlPrefix.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MediaScreenScaffold(
            title = "媒体源",
            modifier = Modifier.weight(1f),
            actions = {
                MediaIconButton(
                    icon = if (searchVisible) MediaIcons.Close else MediaIcons.Search,
                    contentDescription = if (searchVisible) "关闭搜索" else "搜索媒体源",
                    onClick = {
                        searchVisible = !searchVisible
                        if (!searchVisible) searchQuery = ""
                    },
                    modifier = Modifier.testTag("home_search_action"),
                )
                Box {
                    MediaIconButton(
                        icon = MediaIcons.More,
                        contentDescription = "更多",
                        onClick = { moreMenuVisible = true },
                    )
                    MediaOptionMenu(
                        expanded = moreMenuVisible,
                        options = listOf(
                            MediaOption(
                                key = "settings",
                                label = "设置",
                                icon = MediaIcons.Settings,
                            ),
                        ),
                        selectedKey = null,
                        onSelect = {
                            moreMenuVisible = false
                            onOpenSettings()
                        },
                        onDismissRequest = { moreMenuVisible = false },
                    )
                }
            },
        ) { scaffoldPadding ->
            val layoutDirection = LocalLayoutDirection.current
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val pageGutter = if (maxWidth >= 600.dp) {
                    MediaTheme.spacing.widePageGutter
                } else {
                    MediaTheme.spacing.pageGutter
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("home_list"),
                    contentPadding = PaddingValues(
                        start = scaffoldPadding.calculateStartPadding(
                            layoutDirection,
                        ) + pageGutter,
                        top = scaffoldPadding.calculateTopPadding() +
                            MediaTheme.spacing.md,
                        end = scaffoldPadding.calculateEndPadding(
                            layoutDirection,
                        ) + pageGutter,
                        bottom = scaffoldPadding.calculateBottomPadding() +
                            MediaTheme.spacing.md,
                    ),
                    verticalArrangement = Arrangement.spacedBy(
                        MediaTheme.spacing.sm,
                    ),
                ) {
                    if (searchVisible) {
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("home_search_field"),
                                singleLine = true,
                                label = { Text("搜索名称或路径") },
                            )
                        }
                    }
                    item {
                        ConnectionStatusCard(
                            state = state,
                            onRetry = onRetry,
                            onOpenSettings = onOpenSettings,
                        )
                    }
                    item {
                        Text(
                            text = "快捷操作",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    item {
                        WarmPaperCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_quick_actions"),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onOpenSettings)
                                    .testTag("home_settings_entry")
                                    .semantics {
                                        contentDescription = "设置"
                                    }
                                    .padding(MediaTheme.spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(
                                    MediaTheme.spacing.sm,
                                ),
                            ) {
                                MediaGlyph(
                                    icon = MediaIcons.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "设置",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = "服务器、播放与阅读偏好",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                MediaGlyph(
                                    icon = MediaIcons.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (state is HomeUiState.Connected) {
                        if (state.shares.isEmpty()) {
                            item {
                                MediaStatePanel(
                                    kind = MediaStateKind.EMPTY,
                                    title = "没有可浏览的共享",
                                    primaryAction = MediaAction(
                                        label = "服务器设置",
                                        onClick = onOpenSettings,
                                    ),
                                )
                            }
                        } else {
                            item {
                                Text(
                                    text = "已保存的媒体源",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            item {
                                WarmPaperCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("home_saved_shares"),
                                ) {
                                    filteredShares.forEachIndexed { index, share ->
                                        if (index > 0) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(
                                                    horizontal = MediaTheme.spacing.md,
                                                ),
                                            )
                                        }
                                        ShareCard(
                                            share = share,
                                            onClick = onOpenShare,
                                        )
                                    }
                                    if (filteredShares.isEmpty()) {
                                        Text(
                                            text = "没有匹配的媒体源",
                                            modifier = Modifier.padding(
                                                MediaTheme.spacing.lg,
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        MediaBottomNavigation(
            selected = TopLevelDestination.MEDIA_SOURCES,
            onSelect = { destination ->
                if (destination == TopLevelDestination.SETTINGS) {
                    onOpenSettings()
                }
            },
        )
    }
}
