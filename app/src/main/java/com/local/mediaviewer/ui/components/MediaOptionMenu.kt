package com.local.mediaviewer.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.ui.icons.MediaIcons
import com.local.mediaviewer.ui.icons.MediaIcon
import com.local.mediaviewer.ui.icons.MediaIconImage

@Immutable
data class MediaOption<T>(
    val key: T,
    val label: String,
    val icon: MediaIcon? = null,
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
                        when {
                            option.icon != null -> {
                                MediaIconImage(
                                    icon = option.icon,
                                    contentDescription = null,
                                    tint = LocalContentColor.current,
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            option.key == selectedKey -> {
                                // 规格 §7.1：菜单负责可见勾选；
                                // 选中语义之外必须有非颜色选择标记。
                                MediaIconImage(
                                    icon = MediaIcons.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag(
                                        "media_option_selected_check",
                                    ),
                                )
                                Spacer(Modifier.width(12.dp))
                            }
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
