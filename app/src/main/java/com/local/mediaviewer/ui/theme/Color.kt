package com.local.mediaviewer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

val DarkMediaColorScheme = darkColorScheme(
    primary = Color(0xFF67D9F0),
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF004E5B),
    onPrimaryContainer = Color(0xFFA9EEFF),
    secondary = Color(0xFFBEC8D1),
    onSecondary = Color(0xFF28323A),
    secondaryContainer = Color(0xFF3E4851),
    onSecondaryContainer = Color(0xFFDAE4ED),
    tertiary = Color(0xFFC9BFFF),
    onTertiary = Color(0xFF312C61),
    tertiaryContainer = Color(0xFF484378),
    onTertiaryContainer = Color(0xFFE6DEFF),
    background = Color(0xFF080C12),
    onBackground = Color(0xFFDFE3E8),
    surface = Color(0xFF111821),
    onSurface = Color(0xFFDFE3E8),
    surfaceVariant = Color(0xFF3F484C),
    onSurfaceVariant = Color(0xFFBFC8CC),
    outline = Color(0xFF899296),
    outlineVariant = Color(0xFF3F484C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFDFE3E8),
    inverseOnSurface = Color(0xFF2E3135),
    inversePrimary = Color(0xFF006878),
    scrim = Color.Black,
)

val LightMediaColorScheme = lightColorScheme(
    primary = Color(0xFF006878),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA9EEFF),
    onPrimaryContainer = Color(0xFF001F26),
    secondary = Color(0xFF4F6169),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E5ED),
    onSecondaryContainer = Color(0xFF0B1E24),
    tertiary = Color(0xFF615B91),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6DEFF),
    onTertiaryContainer = Color(0xFF1D174B),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF191C1E),
    surface = Color.White,
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDBE4E8),
    onSurfaceVariant = Color(0xFF3F484C),
    outline = Color(0xFF6F797D),
    outlineVariant = Color(0xFFBFC8CC),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF2E3135),
    inverseOnSurface = Color(0xFFF0F1F3),
    inversePrimary = Color(0xFF67D9F0),
    scrim = Color.Black,
)

@Immutable
data class MediaExtendedColors(
    val success: Color,
    val warning: Color,
    val offline: Color,
    val folder: Color,
    val video: Color,
    val audio: Color,
    val image: Color,
    val unknown: Color,
)

val DarkMediaExtendedColors = MediaExtendedColors(
    success = Color(0xFF63D89A),
    warning = Color(0xFFF6C66A),
    offline = Color(0xFFF6C66A),
    folder = Color(0xFFF6C66A),
    video = Color(0xFF67D9F0),
    audio = Color(0xFFC9BFFF),
    image = Color(0xFF63D89A),
    unknown = Color(0xFFBFC8CC),
)

val LightMediaExtendedColors = MediaExtendedColors(
    success = Color(0xFF146C43),
    warning = Color(0xFF805600),
    offline = Color(0xFF805600),
    folder = Color(0xFF805600),
    video = Color(0xFF006878),
    audio = Color(0xFF615B91),
    image = Color(0xFF146C43),
    unknown = Color(0xFF596367),
)

@Immutable
data class PlayerColors(
    val canvas: Color,
    val control: Color,
    val active: Color,
    val accent: Color,
    val disabled: Color,
    val buffering: Color,
    val playedTrack: Color,
    val unplayedTrack: Color,
    val volume: Color,
    val brightness: Color,
    val topScrimStart: Color,
    val topScrimEnd: Color,
    val bottomScrimStart: Color,
    val bottomScrimEnd: Color,
)

val DefaultPlayerColors = PlayerColors(
    canvas = Color.Black,
    control = Color(0xFFF5FAFF),
    active = Color(0xFF67D9F0),
    accent = Color(0xFFC9BFFF),
    disabled = Color(0xFF7D878F).copy(alpha = 0.60f),
    buffering = Color(0xFF67D9F0),
    playedTrack = Color(0xFF67D9F0),
    unplayedTrack = Color(0xFF59636D).copy(alpha = 0.55f),
    volume = Color(0xFF67D9F0),
    brightness = Color(0xFFF6C66A),
    topScrimStart = Color(0xB3000000),
    topScrimEnd = Color.Transparent,
    bottomScrimStart = Color.Transparent,
    bottomScrimEnd = Color(0xCC000000),
)

// 普通主题 surface（迷你播放器、队列浮层等 surface3/surface4）上的
// 播放器控件颜色：黑底 PlayerColors 的近白控件在浅色页面对比度不足，
// 按规格 §6.1 改用当前 ColorScheme 的角色色。
fun surfacePlayerColors(colorScheme: ColorScheme): PlayerColors =
    DefaultPlayerColors.copy(
        control = colorScheme.onSurface,
        active = colorScheme.primary,
        accent = colorScheme.tertiary,
        disabled = colorScheme.outline,
    )
