package com.local.mediaviewer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.local.mediaviewer.ui.components.WarmPaperCard
import com.local.mediaviewer.ui.theme.MediaTheme

@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    WarmPaperCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MediaTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MediaTheme.spacing.xxs),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(MediaTheme.spacing.sm),
            content = content,
        )
    }
}

@Composable
fun SettingsChoiceRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MediaTheme.sizing.listRowMinHeight)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = MediaTheme.spacing.md, vertical = MediaTheme.spacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
