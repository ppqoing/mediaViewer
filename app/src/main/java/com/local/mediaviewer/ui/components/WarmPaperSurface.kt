package com.local.mediaviewer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val PaperGrainAlpha = 0.018f

private val paperGrainPositions = listOf(
    Offset(0.04f, 0.11f),
    Offset(0.12f, 0.73f),
    Offset(0.18f, 0.32f),
    Offset(0.24f, 0.89f),
    Offset(0.31f, 0.53f),
    Offset(0.38f, 0.17f),
    Offset(0.43f, 0.68f),
    Offset(0.49f, 0.39f),
    Offset(0.56f, 0.92f),
    Offset(0.62f, 0.24f),
    Offset(0.69f, 0.61f),
    Offset(0.75f, 0.08f),
    Offset(0.81f, 0.81f),
    Offset(0.88f, 0.46f),
    Offset(0.94f, 0.19f),
    Offset(0.97f, 0.77f),
)

/**
 * 低对比纸张卡片。颗粒坐标固定，并只在尺寸或主题色变化时重新缓存。
 * 媒体内容区域不应使用此组件。
 */
@Composable
fun WarmPaperCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val paperColor = MaterialTheme.colorScheme.surface
    val grainColor = MaterialTheme.colorScheme.onSurface.copy(
        alpha = PaperGrainAlpha,
    )
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(
                alpha = 0.72f,
            ),
        ),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.drawWithCache {
                val radius = 0.55.dp.toPx()
                val grainCenters = paperGrainPositions.map { position ->
                    Offset(
                        x = size.width * position.x,
                        y = size.height * position.y,
                    )
                }
                onDrawWithContent {
                    drawRect(paperColor)
                    grainCenters.forEach { center ->
                        drawCircle(
                            color = grainColor,
                            radius = radius,
                            center = center,
                        )
                    }
                    drawContent()
                }
            },
            content = content,
        )
    }
}
