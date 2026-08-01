package com.local.mediaviewer.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun MediaScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            MediaTopAppBar(
                title = title,
                onBack = onBack,
                actions = actions,
                windowInsets = contentWindowInsets.only(
                    WindowInsetsSides.Top +
                        WindowInsetsSides.Horizontal,
                ),
            )
        },
        snackbarHost = snackbarHost,
        contentWindowInsets = contentWindowInsets,
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}
