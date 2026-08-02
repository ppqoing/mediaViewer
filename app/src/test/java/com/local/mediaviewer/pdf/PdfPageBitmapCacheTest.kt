package com.local.mediaviewer.pdf

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PdfPageBitmapCacheTest {
    @Test
    fun put_evictsLeastRecentlyUsedBitmapAfterByteLimit() {
        val first = bitmap()
        val second = bitmap()
        val third = bitmap()
        val evicted = mutableListOf<Pair<Int, Bitmap>>()
        val cache = PdfPageBitmapCache(
            maxBytes = first.byteCount + second.byteCount,
            onEvicted = { pageIndex, value -> evicted += pageIndex to value },
        )

        cache.put(0, first)
        cache.put(1, second)
        cache.put(2, third)

        assertNull(cache.get(0))
        assertSame(third, cache.get(2))
        assertEquals(listOf(0 to first), evicted)
        cache.clear()
        assertEquals(0, cache.sizeBytes)
    }

    @Test
    fun put_replacesPageAndNotifiesBeforeCacheRecyclesNothing() {
        val first = bitmap()
        val replacement = bitmap()
        val evicted = mutableListOf<Pair<Int, Bitmap>>()
        val cache = PdfPageBitmapCache(
            maxBytes = first.byteCount,
            onEvicted = { pageIndex, value -> evicted += pageIndex to value },
        )

        cache.put(0, first)
        cache.put(0, replacement)
        cache.put(0, replacement)

        assertSame(replacement, cache.get(0))
        assertEquals(listOf(0 to first), evicted)
        assertFalse(first.isRecycled)
        assertFalse(replacement.isRecycled)
        assertEquals(replacement.allocationByteCount.toLong(), cache.sizeBytes)
    }

    @Test
    fun put_evictsBitmapThatExceedsMaxBytes() {
        val oversized = bitmap()
        val evicted = mutableListOf<Pair<Int, Bitmap>>()
        val cache = PdfPageBitmapCache(
            maxBytes = oversized.allocationByteCount - 1,
            onEvicted = { pageIndex, value -> evicted += pageIndex to value },
        )

        cache.put(7, oversized)

        assertNull(cache.get(7))
        assertEquals(0, cache.sizeBytes)
        assertEquals(listOf(7 to oversized), evicted)
        assertTrue(!oversized.isRecycled)
    }

    private fun bitmap(): Bitmap =
        Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
}
