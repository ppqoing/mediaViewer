package com.local.mediaviewer.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CaddyEntryDto(
    val name: String,
    val size: Long,
    val url: String,
    @SerialName("mod_time") val modifiedAt: String,
    val mode: Long,
    @SerialName("is_dir") val isDirectory: Boolean,
    @SerialName("is_symlink") val isSymlink: Boolean,
)
