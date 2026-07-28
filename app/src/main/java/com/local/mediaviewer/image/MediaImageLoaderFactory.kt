package com.local.mediaviewer.image

import android.content.Context
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
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
    ): ImageRequest = createRequest(
        context = context,
        url = url,
        decodeSize = ImageDecodeSize(
            widthPx = 2_048,
            heightPx = 2_048,
        ),
        requestGeneration = 0,
    )

    fun createRequest(
        context: Context,
        url: String,
        decodeSize: ImageDecodeSize,
        requestGeneration: Int,
    ): ImageRequest =
        ImageRequest.Builder(context.applicationContext)
            .data(url)
            .size(
                decodeSize.widthPx,
                decodeSize.heightPx,
            )
            .scale(Scale.FIT)
            .precision(Precision.INEXACT)
            .memoryCacheKey(
                "$url|${decodeSize.widthPx}x" +
                    "${decodeSize.heightPx}|" +
                    "generation=$requestGeneration",
            )
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
}
