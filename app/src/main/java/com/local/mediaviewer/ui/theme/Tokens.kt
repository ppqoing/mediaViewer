package com.local.mediaviewer.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class MediaSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val pageGutter: Dp = 16.dp,
    val widePageGutter: Dp = 24.dp,
)

@Immutable
data class MediaSizing(
    val minimumTouchTarget: Dp = 48.dp,
    val listRowMinHeight: Dp = 64.dp,
    val miniPlayerHeight: Dp = 72.dp,
    val playerPrimaryButton: Dp = 64.dp,
    val fullscreenPrimaryButton: Dp = 72.dp,
    val timelineTouchHeight: Dp = 48.dp,
    val timelineTrackHeight: Dp = 4.dp,
    val timelineThumbSize: Dp = 18.dp,
    val verticalLevelWidth: Dp = 48.dp,
    val verticalLevelHeight: Dp = 160.dp,
    val volumePopupWidth: Dp = 72.dp,
    val volumePopupHeight: Dp = 224.dp,
    val miniPlayerProgressHeight: Dp = 2.dp,
)

@Immutable
data class MediaMotion(
    val pressMillis: Int = 120,
    val stateMillis: Int = 180,
    val overlayMillis: Int = 240,
)

fun MediaMotion.durationMillis(
    requestedMillis: Int,
    durationScale: Float,
): Int = (requestedMillis * durationScale.coerceAtLeast(0f))
    .toInt()

fun <T> MediaMotion.pressSpec(
    durationScale: Float,
): FiniteAnimationSpec<T> = tween(
    durationMillis = durationMillis(pressMillis, durationScale),
    easing = LinearOutSlowInEasing,
)

fun <T> MediaMotion.stateSpec(
    durationScale: Float,
): FiniteAnimationSpec<T> = tween(
    durationMillis = durationMillis(stateMillis, durationScale),
    easing = FastOutSlowInEasing,
)

fun <T> MediaMotion.overlaySpec(
    durationScale: Float,
): FiniteAnimationSpec<T> = tween(
    durationMillis = durationMillis(overlayMillis, durationScale),
    easing = FastOutSlowInEasing,
)

@Composable
fun platformMotionDurationScale(): Float {
    val scope = rememberCoroutineScope()
    return scope.coroutineContext[MotionDurationScale]
        ?.scaleFactor
        ?: 1f
}

@Immutable
data class MediaElevation(
    val surface0: Dp = 0.dp,
    val surface1: Dp = 0.dp,
    val surface2: Dp = 1.dp,
    val surface3: Dp = 3.dp,
    val surface4: Dp = 6.dp,
)

val DefaultMediaSpacing = MediaSpacing()
val DefaultMediaSizing = MediaSizing()
val DefaultMediaMotion = MediaMotion()
val DefaultMediaElevation = MediaElevation()

val LocalMediaExtendedColors = staticCompositionLocalOf {
    DarkMediaExtendedColors
}
val LocalPlayerColors = staticCompositionLocalOf {
    DefaultPlayerColors
}
val LocalMediaSpacing = staticCompositionLocalOf {
    DefaultMediaSpacing
}
val LocalMediaSizing = staticCompositionLocalOf {
    DefaultMediaSizing
}
val LocalMediaMotion = staticCompositionLocalOf {
    DefaultMediaMotion
}
val LocalMediaElevation = staticCompositionLocalOf {
    DefaultMediaElevation
}
