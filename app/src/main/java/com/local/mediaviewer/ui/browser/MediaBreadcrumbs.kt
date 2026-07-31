package com.local.mediaviewer.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.local.mediaviewer.browser.Breadcrumb
import com.local.mediaviewer.ui.theme.MediaTheme

@Composable
fun MediaBreadcrumbs(
    breadcrumbs: List<Breadcrumb>,
    onBreadcrumbClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = MediaTheme.spacing.sm,
            vertical = MediaTheme.spacing.xs,
        ),
        horizontalArrangement = Arrangement.spacedBy(MediaTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(breadcrumbs) { index, breadcrumb ->
            if (index > 0) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.clearAndSetSemantics {},
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
