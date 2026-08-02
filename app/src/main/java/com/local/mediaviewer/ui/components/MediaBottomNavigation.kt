package com.local.mediaviewer.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.ui.icons.MediaIcon
import com.local.mediaviewer.ui.icons.MediaIconImage
import com.local.mediaviewer.ui.icons.MediaIcons

enum class TopLevelDestination {
    MEDIA_SOURCES,
    SETTINGS,
}

private data class TopLevelNavigationItem(
    val destination: TopLevelDestination,
    val label: String,
    val icon: MediaIcon,
    val testTag: String,
)

private val topLevelNavigationItems = listOf(
    TopLevelNavigationItem(
        destination = TopLevelDestination.MEDIA_SOURCES,
        label = "媒体源",
        icon = MediaIcons.NetworkShare,
        testTag = "bottom_nav_media_sources",
    ),
    TopLevelNavigationItem(
        destination = TopLevelDestination.SETTINGS,
        label = "设置",
        icon = MediaIcons.Settings,
        testTag = "bottom_nav_settings",
    ),
)

@Composable
fun MediaBottomNavigation(
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        topLevelNavigationItems.forEach { item ->
            NavigationBarItem(
                selected = item.destination == selected,
                onClick = { onSelect(item.destination) },
                icon = {
                    MediaIconImage(
                        icon = item.icon,
                        contentDescription = null,
                        tint = LocalContentColor.current,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = { Text(item.label) },
                modifier = Modifier.testTag(item.testTag),
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
