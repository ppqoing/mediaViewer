package com.local.mediaviewer.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

@JvmInline
value class MediaIcon(@param:DrawableRes val resourceId: Int)

@Composable
fun MediaIconImage(
    icon: MediaIcon,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(icon.resourceId),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier,
    )
}
