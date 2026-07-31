package com.local.mediaviewer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun MediaViewerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val extended = if (darkTheme) {
        DarkMediaExtendedColors
    } else {
        LightMediaExtendedColors
    }
    CompositionLocalProvider(
        LocalMediaExtendedColors provides extended,
        LocalPlayerColors provides DefaultPlayerColors,
        LocalMediaSpacing provides DefaultMediaSpacing,
        LocalMediaSizing provides DefaultMediaSizing,
        LocalMediaMotion provides DefaultMediaMotion,
        LocalMediaElevation provides DefaultMediaElevation,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) {
                DarkMediaColorScheme
            } else {
                LightMediaColorScheme
            },
            typography = MediaTypography,
            shapes = MediaShapes,
            content = content,
        )
    }
}

object MediaTheme {
    val extendedColors: MediaExtendedColors
        @Composable get() = LocalMediaExtendedColors.current

    val playerColors: PlayerColors
        @Composable get() = LocalPlayerColors.current

    val spacing: MediaSpacing
        @Composable get() = LocalMediaSpacing.current

    val sizing: MediaSizing
        @Composable get() = LocalMediaSizing.current

    val motion: MediaMotion
        @Composable get() = LocalMediaMotion.current

    val elevation: MediaElevation
        @Composable get() = LocalMediaElevation.current
}
