package com.local.mediaviewer.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.local.mediaviewer.ui.icons.MediaIcons
import com.local.mediaviewer.ui.icons.MediaIconImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaTopAppBar(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title) },
        modifier = modifier,
        navigationIcon = {
            onBack?.let { callback ->
                IconButton(onClick = callback) {
                    MediaIconImage(
                        icon = MediaIcons.Back,
                        contentDescription = "返回",
                        tint = contentColor,
                    )
                }
            }
        },
        actions = actions,
        windowInsets = windowInsets,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            titleContentColor = contentColor,
            navigationIconContentColor = contentColor,
            actionIconContentColor = contentColor,
        ),
    )
}
