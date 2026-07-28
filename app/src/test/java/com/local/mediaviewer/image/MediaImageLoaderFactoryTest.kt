package com.local.mediaviewer.image

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.request.CachePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class MediaImageLoaderFactoryTest {
    @Test
    fun `ImageLoader 有内存缓存且明确禁用磁盘缓存`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = MediaImageLoaderFactory.create(context)

        assertNull(loader.diskCache)
        assertEquals(
            CachePolicy.DISABLED,
            loader.defaults.diskCachePolicy,
        )
        requireNotNull(loader.memoryCache)

        val request = MediaImageLoaderFactory.createRequest(
            context,
            "http://192.0.2.1/pik/poster.png",
        )
        assertEquals(
            CachePolicy.ENABLED,
            request.memoryCachePolicy,
        )
        assertEquals(
            CachePolicy.DISABLED,
            request.diskCachePolicy,
        )
        assertEquals(
            CachePolicy.ENABLED,
            request.networkCachePolicy,
        )

        loader.shutdown()
    }
}
