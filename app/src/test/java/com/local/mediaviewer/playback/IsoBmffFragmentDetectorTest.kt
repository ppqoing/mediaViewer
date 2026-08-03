package com.local.mediaviewer.playback

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class IsoBmffFragmentDetectorTest {
    @Test
    fun `dash compatible brand is fragmented`() {
        val input = ftyp("isom", "iso6", "dash")
        assertEquals(FragmentedMp4Detection.FRAGMENTED, IsoBmffFragmentDetector.detect(input))
    }

    @Test
    fun `all supported fragmented brands are detected`() {
        listOf("msdh", "msix", "dsms").forEach { brand ->
            assertEquals(brand, FragmentedMp4Detection.FRAGMENTED, IsoBmffFragmentDetector.detect(ftyp(brand)))
        }
    }

    @Test
    fun `mvex nested in moov is fragmented`() {
        val input = concat(ftyp("isom", "iso6"), box("moov", box("mvex")))
        assertEquals(FragmentedMp4Detection.FRAGMENTED, IsoBmffFragmentDetector.detect(input))
    }

    @Test
    fun `top level moof is fragmented`() {
        val input = concat(ftyp("isom", "iso6"), box("moof"))
        assertEquals(FragmentedMp4Detection.FRAGMENTED, IsoBmffFragmentDetector.detect(input))
    }

    @Test
    fun `flat isom mp4 is standard`() {
        val input = concat(ftyp("isom", "iso6", "avc1"), box("moov", box("trak")), zeroSizedBox("mdat", byteArrayOf(1, 2, 3, 4)))
        assertEquals(FragmentedMp4Detection.STANDARD, IsoBmffFragmentDetector.detect(input))
    }

    @Test
    fun `extended size box is parsed`() {
        val input = concat(ftyp("isom"), extendedBox("moof"))
        assertEquals(FragmentedMp4Detection.FRAGMENTED, IsoBmffFragmentDetector.detect(input))
    }

    @Test
    fun `short header and impossible sizes are malformed`() {
        val shortHeader = byteArrayOf(0, 0, 0, 8, 'f'.code.toByte())
        val smallerThanHeader = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putInt(4).put(ascii("free")).array()
        val overflowingExtendedSize = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN).putInt(1).put(ascii("free")).putLong(-1L).array()
        listOf(shortHeader, smallerThanHeader, overflowingExtendedSize).forEach { input ->
            assertEquals(FragmentedMp4Detection.MALFORMED, IsoBmffFragmentDetector.detect(input))
        }
    }

    private fun ftyp(major: String, vararg compatible: String): ByteArray = box("ftyp", concat(ascii(major), byteArrayOf(0, 0, 0, 0), *compatible.map(::ascii).toTypedArray()))
    private fun box(type: String, payload: ByteArray = byteArrayOf()): ByteArray = ByteBuffer.allocate(8 + payload.size).order(ByteOrder.BIG_ENDIAN).putInt(8 + payload.size).put(ascii(type)).put(payload).array()
    private fun extendedBox(type: String, payload: ByteArray = byteArrayOf()): ByteArray = ByteBuffer.allocate(16 + payload.size).order(ByteOrder.BIG_ENDIAN).putInt(1).put(ascii(type)).putLong(16L + payload.size).put(payload).array()
    private fun zeroSizedBox(type: String, payload: ByteArray): ByteArray = ByteBuffer.allocate(8 + payload.size).order(ByteOrder.BIG_ENDIAN).putInt(0).put(ascii(type)).put(payload).array()
    private fun ascii(value: String): ByteArray = value.toByteArray(Charsets.US_ASCII)
    private fun concat(vararg values: ByteArray): ByteArray {
        val output = ByteArray(values.sumOf(ByteArray::size))
        var offset = 0
        values.forEach { value -> value.copyInto(output, destinationOffset = offset); offset += value.size }
        return output
    }
}
