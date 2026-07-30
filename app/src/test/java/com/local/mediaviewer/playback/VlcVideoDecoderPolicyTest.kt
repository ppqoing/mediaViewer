package com.local.mediaviewer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VlcVideoDecoderPolicyTest {
    @Test
    fun `兼容模式保留硬件解码但关闭直接渲染`() {
        val configuration = VlcVideoDecoderPolicy.compatibility

        assertTrue(configuration.hardwareDecodingEnabled)
        assertFalse(configuration.forceHardwareDecoding)
        assertEquals(
            listOf(
                ":no-mediacodec-dr",
                ":no-omxil-dr",
            ),
            configuration.mediaOptions,
        )
    }
}
