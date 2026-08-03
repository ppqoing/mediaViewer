package com.local.mediaviewer.playback

internal enum class FragmentedMp4Detection {
    FRAGMENTED,
    STANDARD,
    MALFORMED,
}

internal fun interface FragmentedMp4Detector {
    fun detect(prefix: ByteArray): FragmentedMp4Detection
}

internal object IsoBmffFragmentDetector : FragmentedMp4Detector {
    private val fragmentedBrands = setOf("dash", "msdh", "msix", "dsms")

    override fun detect(prefix: ByteArray): FragmentedMp4Detection {
        if (prefix.size < BOX_HEADER_BYTES) return FragmentedMp4Detection.MALFORMED
        return scan(prefix, start = 0, end = prefix.size, topLevel = true)
    }

    private fun scan(
        bytes: ByteArray,
        start: Int,
        end: Int,
        topLevel: Boolean,
    ): FragmentedMp4Detection {
        var offset = start
        while (offset < end) {
            if (end - offset < BOX_HEADER_BYTES) return FragmentedMp4Detection.MALFORMED
            val size32 = readUInt32(bytes, offset)
            val headerBytes = if (size32 == EXTENDED_SIZE_MARKER) 16 else 8
            if (end - offset < headerBytes) return FragmentedMp4Detection.MALFORMED
            val boxSize = when (size32) {
                0L -> (end - offset).toLong()
                EXTENDED_SIZE_MARKER -> readUInt64(bytes, offset + 8)
                    ?: return FragmentedMp4Detection.MALFORMED
                else -> size32
            }
            if (boxSize < headerBytes || boxSize > end - offset) return FragmentedMp4Detection.MALFORMED

            val boxEnd = offset + boxSize.toInt()
            val type = ascii4(bytes, offset + 4)
            val payloadStart = offset + headerBytes
            when {
                type == "ftyp" -> {
                    val brands = readBrands(bytes, payloadStart, boxEnd)
                        ?: return FragmentedMp4Detection.MALFORMED
                    if (brands.any(fragmentedBrands::contains)) return FragmentedMp4Detection.FRAGMENTED
                }
                topLevel && type == "moof" -> return FragmentedMp4Detection.FRAGMENTED
                topLevel && type == "moov" -> {
                    val nested = scan(bytes, payloadStart, boxEnd, topLevel = false)
                    if (nested != FragmentedMp4Detection.STANDARD) return nested
                }
                !topLevel && type == "mvex" -> return FragmentedMp4Detection.FRAGMENTED
            }
            offset = boxEnd
        }
        return FragmentedMp4Detection.STANDARD
    }

    private fun readBrands(bytes: ByteArray, start: Int, end: Int): List<String>? {
        if (end - start < FTYP_FIXED_PAYLOAD_BYTES) return null
        if ((end - start - FTYP_FIXED_PAYLOAD_BYTES) % BRAND_BYTES != 0) return null
        return buildList {
            add(ascii4(bytes, start))
            var offset = start + FTYP_FIXED_PAYLOAD_BYTES
            while (offset < end) {
                add(ascii4(bytes, offset))
                offset += BRAND_BYTES
            }
        }
    }

    private fun readUInt32(bytes: ByteArray, offset: Int): Long =
        (0 until 4).fold(0L) { value, index ->
            (value shl 8) or (bytes[offset + index].toLong() and 0xffL)
        }

    private fun readUInt64(bytes: ByteArray, offset: Int): Long? {
        if ((bytes[offset].toInt() and 0x80) != 0) return null
        return (0 until 8).fold(0L) { value, index ->
            (value shl 8) or (bytes[offset + index].toLong() and 0xffL)
        }
    }

    private fun ascii4(bytes: ByteArray, offset: Int): String =
        bytes.copyOfRange(offset, offset + BRAND_BYTES).toString(Charsets.US_ASCII)

    private const val BOX_HEADER_BYTES = 8
    private const val FTYP_FIXED_PAYLOAD_BYTES = 8
    private const val BRAND_BYTES = 4
    private const val EXTENDED_SIZE_MARKER = 1L
}
