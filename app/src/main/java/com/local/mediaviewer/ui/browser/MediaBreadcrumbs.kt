package com.local.mediaviewer.ui.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.browser.Breadcrumb
import com.local.mediaviewer.ui.icons.MediaIconImage
import com.local.mediaviewer.ui.icons.MediaIcons
import com.local.mediaviewer.ui.theme.MediaTheme

@Composable
fun MediaBreadcrumbs(
    breadcrumbs: List<Breadcrumb>,
    onBreadcrumbClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
        ),
        shadowElevation = 1.dp,
    ) {
        LazyRow(
            contentPadding = PaddingValues(
                horizontal = MediaTheme.spacing.sm,
                vertical = MediaTheme.spacing.xxs,
            ),
            horizontalArrangement = Arrangement.spacedBy(MediaTheme.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(breadcrumbs) { index, breadcrumb ->
                if (index > 0) {
                    MediaIconImage(
                        icon = MediaIcons.ChevronRight,
                        contentDescription = null,
                        tint = LocalContentColor.current,
                        modifier = Modifier
                            .requiredSize(20.dp)
                            .testTag("breadcrumb_separator_$index"),
                    )
                }
                val isCurrent = index == breadcrumbs.lastIndex
                TextButton(
                    onClick = { onBreadcrumbClick(index) },
                    modifier = Modifier
                        .testTag("breadcrumb_$index")
                        .then(
                            if (isCurrent) {
                                Modifier.semantics { selected = true }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Text(
                        text = breadcrumb.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
