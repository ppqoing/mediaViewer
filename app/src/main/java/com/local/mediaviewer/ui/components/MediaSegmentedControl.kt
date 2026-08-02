package com.local.mediaviewer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.ui.icons.MediaIcon
import com.local.mediaviewer.ui.icons.MediaIconImage
import com.local.mediaviewer.ui.theme.MediaPillShape
import com.local.mediaviewer.ui.theme.MediaTheme

@Immutable
data class SegmentItem(
    val id: String,
    val label: String,
    val icon: MediaIcon? = null,
)

@Composable
fun MediaSegmentedControl(
    items: List<SegmentItem>,
    selectedId: String,
    onSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(MediaPillShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { item ->
            val isSelected = item.id == selectedId
            val contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .semantics(mergeDescendants = true) {
                        selected = isSelected
                    }
                    .clickable(
                        role = Role.RadioButton,
                        onClick = { onSelected(item.id) },
                    )
                    .widthIn(min = MediaTheme.sizing.minimumTouchTarget)
                    .heightIn(min = MediaTheme.sizing.minimumTouchTarget)
                    .padding(horizontal = MediaTheme.spacing.md)
                    .testTag("segment_${item.id}"),
                horizontalArrangement = Arrangement.spacedBy(MediaTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item.icon?.let { icon ->
                    MediaIconImage(
                        icon = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = item.label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
