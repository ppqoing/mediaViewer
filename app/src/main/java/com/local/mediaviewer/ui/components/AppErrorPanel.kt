package com.local.mediaviewer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

/**
 * 显示可恢复错误及其操作按钮。
 *
 * @param message 面向用户的错误说明。
 * @param onRetry 用户点击操作按钮时执行的回调。
 * @param actionLabel 操作按钮文字，默认为“重试”。
 */
@Composable
fun AppErrorPanel(
    message: String,
    onRetry: () -> Unit,
    actionLabel: String = "重试",
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message)
        Button(onClick = onRetry) {
            Text(actionLabel)
        }
    }
}
