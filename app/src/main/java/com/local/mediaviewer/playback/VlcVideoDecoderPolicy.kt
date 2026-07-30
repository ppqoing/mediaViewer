package com.local.mediaviewer.playback

internal data class VlcVideoDecoderConfiguration(
    val hardwareDecodingEnabled: Boolean,
    val forceHardwareDecoding: Boolean,
    val mediaOptions: List<String>,
)

internal object VlcVideoDecoderPolicy {
    val compatibility = VlcVideoDecoderConfiguration(
        hardwareDecodingEnabled = true,
        forceHardwareDecoding = false,
        mediaOptions = listOf(
            ":no-mediacodec-dr",
            ":no-omxil-dr",
        ),
    )
}
