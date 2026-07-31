package com.local.mediaviewer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val MediaTypography = Typography(
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
)

val MediaPlayerTimeStyle = MediaTypography.labelLarge.copy(
    fontFeatureSettings = "tnum",
)

object MediaTextStyles {
    val appTitle: TextStyle get() = MediaTypography.titleLarge
    val screenTitle: TextStyle get() = MediaTypography.titleLarge
    val sectionTitle: TextStyle get() = MediaTypography.titleMedium
    val body: TextStyle get() = MediaTypography.bodyMedium
    val metadata: TextStyle get() = MediaTypography.bodySmall
    val badge: TextStyle get() = MediaTypography.labelMedium
    val playerTime: TextStyle get() = MediaPlayerTimeStyle
}
