package com.local.mediaviewer.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import com.local.mediaviewer.model.ServerShare
import com.local.mediaviewer.model.ShareAuthenticationMode
import com.local.mediaviewer.ui.components.MediaGlyph
import com.local.mediaviewer.ui.icons.MediaIcons
import com.local.mediaviewer.ui.icons.MediaIconImage
import com.local.mediaviewer.ui.theme.MediaTheme

@Composable
fun ShareCard(
    share: ServerShare,
    onClick: (ServerShare) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unavailableReason = when {
        !share.directoryBrowsing -> "目录浏览未开放"
        share.authenticationMode == ShareAuthenticationMode.BASIC ->
            "不支持当前认证方式"
        else -> null
    }
    val cardModifier = modifier
        .fillMaxWidth()
        .testTag("share:${share.displayName}")
        .then(
            if (unavailableReason == null) {
                Modifier.clickable { onClick(share) }
            } else {
                Modifier.semantics { disabled() }
            },
        )

    Row(
        modifier = cardModifier.padding(MediaTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MediaTheme.spacing.sm),
    ) {
        MediaGlyph(
            icon = MediaIcons.NetworkShare,
            contentDescription = null,
            tint = MediaTheme.extendedColors.folder,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = share.displayName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = unavailableReason ?: share.urlPrefix,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (unavailableReason == null) {
            MediaIconImage(
                icon = MediaIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
