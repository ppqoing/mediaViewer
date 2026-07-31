package com.local.mediaviewer.ui.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.image.ImageItemFailure
import com.local.mediaviewer.image.ImageLoadFailureKind
import com.local.mediaviewer.image.ImageReaderItem
import com.local.mediaviewer.ui.components.MediaGlyph
import com.local.mediaviewer.ui.icons.MediaIcons
import com.local.mediaviewer.ui.theme.MediaTheme

@Composable
internal fun ImageItemErrorPanel(
    item: ImageReaderItem,
    failure: ImageItemFailure,
    onRetry: () -> Unit,
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val playerColors = MediaTheme.playerColors
    val retryLabel = when (failure.kind) {
        ImageLoadFailureKind.NETWORK ->
            "重新连接并重试"

        ImageLoadFailureKind.DECODE ->
            "重试此图"
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
            },
        shape = MaterialTheme.shapes.large,
        color = playerColors.unplayedTrack.copy(
            alpha = 0.42f,
        ),
        contentColor = playerColors.control,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = MediaTheme.spacing.lg,
                vertical = MediaTheme.spacing.md,
            ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                MediaTheme.spacing.xs,
                Alignment.CenterVertically,
            ),
        ) {
            MediaGlyph(
                icon = MediaIcons.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = item.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                color = playerColors.control,
            )
            Text(
                text = failure.message,
                style = MaterialTheme.typography.bodyMedium,
                color = playerColors.control.copy(
                    alpha = 0.82f,
                ),
            )
            Button(
                onClick = onRetry,
                enabled = !isRefreshing,
                modifier = Modifier.testTag(
                    "retry_image:" +
                        item.logicalUrl.hashCode(),
                ).heightIn(
                    min = MediaTheme.sizing.minimumTouchTarget,
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor =
                        playerColors.active,
                    contentColor =
                        playerColors.canvas,
                    disabledContainerColor =
                        playerColors.active.copy(
                            alpha = 0.38f,
                        ),
                    disabledContentColor =
                        playerColors.control.copy(
                            alpha = 0.6f,
                        ),
                ),
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .testTag(
                                "retry_image_loading:" +
                                    item.logicalUrl
                                        .hashCode(),
                            ),
                        color = playerColors.control,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(retryLabel)
                }
            }
        }
    }
}

@Composable
internal fun ImageItemLoadingPanel(
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
) {
    val playerColors = MediaTheme.playerColors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(playerColors.canvas),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(
                MediaTheme.spacing.xs,
                Alignment.CenterVertically,
            ),
    ) {
        if (errorMessage == null) {
            CircularProgressIndicator(
                color = playerColors.active,
            )
        } else {
            MediaGlyph(
                icon = MediaIcons.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = errorMessage,
                color = playerColors.control,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
