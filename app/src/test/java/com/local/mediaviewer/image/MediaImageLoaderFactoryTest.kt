package com.local.mediaviewer.image

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.request.CachePolicy
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `图片请求限制解码尺寸并隔离重试代次`() =
        runTest {
            val context =
                ApplicationProvider
                    .getApplicationContext<Context>()
            val request =
                MediaImageLoaderFactory.createRequest(
                    context = context,
                    url =
                        "http://192.0.2.1/pik/a.png",
                    decodeSize =
                        ImageDecodeSize(1080, 4096),
                    requestGeneration = 3,
                )

            assertEquals(
                Size(1080, 4096),
                request.sizeResolver.size(),
            )
            assertEquals(Scale.FIT, request.scale)
            assertEquals(
                Precision.INEXACT,
                request.precision,
            )
            assertTrue(
                requireNotNull(request.memoryCacheKey)
                    .contains("generation=3"),
            )
        }
}
