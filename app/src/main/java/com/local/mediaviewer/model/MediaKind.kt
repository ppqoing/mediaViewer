package com.local.mediaviewer.model

import kotlinx.serialization.Serializable

@Serializable
enum class MediaKind {
    DIRECTORY,
    VIDEO,
    AUDIO,
    IMAGE,
    PDF,
    UNKNOWN,
}
