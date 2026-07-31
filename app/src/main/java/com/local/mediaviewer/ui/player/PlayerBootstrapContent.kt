package com.local.mediaviewer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.navigation.PlayerEntryState

@Composable
fun PlayerBootstrapContent(
    state: PlayerEntryState,
    onReconnect: () -> Unit,
    onBack: () -> Unit,
) {
    if (state is PlayerEntryState.Ready) return

    Box(modifier = Modifier.fillMaxSize()) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                contentDescription = "返回",
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state) {
                PlayerEntryState.Connecting -> {
                    CircularProgressIndicator()
                    Text("正在连接播放器")
                }

                PlayerEntryState.Empty -> Text("播放队列为空")

                is PlayerEntryState.Failed -> {
                    Text(state.message)
                    Button(onClick = onReconnect) {
                        Text("重连播放器")
                    }
                }

                is PlayerEntryState.Ready -> Unit
            }
        }
    }
}
