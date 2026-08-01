package com.local.mediaviewer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.local.mediaviewer.ui.theme.MediaTheme

@Composable
fun PlayerUtilityRow(
    startContent: @Composable RowScope.() -> Unit,
    endContent: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("player_utility_layer"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.testTag("player_utility_start_group"),
            horizontalArrangement = Arrangement.spacedBy(MediaTheme.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            content = startContent,
        )
        Row(
            modifier = Modifier.testTag("player_utility_end_group"),
            horizontalArrangement = Arrangement.spacedBy(MediaTheme.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            content = endContent,
        )
    }
}
