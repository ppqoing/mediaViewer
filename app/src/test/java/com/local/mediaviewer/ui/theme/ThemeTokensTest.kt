package com.local.mediaviewer.ui.theme

import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTokensTest {
    @Test
    fun `brand colors use approved light dark and video mappings`() {
        assertEquals(Color(0xFF67D9F0), DarkMediaColorScheme.primary)
        assertEquals(Color(0xFF006878), LightMediaColorScheme.primary)
        assertEquals(Color(0xFF67D9F0), DefaultPlayerColors.active)
        assertEquals(Color(0xFFF5FAFF), DefaultPlayerColors.control)
    }

    @Test
    fun `approved foreground pairs meet contrast gates`() {
        val textPairs = listOf(
            DarkMediaColorScheme.onBackground to DarkMediaColorScheme.background,
            DarkMediaColorScheme.onSurface to DarkMediaColorScheme.surface,
            LightMediaColorScheme.onBackground to LightMediaColorScheme.background,
            LightMediaColorScheme.onSurface to LightMediaColorScheme.surface,
        )
        textPairs.forEach { (foreground, background) ->
            assertTrue(
                "$foreground on $background",
                contrastRatio(foreground, background) >= 4.5f,
            )
        }
        assertTrue(
            contrastRatio(
                DefaultPlayerColors.control,
                DefaultPlayerColors.canvas,
            ) >= 3f,
        )
    }

    @Test
    fun `spacing shapes typography motion and dimensions stay on approved scale`() {
        assertEquals(16f, DefaultMediaSpacing.pageGutter.value)
        assertEquals(24f, DefaultMediaSpacing.widePageGutter.value)
        assertEquals(48f, DefaultMediaSizing.minimumTouchTarget.value)
        assertEquals(64f, DefaultMediaSizing.listRowMinHeight.value)
        assertEquals(72f, DefaultMediaSizing.miniPlayerHeight.value)
        assertEquals(RoundedCornerShape(8.dp), MediaShapes.small)
        assertEquals(RoundedCornerShape(12.dp), MediaShapes.medium)
        assertEquals(RoundedCornerShape(16.dp), MediaShapes.large)
        assertEquals(RoundedCornerShape(24.dp), MediaShapes.extraLarge)
        assertEquals(RoundedCornerShape(percent = 50), MediaPillShape)
        assertEquals(22.sp, MediaTypography.titleLarge.fontSize)
        assertEquals(28.sp, MediaTypography.titleLarge.lineHeight)
        assertEquals(FontWeight.SemiBold, MediaTypography.titleLarge.fontWeight)
        assertEquals(MediaTypography.titleLarge, MediaTextStyles.appTitle)
        assertEquals(MediaTypography.titleLarge, MediaTextStyles.screenTitle)
        assertEquals(MediaTypography.titleMedium, MediaTextStyles.sectionTitle)
        assertEquals(MediaTypography.bodyMedium, MediaTextStyles.body)
        assertEquals(MediaTypography.bodySmall, MediaTextStyles.metadata)
        assertEquals(MediaTypography.labelMedium, MediaTextStyles.badge)
        assertEquals(14.sp, MediaPlayerTimeStyle.fontSize)
        assertEquals("tnum", MediaPlayerTimeStyle.fontFeatureSettings)
        assertEquals(MediaPlayerTimeStyle, MediaTextStyles.playerTime)
        assertEquals(0.dp, DefaultMediaElevation.surface0)
        assertEquals(1.dp, DefaultMediaElevation.surface2)
        assertEquals(3.dp, DefaultMediaElevation.surface3)
        assertEquals(6.dp, DefaultMediaElevation.surface4)
        assertEquals(120, DefaultMediaMotion.pressMillis)
        assertEquals(180, DefaultMediaMotion.stateMillis)
        assertEquals(240, DefaultMediaMotion.overlayMillis)
        assertEquals(0, DefaultMediaMotion.durationMillis(180, durationScale = 0f))
        assertEquals(90, DefaultMediaMotion.durationMillis(180, durationScale = 0.5f))
        assertEquals(180, DefaultMediaMotion.durationMillis(180, durationScale = 1f))
        assertEquals(
            0,
            (DefaultMediaMotion.stateSpec<Float>(
                durationScale = 0f,
            ) as TweenSpec<*>).durationMillis,
        )
        assertEquals(
            240,
            (DefaultMediaMotion.overlaySpec<Float>(
                durationScale = 1f,
            ) as TweenSpec<*>).durationMillis,
        )
    }
}

private fun contrastRatio(foreground: Color, background: Color): Float {
    val lighter = maxOf(foreground.luminance(), background.luminance())
    val darker = minOf(foreground.luminance(), background.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}
