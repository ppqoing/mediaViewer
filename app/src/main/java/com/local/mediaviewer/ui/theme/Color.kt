package com.local.mediaviewer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

val DarkMediaColorScheme = darkColorScheme(
    primary = Color(0xFFE8A36C),
    onPrimary = Color(0xFF4B240B),
    primaryContainer = Color(0xFF6B3517),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFB7C298),
    onSecondary = Color(0xFF283115),
    secondaryContainer = Color(0xFF3E482B),
    onSecondaryContainer = Color(0xFFD3DEB2),
    tertiary = Color(0xFFD7A080),
    onTertiary = Color(0xFF442A1B),
    tertiaryContainer = Color(0xFF5D3B28),
    onTertiaryContainer = Color(0xFFFFDBC8),
    background = Color(0xFF1F1712),
    onBackground = Color(0xFFF5E7D3),
    surface = Color(0xFF2A1F18),
    onSurface = Color(0xFFF5E7D3),
    surfaceVariant = Color(0xFF4C3B30),
    onSurfaceVariant = Color(0xFFD8C5B5),
    outline = Color(0xFFA99584),
    outlineVariant = Color(0xFF554338),
    error = Color(0xFFFFB4A8),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF8D211B),
    onErrorContainer = Color(0xFFFFDAD5),
    inverseSurface = Color(0xFFF5E7D3),
    inverseOnSurface = Color(0xFF392F28),
    inversePrimary = Color(0xFFA64E1D),
    scrim = Color(0xFF160F0B),
)

val LightMediaColorScheme = lightColorScheme(
    primary = Color(0xFFC96B2C),
    onPrimary = Color(0xFFFFF7E8),
    primaryContainer = Color(0xFFF2C49F),
    onPrimaryContainer = Color(0xFF49220D),
    secondary = Color(0xFF77835F),
    onSecondary = Color(0xFFFFF7E8),
    secondaryContainer = Color(0xFFDCE4C7),
    onSecondaryContainer = Color(0xFF29311D),
    tertiary = Color(0xFF936247),
    onTertiary = Color(0xFFFFF7E8),
    tertiaryContainer = Color(0xFFF0D2BF),
    onTertiaryContainer = Color(0xFF3A2418),
    background = Color(0xFFF5EAD3),
    onBackground = Color(0xFF2E2118),
    surface = Color(0xFFFFF7E8),
    onSurface = Color(0xFF2E2118),
    surfaceVariant = Color(0xFFEAD9BA),
    onSurfaceVariant = Color(0xFF756454),
    outline = Color(0xFF8E795F),
    outlineVariant = Color(0xFFD8C5A5),
    error = Color(0xFFA64232),
    onError = Color(0xFFFFF7E8),
    errorContainer = Color(0xFFF3D0C8),
    onErrorContainer = Color(0xFF43120C),
    inverseSurface = Color(0xFF3D3027),
    inverseOnSurface = Color(0xFFFFF1DC),
    inversePrimary = Color(0xFFE8A36C),
    scrim = Color(0xFF2E2118),
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
    success = Color(0xFFA9B889),
    warning = Color(0xFFE8A36C),
    offline = Color(0xFFD6A35F),
    folder = Color(0xFFB7C298),
    video = Color(0xFFE8A36C),
    audio = Color(0xFFD7A080),
    image = Color(0xFFA9B889),
    unknown = Color(0xFFD8C5B5),
)

val LightMediaExtendedColors = MediaExtendedColors(
    success = Color(0xFF5F7448),
    warning = Color(0xFF9A641F),
    offline = Color(0xFF9A641F),
    folder = Color(0xFF77835F),
    video = Color(0xFFC96B2C),
    audio = Color(0xFF936247),
    image = Color(0xFF77835F),
    unknown = Color(0xFF756454),
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
    canvas = Color(0xFF160F0B),
    control = Color(0xFFFFF7E8),
    active = Color(0xFFC96B2C),
    accent = Color(0xFF77835F),
    disabled = Color(0xFFB7A99B).copy(alpha = 0.60f),
    buffering = Color(0xFFE08A45),
    playedTrack = Color(0xFFC96B2C),
    unplayedTrack = Color(0xFF8A7768).copy(alpha = 0.55f),
    volume = Color(0xFFE8A36C),
    brightness = Color(0xFFF0C46F),
    topScrimStart = Color(0xA82E2118),
    topScrimEnd = Color.Transparent,
    bottomScrimStart = Color.Transparent,
    bottomScrimEnd = Color(0xB82E2118),
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
