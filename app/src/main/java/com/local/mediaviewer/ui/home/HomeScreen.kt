package com.local.mediaviewer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.home.HomeUiState
import com.local.mediaviewer.model.ServerShare
import com.local.mediaviewer.ui.components.MediaAction
import com.local.mediaviewer.ui.components.MediaIconButton
import com.local.mediaviewer.ui.components.MediaScreenScaffold
import com.local.mediaviewer.ui.components.MediaStateKind
import com.local.mediaviewer.ui.components.MediaStatePanel
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
    MediaScreenScaffold(
        title = "MediaViewer",
        actions = {
            MediaIconButton(
                icon = MediaIcons.Settings,
                contentDescription = "设置",
                onClick = onOpenSettings,
            )
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
                    start = scaffoldPadding.calculateStartPadding(layoutDirection) +
                        pageGutter,
                    top = scaffoldPadding.calculateTopPadding() + MediaTheme.spacing.md,
                    end = scaffoldPadding.calculateEndPadding(layoutDirection) +
                        pageGutter,
                    bottom = scaffoldPadding.calculateBottomPadding() + MediaTheme.spacing.md,
                ),
                verticalArrangement = Arrangement.spacedBy(MediaTheme.spacing.sm),
            ) {
                item {
                    ConnectionStatusCard(
                        state = state,
                        onRetry = onRetry,
                        onOpenSettings = onOpenSettings,
                    )
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
                                text = "共享",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        items(
                            items = state.shares,
                            key = ServerShare::id,
                        ) { share ->
                            ShareCard(
                                share = share,
                                onClick = onOpenShare,
                            )
                        }
                    }
                }
            }
        }
    }
}
