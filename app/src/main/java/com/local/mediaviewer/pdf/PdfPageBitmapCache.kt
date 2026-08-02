package com.local.mediaviewer.pdf

import android.graphics.Bitmap
import java.util.LinkedHashMap

class PdfPageBitmapCache(
    private val maxBytes: Int,
    private val onEvicted: (pageIndex: Int, bitmap: Bitmap) -> Unit,
) {
    private val entries = LinkedHashMap<Int, Bitmap>(0, 0.75f, true)
    private var currentBytes = 0L

    val sizeBytes: Long
        get() = currentBytes

    init {
        require(maxBytes >= 0) { "maxBytes must be non-negative" }
    }

    fun get(pageIndex: Int): Bitmap? = entries[pageIndex]

    fun put(pageIndex: Int, bitmap: Bitmap) {
        val existing = entries[pageIndex]
        if (existing === bitmap) return

        if (existing != null) {
            remove(pageIndex, existing)
        }
        entries[pageIndex] = bitmap
        currentBytes += bitmap.allocationByteCount.toLong()
        trimToSize()
    }

    fun clear() {
        while (entries.isNotEmpty()) {
            val entry = entries.entries.iterator().next()
            remove(entry.key, entry.value)
        }
    }

    private fun trimToSize() {
        while (currentBytes > maxBytes.toLong() && entries.isNotEmpty()) {
            val entry = entries.entries.iterator().next()
            remove(entry.key, entry.value)
        }
    }

    private fun remove(pageIndex: Int, bitmap: Bitmap) {
        val bytes = bitmap.allocationByteCount.toLong()
        onEvicted(pageIndex, bitmap)
        entries.remove(pageIndex)
        currentBytes = (currentBytes - bytes).coerceAtLeast(0)
    }
}
