package com.local.mediaviewer.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object SettingsRoute

@Serializable
data class BrowserRoute(val rootId: String)

@Serializable
data class PlayerRoute(val mediaKey: String)

@Serializable
data class ImageReaderRoute(
    val rootId: String,
    val directoryLogicalUrl: String,
    val selectedLogicalUrl: String,
    val selectedName: String,
)

@Serializable
data class PdfReaderRoute(
    val rootId: String,
    val logicalUrl: String,
    val fileName: String,
)
