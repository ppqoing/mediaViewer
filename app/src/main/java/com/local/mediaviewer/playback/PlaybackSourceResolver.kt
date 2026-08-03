package com.local.mediaviewer.playback

import com.local.mediaviewer.core.DefaultDispatcherProvider
import com.local.mediaviewer.core.DispatcherProvider
import java.io.IOException
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.Buffer

enum class PlaybackDemuxStrategy {
    DEFAULT,
    AVFORMAT,
}

data class PlaybackSource(
    val url: String,
    val demuxStrategy: PlaybackDemuxStrategy = PlaybackDemuxStrategy.DEFAULT,
)

fun interface PlaybackSourceResolver {
    suspend fun resolve(url: String): PlaybackSource
}

internal object PassthroughPlaybackSourceResolver : PlaybackSourceResolver {
    override suspend fun resolve(url: String): PlaybackSource = PlaybackSource(url)
}

internal class DefaultPlaybackSourceResolver(
    private val callFactory: Call.Factory = OkHttpClient.Builder()
        .callTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build(),
    private val detector: FragmentedMp4Detector = IsoBmffFragmentDetector,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) : PlaybackSourceResolver {
    private val cache = object : LinkedHashMap<String, PlaybackDemuxStrategy>(
        MAX_CACHE_ENTRIES,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, PlaybackDemuxStrategy>,
        ): Boolean = size > MAX_CACHE_ENTRIES
    }

    override suspend fun resolve(url: String): PlaybackSource {
        if (!isHttpMp4(url)) return PlaybackSource(url)
        synchronized(cache) { cache[url] }?.let { strategy ->
            return PlaybackSource(url, strategy)
        }
        val detection = probe(url) ?: return PlaybackSource(url)
        if (detection == FragmentedMp4Detection.MALFORMED) return PlaybackSource(url)
        val strategy = if (detection == FragmentedMp4Detection.FRAGMENTED) {
            PlaybackDemuxStrategy.AVFORMAT
        } else {
            PlaybackDemuxStrategy.DEFAULT
        }
        synchronized(cache) { cache[url] = strategy }
        return PlaybackSource(url, strategy)
    }

    private fun isHttpMp4(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        return parsed.encodedPath.substringAfterLast('/').endsWith(
            suffix = ".mp4",
            ignoreCase = true,
        )
    }

    private suspend fun probe(url: String): FragmentedMp4Detection? =
        withContext(dispatchers.io) {
            val call = try {
                callFactory.newCall(
                    Request.Builder()
                        .url(url)
                        .header("Range", RANGE_HEADER)
                        .get()
                        .build(),
                )
            } catch (_: IllegalArgumentException) {
                return@withContext null
            }
            executeCancellable(call)
        }

    private suspend fun executeCancellable(call: Call): FragmentedMp4Detection? =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            val result = try {
                call.execute().use { response ->
                    if (response.code != 200 && response.code != 206) {
                        null
                    } else {
                        val source = response.body.source()
                        val prefix = Buffer()
                        while (prefix.size < MAX_PREFIX_BYTES) {
                            val read = source.read(prefix, MAX_PREFIX_BYTES - prefix.size)
                            if (read == -1L) break
                        }
                        detector.detect(prefix.readByteArray())
                    }
                }
            } catch (_: IOException) {
                null
            }
            if (continuation.isActive) continuation.resume(result)
        }

    private companion object {
        const val RANGE_HEADER = "bytes=0-65535"
        const val MAX_PREFIX_BYTES = 64L * 1024L
        const val MAX_CACHE_ENTRIES = 128
        const val PROBE_TIMEOUT_SECONDS = 2L
    }
}
