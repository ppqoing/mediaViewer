package com.local.mediaviewer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.ui.icons.MediaIcon
import com.local.mediaviewer.ui.icons.MediaIconImage
import com.local.mediaviewer.ui.theme.MediaPillShape
import com.local.mediaviewer.ui.theme.MediaTheme

@Immutable
data class FilterChipItem(
    val id: String,
    val label: String,
    val icon: MediaIcon? = null,
)

@Composable
fun MediaFilterChips(
    items: List<FilterChipItem>,
    selectedId: String,
    onSelected: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = MediaTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(MediaTheme.spacing.xs),
    ) {
        items(
            items = items,
            key = FilterChipItem::id,
        ) { item ->
            val isSelected = item.id == selectedId
            FilterChip(
                selected = isSelected,
                onClick = { onSelected(item.id) },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                leadingIcon = item.icon?.let { icon ->
                    {
                        MediaIconImage(
                            icon = icon,
                            contentDescription = null,
                            tint = LocalContentColor.current,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                modifier = Modifier
                    .widthIn(min = MediaTheme.sizing.minimumTouchTarget)
                    .heightIn(min = MediaTheme.sizing.minimumTouchTarget)
                    .testTag("filter_${item.id}"),
                shape = MediaPillShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}
