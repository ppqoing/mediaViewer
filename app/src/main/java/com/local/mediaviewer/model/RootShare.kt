package com.local.mediaviewer.model

enum class RootShare(
    val id: String,
    val displayName: String,
    val path: String,
) {
    MIDDLE("middle", "MiddleDir", "/middle/"),
    PIK("pik", "pik", "/pik/");

    companion object {
        fun fromId(id: String): RootShare =
            entries.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("未知根目录：$id")
    }
}
