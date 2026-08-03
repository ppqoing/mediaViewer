package com.local.mediaviewer.playback

import com.local.mediaviewer.core.DispatcherProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackSourceResolverTest {
    private lateinit var server: MockWebServer
    private val directDispatchers = object : DispatcherProvider {
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stopServer() = server.close()

    @Test
    fun `fragmented mp4 sends bounded range and selects avformat`() = runTest {
        server.enqueue(MockResponse.Builder().code(206).body(Buffer().write(ftypDash())).build())
        val resolver = resolver()
        val url = server.url("/pik/%E5%A7%9D%E5%A7%AC/movie%2050v.mp4?token=a").toString()

        val source = resolver.resolve(url)

        assertEquals(PlaybackDemuxStrategy.AVFORMAT, source.demuxStrategy)
        assertEquals(url, source.url)
        assertEquals("bytes=0-65535", server.takeRequest().headers["Range"])
    }

    @Test
    fun `server ignoring range is read at most 64 KiB`() = runTest {
        var observedBytes = -1
        val detector = FragmentedMp4Detector { bytes ->
            observedBytes = bytes.size
            FragmentedMp4Detection.STANDARD
        }
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(ByteArray(128 * 1024))).build())

        val source = resolver(detector = detector).resolve(server.url("/middle/large.mp4").toString())

        assertEquals(64 * 1024, observedBytes)
        assertEquals(PlaybackDemuxStrategy.DEFAULT, source.demuxStrategy)
    }

    @Test
    fun `successful result is cached by exact request url`() = runTest {
        server.enqueue(MockResponse.Builder().code(206).body(Buffer().write(ftypDash())).build())
        val resolver = resolver()
        val url = server.url("/middle/cached.mp4").toString()

        assertEquals(PlaybackDemuxStrategy.AVFORMAT, resolver.resolve(url).demuxStrategy)
        assertEquals(PlaybackDemuxStrategy.AVFORMAT, resolver.resolve(url).demuxStrategy)

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `failed result is not cached and next probe can succeed`() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(MockResponse.Builder().code(206).body(Buffer().write(ftypDash())).build())
        val resolver = resolver()
        val url = server.url("/middle/retry.mp4").toString()

        assertEquals(PlaybackDemuxStrategy.DEFAULT, resolver.resolve(url).demuxStrategy)
        assertEquals(PlaybackDemuxStrategy.AVFORMAT, resolver.resolve(url).demuxStrategy)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `malformed structure is not cached and next probe can succeed`() = runTest {
        server.enqueue(MockResponse.Builder().code(206).body(Buffer().write(byteArrayOf(0, 0, 0, 4, 0, 0, 0, 0))).build())
        server.enqueue(MockResponse.Builder().code(206).body(Buffer().write(ftypDash())).build())
        val resolver = resolver()
        val url = server.url("/middle/malformed.mp4").toString()

        assertEquals(PlaybackDemuxStrategy.DEFAULT, resolver.resolve(url).demuxStrategy)
        assertEquals(PlaybackDemuxStrategy.AVFORMAT, resolver.resolve(url).demuxStrategy)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `cache evicts eldest entry after 128 urls`() = runTest {
        repeat(130) { server.enqueue(MockResponse.Builder().code(206).body(Buffer().write(ftypDash())).build()) }
        val resolver = resolver()
        val urls = (0..128).map { index -> server.url("/middle/$index.mp4").toString() }

        urls.forEach { resolver.resolve(it) }
        resolver.resolve(urls.first())

        assertEquals(130, server.requestCount)
    }

    @Test
    fun `non http or non mp4 urls bypass the network`() = runTest {
        val resolver = resolver()
        val inputs = listOf("file:///sdcard/movie.mp4", server.url("/middle/movie.mkv").toString(), server.url("/middle/movie.mp4.txt").toString())

        inputs.forEach { input -> assertEquals(PlaybackSource(input), resolver.resolve(input)) }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `cancellation cancels a stalled probe`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(ByteArray(128 * 1024))).bodyDelay(30, TimeUnit.SECONDS).build())
        val job = backgroundScope.async(Dispatchers.IO) { resolver().resolve(server.url("/middle/stalled.mp4").toString()) }
        assertTrue(server.takeRequest(2, TimeUnit.SECONDS) != null)

        job.cancelAndJoin()

        assertTrue(job.isCancelled)
    }

    @Test
    fun `call timeout falls back to default without caching failure`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(ftypDash())).bodyDelay(1, TimeUnit.SECONDS).build())
        server.enqueue(MockResponse.Builder().code(206).body(Buffer().write(ftypDash())).build())
        val resolver = resolver(callTimeoutMillis = 50)
        val url = server.url("/middle/timeout.mp4").toString()

        assertEquals(PlaybackDemuxStrategy.DEFAULT, resolver.resolve(url).demuxStrategy)
        assertEquals(PlaybackDemuxStrategy.AVFORMAT, resolver.resolve(url).demuxStrategy)
        assertEquals(2, server.requestCount)
    }

    private fun resolver(detector: FragmentedMp4Detector = IsoBmffFragmentDetector, callTimeoutMillis: Long = 2_000) =
        DefaultPlaybackSourceResolver(
            callFactory = OkHttpClient.Builder().callTimeout(callTimeoutMillis, TimeUnit.MILLISECONDS).build(),
            detector = detector,
            dispatchers = directDispatchers,
        )

    private fun ftypDash(): ByteArray = byteArrayOf(
        0, 0, 0, 24,
        'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
        'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
        0, 0, 0, 0,
        'd'.code.toByte(), 'a'.code.toByte(), 's'.code.toByte(), 'h'.code.toByte(),
        'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), '6'.code.toByte(),
    )
}
