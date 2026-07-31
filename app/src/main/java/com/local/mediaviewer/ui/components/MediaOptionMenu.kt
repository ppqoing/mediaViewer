package com.local.mediaviewer.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Immutable
data class MediaOption<T>(
    val key: T,
    val label: String,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
)

@Composable
fun <T> MediaOptionMenu(
    expanded: Boolean,
    options: List<MediaOption<T>>,
    selectedKey: T?,
    onSelect: (T) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        options.forEach { option ->
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        option.icon?.let { icon ->
                            Icon(icon, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(option.label)
                    }
                },
                onClick = { onSelect(option.key) },
                enabled = option.enabled,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    if (selectedKey != null) {
                        selected = option.key == selectedKey
                    }
                },
            )
        }
    }
}
