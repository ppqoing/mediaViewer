package com.local.mediaviewer.settings

enum class VideoControlsAutoHide(
    val delayMs: Long?,
) {
    THREE_SECONDS(3_000L),
    FIVE_SECONDS(5_000L),
    TEN_SECONDS(10_000L),
    FIFTEEN_SECONDS(15_000L),
    NEVER(null),
    ;

    companion object {
        fun fromStored(value: String?): VideoControlsAutoHide =
            entries.firstOrNull { it.name == value } ?: THREE_SECONDS
    }
}
