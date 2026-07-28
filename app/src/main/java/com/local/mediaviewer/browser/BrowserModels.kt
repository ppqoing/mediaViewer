package com.local.mediaviewer.browser

import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.RootShare

data class Breadcrumb(
    val label: String,
    val logicalUrl: String,
)

data class BrowserPage(
    val root: RootShare,
    val logicalDirectoryUrl: String,
    val requestDirectoryUrl: String,
    val breadcrumbs: List<Breadcrumb>,
    val entries: List<DirectoryEntry>,
)

data class MediaLaunchRequest(
    val name: String,
    val logicalUrl: String,
    val requestUrl: String,
    val mediaKey: String,
    val kind: MediaKind,
)
