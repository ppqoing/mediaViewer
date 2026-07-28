package com.local.mediaviewer.navigation

import com.local.mediaviewer.model.MediaKind
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object SettingsRoute

@Serializable
data class BrowserRoute(val rootId: String)

@Serializable
data class PlayerRoute(
    val name: String,
    val logicalUrl: String,
    val requestUrl: String,
    val mediaKey: String,
    val kind: MediaKind,
)

@Serializable
data class ImageReaderRoute(
    val rootId: String,
    val directoryLogicalUrl: String,
    val selectedLogicalUrl: String,
    val selectedName: String,
)
