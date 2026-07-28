package com.local.mediaviewer.image

import android.content.Context
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import okhttp3.OkHttpClient

object MediaImageLoaderFactory {
    fun create(context: Context): ImageLoader {
        val appContext = context.applicationContext
        val callFactory = OkHttpClient.Builder()
            .cache(null)
            .build()

        return ImageLoader.Builder(appContext)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(appContext, 0.20)
                    .build()
            }
            .diskCache(null)
            .diskCachePolicy(CachePolicy.DISABLED)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { callFactory },
                    ),
                )
            }
            .build()
    }

    fun createRequest(
        context: Context,
        url: String,
    ): ImageRequest =
        ImageRequest.Builder(context.applicationContext)
            .data(url)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
}
