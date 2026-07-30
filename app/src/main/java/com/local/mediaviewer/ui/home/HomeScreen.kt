package com.local.mediaviewer.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.home.HomeUiState
import com.local.mediaviewer.model.ServerShare
import com.local.mediaviewer.model.ShareAuthenticationMode
import com.local.mediaviewer.ui.components.AppErrorPanel

/**
 * 展示服务器连接状态和 RangeShelf 动态共享入口。
 *
 * @param state 当前首页状态。
 * @param onRetry 重试连接回调。
 * @param onOpenSettings 打开设置页回调。
 * @param onOpenShare 打开当前客户端可浏览共享的回调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenShare: (ServerShare) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("mediaviewer") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                HomeUiState.Connecting -> {
                    Text("正在连接服务器…")
                    CircularProgressIndicator()
                }

                is HomeUiState.Error -> {
                    AppErrorPanel(state.message, onRetry)
                }

                is HomeUiState.Connected -> {
                    Text("当前 IPv4：${state.ipv4}")
                    if (state.shares.isEmpty()) {
                        Text("服务器当前没有启用的共享")
                    }
                    state.shares.forEach { share ->
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = share.canBrowse,
                                    onClick = { onOpenShare(share) },
                                ),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(text = share.displayName)
                                if (!share.directoryBrowsing) {
                                    Text("服务器未启用目录浏览")
                                } else if (
                                    share.authenticationMode ==
                                    ShareAuthenticationMode.BASIC
                                ) {
                                    Text("需要 Basic Auth，当前版本暂不能进入")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
