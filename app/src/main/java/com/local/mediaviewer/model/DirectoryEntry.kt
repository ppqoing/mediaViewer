package com.local.mediaviewer.model

import java.time.Instant

data class DirectoryEntry(
    val name: String,
    val size: Long,
    val modifiedAt: Instant,
    val mode: Long,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val logicalUrl: String,
    val requestUrl: String,
    val kind: MediaKind,
)
