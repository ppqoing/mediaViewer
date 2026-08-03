# 分片 MP4 播放进度兼容 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不修改服务器和媒体文件的前提下，仅对可靠识别出的 HTTP fMP4 启用 LibVLC `avformat` 解复用器，使用户样本的时间数字、进度条、画面和声音保持同步。

**Architecture:** 在现有端点 URL 与 LibVLC 引擎之间增加一个可注入的播放源解析器。解析器通过最多 64 KiB 的 Range 请求和边界安全的 ISO BMFF 检测器选择 `DEFAULT` 或 `AVFORMAT`，`PlaybackCoordinator` 负责串行解析，`AndroidVlcPlaybackEngine` 只负责把策略翻译成 LibVLC Media 选项。

**Tech Stack:** Kotlin 2.3.21、Android SDK 36、Kotlin Coroutines 1.11.0、OkHttp/MockWebServer 5.3.0、LibVLC 4.0.0-eap29、JUnit 4、Robolectric、Gradle Android Plugin 9.3.0。

## Global Constraints

- 仅修改 Android 应用端；不修改 Caddy、HTTP Range 服务、服务器映射或媒体源文件。
- 只探测 HTTP/HTTPS 且路径扩展名为 `.mp4` 的地址，请求头固定为 `Range: bytes=0-65535`。
- 单次响应最多读取 64 KiB，默认客户端的单次探测总超时不超过两秒。
- 只有识别到 `dash`、`msdh`、`msix`、`dsms`、`moov/mvex` 或顶层 `moof` 时才选择 `AVFORMAT`；不得对所有 MP4 全局强制 `avformat`。
- 成功判定使用最多 128 条的进程内 LRU 缓存；网络失败、取消和结构损坏不写缓存。
- `CancellationException` 必须继续传播，探测失败则返回 `DEFAULT` 并允许原路径播放。
- 不升级或降级 LibVLC、Media3、OkHttp 及其他依赖。
- 不修改 MediaSession 原始位置快照、UI 轮询周期、进度条计算、播放队列、后台播放、通知控制或恢复位置持久化。
- 不重新拼接最终请求 URL，不记录完整媒体 URL。
- 保留工作树中已有的用户改动；每次提交只暂存任务明确列出的文件，并在提交前核对暂存文件清单。
- 自动测试、模拟器验证和用户 LAN 样本验证必须分别报告；无法运行的动态项标记为 `NOT RUN` 或 `BLOCKED`。

---

## 文件结构与职责

- 新建 `app/src/main/java/com/local/mediaviewer/playback/IsoBmffFragmentDetector.kt`：解析受限前缀内的 ISO BMFF Box，返回 `FRAGMENTED`、`STANDARD` 或 `MALFORMED`。
- 新建 `app/src/main/java/com/local/mediaviewer/playback/PlaybackSourceResolver.kt`：定义播放源、解复用策略、解析器接口、默认 Range 探测实现和 128 条 LRU 缓存。
- 修改 `app/src/main/java/com/local/mediaviewer/playback/PlaybackEngine.kt`：增加接受 `PlaybackSource` 的兼容重载，保留现有字符串重载供测试替身和既有调用方使用。
- 新建 `app/src/main/java/com/local/mediaviewer/playback/VlcMediaOptions.kt`：把解复用策略纯函数映射为 LibVLC Media 选项。
- 修改 `app/src/main/java/com/local/mediaviewer/playback/AndroidVlcPlaybackEngine.kt`：读取 `PlaybackSource` 并在必要时添加 `:demux=avformat`。
- 修改 `app/src/main/java/com/local/mediaviewer/queue/PlaybackCoordinator.kt`：让直接准备、队列装载和端点恢复共用同一 suspend 播放源解析路径。
- 修改 `app/src/main/java/com/local/mediaviewer/app/AppContainer.kt`：在应用进程内共享默认解析器及其缓存，并注入新建的协调器。
- 新建 `app/src/test/java/com/local/mediaviewer/playback/IsoBmffFragmentDetectorTest.kt`：覆盖品牌、`mvex`、`moof`、合法长度和畸形 Box。
- 新建 `app/src/test/java/com/local/mediaviewer/playback/PlaybackSourceResolverTest.kt`：覆盖 Range、读取上限、缓存、失败回退、取消和 URL 条件。
- 新建 `app/src/test/java/com/local/mediaviewer/playback/VlcMediaOptionsTest.kt`：证明 `avformat` 选项只来自 `AVFORMAT`。
- 修改 `app/src/test/java/com/local/mediaviewer/queue/PlaybackCoordinatorTest.kt`：验证策略路由、串行顺序、直接准备和端点恢复。
- 修改 `app/src/androidTest/java/com/local/mediaviewer/LibVlcEngineCreationTest.kt`：对默认和 `avformat` 两种播放源执行真实 LibVLC Media 创建冒烟测试。
- 新建 `docs/verification/2026-08-03-fragmented-mp4-progress.md`：记录自动门禁、模拟器、用户 LAN 样本及对照文件的真实结果。

### Task 1: 边界安全的 ISO BMFF 分片检测器

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/playback/IsoBmffFragmentDetector.kt`
- Test: `app/src/test/java/com/local/mediaviewer/playback/IsoBmffFragmentDetectorTest.kt`

**Interfaces:**
- Consumes: 最多 65,536 字节的媒体文件前缀。
- Produces: `FragmentedMp4Detector.detect(prefix: ByteArray): FragmentedMp4Detection`；结果枚举为 `FRAGMENTED`、`STANDARD`、`MALFORMED`。

- [ ] **Step 1: 写入品牌与 Box 结构的失败测试**

```kotlin
package com.local.mediaviewer.playback

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class IsoBmffFragmentDetectorTest {
    @Test
    fun `dash compatible brand is fragmented`() {
        val input = ftyp("isom", "iso6", "dash")
        assertEquals(
            FragmentedMp4Detection.FRAGMENTED,
            IsoBmffFragmentDetector.detect(input),
        )
    }

    @Test
    fun `all supported fragmented brands are detected`() {
        listOf("msdh", "msix", "dsms").forEach { brand ->
            assertEquals(
                brand,
                FragmentedMp4Detection.FRAGMENTED,
                IsoBmffFragmentDetector.detect(ftyp(brand)),
            )
        }
    }

    @Test
    fun `mvex nested in moov is fragmented`() {
        val input = concat(
            ftyp("isom", "iso6"),
            box("moov", box("mvex")),
        )
        assertEquals(
            FragmentedMp4Detection.FRAGMENTED,
            IsoBmffFragmentDetector.detect(input),
        )
    }

    @Test
    fun `top level moof is fragmented`() {
        val input = concat(ftyp("isom", "iso6"), box("moof"))
        assertEquals(
            FragmentedMp4Detection.FRAGMENTED,
            IsoBmffFragmentDetector.detect(input),
        )
    }

    @Test
    fun `flat isom mp4 is standard`() {
        val input = concat(
            ftyp("isom", "iso6", "avc1"),
            box("moov", box("trak")),
            zeroSizedBox("mdat", byteArrayOf(1, 2, 3, 4)),
        )
        assertEquals(
            FragmentedMp4Detection.STANDARD,
            IsoBmffFragmentDetector.detect(input),
        )
    }

    @Test
    fun `extended size box is parsed`() {
        val input = concat(ftyp("isom"), extendedBox("moof"))
        assertEquals(
            FragmentedMp4Detection.FRAGMENTED,
            IsoBmffFragmentDetector.detect(input),
        )
    }

    @Test
    fun `short header and impossible sizes are malformed`() {
        val shortHeader = byteArrayOf(0, 0, 0, 8, 'f'.code.toByte())
        val smallerThanHeader = ByteBuffer.allocate(8)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(4)
            .put(ascii("free"))
            .array()
        val overflowingExtendedSize = ByteBuffer.allocate(16)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(1)
            .put(ascii("free"))
            .putLong(-1L)
            .array()

        listOf(shortHeader, smallerThanHeader, overflowingExtendedSize).forEach { input ->
            assertEquals(
                FragmentedMp4Detection.MALFORMED,
                IsoBmffFragmentDetector.detect(input),
            )
        }
    }

    private fun ftyp(major: String, vararg compatible: String): ByteArray =
        box(
            "ftyp",
            concat(
                ascii(major),
                byteArrayOf(0, 0, 0, 0),
                *compatible.map(::ascii).toTypedArray(),
            ),
        )

    private fun box(type: String, payload: ByteArray = byteArrayOf()): ByteArray =
        ByteBuffer.allocate(8 + payload.size)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(8 + payload.size)
            .put(ascii(type))
            .put(payload)
            .array()

    private fun extendedBox(type: String, payload: ByteArray = byteArrayOf()): ByteArray =
        ByteBuffer.allocate(16 + payload.size)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(1)
            .put(ascii(type))
            .putLong(16L + payload.size)
            .put(payload)
            .array()

    private fun zeroSizedBox(type: String, payload: ByteArray): ByteArray =
        ByteBuffer.allocate(8 + payload.size)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(0)
            .put(ascii(type))
            .put(payload)
            .array()

    private fun ascii(value: String): ByteArray =
        value.toByteArray(Charsets.US_ASCII)

    private fun concat(vararg values: ByteArray): ByteArray {
        val output = ByteArray(values.sumOf(ByteArray::size))
        var offset = 0
        values.forEach { value ->
            value.copyInto(output, destinationOffset = offset)
            offset += value.size
        }
        return output
    }
}
```

- [ ] **Step 2: 运行测试并确认因类型尚不存在而失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.local.mediaviewer.playback.IsoBmffFragmentDetectorTest"
```

Expected: `FAIL`，编译错误指出 `FragmentedMp4Detection` 或 `IsoBmffFragmentDetector` 未定义。

- [ ] **Step 3: 实现最小的边界安全 Box 解析器**

```kotlin
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
            if (boxSize < headerBytes || boxSize > end - offset) {
                return FragmentedMp4Detection.MALFORMED
            }
            val boxEnd = offset + boxSize.toInt()
            val type = ascii4(bytes, offset + 4)
            val payloadStart = offset + headerBytes
            when {
                type == "ftyp" -> {
                    val brands = readBrands(bytes, payloadStart, boxEnd)
                        ?: return FragmentedMp4Detection.MALFORMED
                    if (brands.any(fragmentedBrands::contains)) {
                        return FragmentedMp4Detection.FRAGMENTED
                    }
                }
                topLevel && type == "moof" ->
                    return FragmentedMp4Detection.FRAGMENTED
                topLevel && type == "moov" -> {
                    val nested = scan(bytes, payloadStart, boxEnd, topLevel = false)
                    if (nested != FragmentedMp4Detection.STANDARD) return nested
                }
                !topLevel && type == "mvex" ->
                    return FragmentedMp4Detection.FRAGMENTED
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
```

- [ ] **Step 4: 运行检测器测试并确认通过**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.local.mediaviewer.playback.IsoBmffFragmentDetectorTest"
```

Expected: `BUILD SUCCESSFUL`，该测试类全部通过。

- [ ] **Step 5: 只提交检测器及其测试**

```powershell
git add -- app/src/main/java/com/local/mediaviewer/playback/IsoBmffFragmentDetector.kt app/src/test/java/com/local/mediaviewer/playback/IsoBmffFragmentDetectorTest.kt
git diff --cached --check
git diff --cached --name-only
git commit -m "feat: detect fragmented MP4 containers"
```

提交前确认暂存清单只有上述两个文件。

### Task 2: 有界、可取消并带缓存的播放源解析器

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/playback/PlaybackSourceResolver.kt`
- Test: `app/src/test/java/com/local/mediaviewer/playback/PlaybackSourceResolverTest.kt`

**Interfaces:**
- Consumes: `FragmentedMp4Detector.detect(prefix: ByteArray)`。
- Produces: `PlaybackSource(url: String, demuxStrategy: PlaybackDemuxStrategy)`、`PlaybackSourceResolver.resolve(url: String)`、`DefaultPlaybackSourceResolver`、`PassthroughPlaybackSourceResolver`。

- [ ] **Step 1: 写入 Range、读取上限和策略选择的失败测试**

```kotlin
package com.local.mediaviewer.playback

import com.local.mediaviewer.core.DispatcherProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import okhttp3.OkHttpClient
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
        server.enqueue(
            MockResponse.Builder()
                .code(206)
                .body(Buffer().write(ftypDash()))
                .build(),
        )
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
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(Buffer().write(ByteArray(128 * 1024)))
                .build(),
        )

        val source = resolver(detector = detector).resolve(
            server.url("/middle/large.mp4").toString(),
        )

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
        server.enqueue(
            MockResponse.Builder()
                .code(206)
                .body(Buffer().write(byteArrayOf(0, 0, 0, 4, 0, 0, 0, 0)))
                .build(),
        )
        server.enqueue(MockResponse.Builder().code(206).body(Buffer().write(ftypDash())).build())
        val resolver = resolver()
        val url = server.url("/middle/malformed.mp4").toString()

        assertEquals(PlaybackDemuxStrategy.DEFAULT, resolver.resolve(url).demuxStrategy)
        assertEquals(PlaybackDemuxStrategy.AVFORMAT, resolver.resolve(url).demuxStrategy)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `cache evicts eldest entry after 128 urls`() = runTest {
        repeat(130) {
            server.enqueue(MockResponse.Builder().code(206).body(Buffer().write(ftypDash())).build())
        }
        val resolver = resolver()
        val urls = (0..128).map { index ->
            server.url("/middle/$index.mp4").toString()
        }

        urls.forEach { resolver.resolve(it) }
        resolver.resolve(urls.first())

        assertEquals(130, server.requestCount)
    }

    @Test
    fun `non http or non mp4 urls bypass the network`() = runTest {
        val resolver = resolver()
        val inputs = listOf(
            "file:///sdcard/movie.mp4",
            server.url("/middle/movie.mkv").toString(),
            server.url("/middle/movie.mp4.txt").toString(),
        )

        inputs.forEach { input ->
            assertEquals(PlaybackSource(input), resolver.resolve(input))
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `cancellation cancels a stalled probe`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(Buffer().write(ByteArray(128 * 1024)))
                .bodyDelay(30, TimeUnit.SECONDS)
                .build(),
        )
        val job = backgroundScope.async(Dispatchers.IO) {
            resolver().resolve(server.url("/middle/stalled.mp4").toString())
        }
        assertTrue(server.takeRequest(2, TimeUnit.SECONDS) != null)

        job.cancelAndJoin()

        assertTrue(job.isCancelled)
    }

    @Test
    fun `call timeout falls back to default without caching failure`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(Buffer().write(ftypDash()))
                .bodyDelay(1, TimeUnit.SECONDS)
                .build(),
        )
        server.enqueue(MockResponse.Builder().code(206).body(Buffer().write(ftypDash())).build())
        val resolver = resolver(callTimeoutMillis = 50)
        val url = server.url("/middle/timeout.mp4").toString()

        assertEquals(PlaybackDemuxStrategy.DEFAULT, resolver.resolve(url).demuxStrategy)
        assertEquals(PlaybackDemuxStrategy.AVFORMAT, resolver.resolve(url).demuxStrategy)
        assertEquals(2, server.requestCount)
    }

    private fun resolver(
        detector: FragmentedMp4Detector = IsoBmffFragmentDetector,
        callTimeoutMillis: Long = 2_000,
    ) = DefaultPlaybackSourceResolver(
        callFactory = OkHttpClient.Builder()
            .callTimeout(callTimeoutMillis, TimeUnit.MILLISECONDS)
            .build(),
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
```

- [ ] **Step 2: 运行解析器测试并确认因接口尚不存在而失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.local.mediaviewer.playback.PlaybackSourceResolverTest"
```

Expected: `FAIL`，编译错误指出 `PlaybackSource`、`PlaybackDemuxStrategy` 或 `DefaultPlaybackSourceResolver` 未定义。

- [ ] **Step 3: 实现播放源模型、Range 探测、取消传播和 LRU 缓存**

```kotlin
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
```

- [ ] **Step 4: 运行解析器与检测器测试并确认通过**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.local.mediaviewer.playback.IsoBmffFragmentDetectorTest" --tests "com.local.mediaviewer.playback.PlaybackSourceResolverTest"
```

Expected: `BUILD SUCCESSFUL`，两个测试类全部通过；取消测试在两秒以内结束。

- [ ] **Step 5: 只提交播放源解析器及测试**

```powershell
git add -- app/src/main/java/com/local/mediaviewer/playback/PlaybackSourceResolver.kt app/src/test/java/com/local/mediaviewer/playback/PlaybackSourceResolverTest.kt
git diff --cached --check
git diff --cached --name-only
git commit -m "feat: resolve demux strategy for HTTP MP4"
```

提交前确认暂存清单只有上述两个文件。

### Task 3: 协调器路由与应用级缓存注入

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/playback/PlaybackEngine.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/queue/PlaybackCoordinator.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/app/AppContainer.kt`
- Test: `app/src/test/java/com/local/mediaviewer/queue/PlaybackCoordinatorTest.kt`

**Interfaces:**
- Consumes: `PlaybackSourceResolver.resolve(url: String): PlaybackSource`。
- Produces: `PlaybackEngine.prepare(source: PlaybackSource)` 兼容重载；所有协调器准备路径向引擎传递完整策略。

- [ ] **Step 1: 为队列装载和直接准备写入失败测试**

在 `PlaybackCoordinatorTest` 增加 `kotlinx.coroutines.CompletableDeferred`、`PlaybackSource`、`PlaybackDemuxStrategy`、`PlaybackSourceResolver` 和 `PassthroughPlaybackSourceResolver` 的 imports；fixture 增加可注入解析器，并让假引擎记录完整播放源：

```kotlin
@Test
fun `queue load passes resolved demux strategy to engine`() = runTest {
    val engine = FakeEngine()
    val resolver = PlaybackSourceResolver { url ->
        PlaybackSource(url, PlaybackDemuxStrategy.AVFORMAT)
    }
    val coordinator = coordinator(
        engine = engine,
        sourceResolver = resolver,
        scope = this,
    )

    coordinator.replaceQueue(listOf(item("a")), "a")
    advanceUntilIdle()

    assertEquals(
        PlaybackSource(requestUrlFor("a"), PlaybackDemuxStrategy.AVFORMAT),
        engine.prepareSources.single(),
    )
    coordinator.close()
}

@Test
fun `direct prepare also uses source resolver`() = runTest {
    val engine = FakeEngine()
    val resolver = PlaybackSourceResolver { url ->
        PlaybackSource(url, PlaybackDemuxStrategy.AVFORMAT)
    }
    val coordinator = coordinator(engine, sourceResolver = resolver, scope = this)

    coordinator.prepare("http://10.0.0.9:8080/direct.mp4")
    advanceUntilIdle()

    assertEquals(PlaybackDemuxStrategy.AVFORMAT, engine.prepareSources.single().demuxStrategy)
    coordinator.close()
}

@Test
fun `endpoint recovery resolves the refreshed request url`() = runTest {
    val resolvedUrls = mutableListOf<String>()
    val resolver = PlaybackSourceResolver { url ->
        resolvedUrls += url
        PlaybackSource(url, PlaybackDemuxStrategy.AVFORMAT)
    }
    val session = FakeSession(
        refreshedEndpoint = SessionEndpoint(
            logicalBaseUrl = "http://media.example:8080",
            requestBaseUrl = "http://10.0.0.10:8080",
            ipv4 = "10.0.0.10",
        ),
    )
    val engine = FakeEngine()
    val coordinator = coordinator(
        engine = engine,
        session = session,
        sourceResolver = resolver,
        scope = this,
    )
    coordinator.replaceQueue(listOf(item("a")), "a")
    advanceUntilIdle()

    engine.emit(PlaybackState(status = PlaybackStatus.ERROR, errorMessage = "old endpoint"))
    advanceUntilIdle()

    assertEquals(
        listOf(
            "http://10.0.0.9:8080/a.mp4",
            "http://10.0.0.10:8080/a.mp4",
        ),
        resolvedUrls,
    )
    assertEquals(
        PlaybackDemuxStrategy.AVFORMAT,
        engine.prepareSources.last().demuxStrategy,
    )
    coordinator.close()
}

@Test
fun `serialized probe cannot apply old media after newer media`() = runTest {
    val firstProbeGate = CompletableDeferred<Unit>()
    val resolver = PlaybackSourceResolver { url ->
        if (url.endsWith("/a.mp4")) firstProbeGate.await()
        PlaybackSource(url)
    }
    val engine = FakeEngine()
    val coordinator = coordinator(engine, sourceResolver = resolver, scope = this)

    coordinator.replaceQueue(listOf(item("a"), item("b")), "a")
    runCurrent()
    coordinator.select("b")
    firstProbeGate.complete(Unit)
    advanceUntilIdle()

    assertEquals(
        listOf(requestUrlFor("a"), requestUrlFor("b")),
        engine.prepareSources.map(PlaybackSource::url),
    )
    assertEquals("b", coordinator.sessionState.value.currentItem?.mediaKey)
    coordinator.close()
}
```

Fixture 和假引擎改为：

```kotlin
private fun coordinator(
    engine: FakeEngine,
    repository: FakeQueueRepository = FakeQueueRepository(),
    positions: FakePositionStore = FakePositionStore(),
    session: FakeSession = FakeSession(),
    sourceResolver: PlaybackSourceResolver = PassthroughPlaybackSourceResolver,
    scope: CoroutineScope,
) = PlaybackCoordinator(
    engine = engine,
    queueRepository = repository,
    positionStore = positions,
    session = session,
    sourceResolver = sourceResolver,
    scope = scope,
)

private class FakeEngine : PlaybackEngine {
    val prepareSources = mutableListOf<PlaybackSource>()

    override fun prepare(source: PlaybackSource) {
        prepareSources += source
        prepare(source.url)
    }
}
```

保留 `FakeEngine` 现有的状态流、字符串 `prepareCalls`、seek、play、stop 和 close 实现，不删除既有断言所需字段。

- [ ] **Step 2: 运行协调器测试并确认因新接口尚未接入而失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.local.mediaviewer.queue.PlaybackCoordinatorTest"
```

Expected: `FAIL`，编译错误指出协调器构造函数没有 `sourceResolver`，或运行断言显示引擎仍收到默认字符串准备调用。

- [ ] **Step 3: 增加兼容的引擎重载并接通全部准备路径**

在 `PlaybackEngine` 中保留现有抽象方法，并增加默认重载，因此其他测试替身无需机械修改：

```kotlin
fun prepare(url: String)

fun prepare(source: PlaybackSource) {
    prepare(source.url)
}
```

在 `PlaybackCoordinator` 构造函数加入：

```kotlin
private val sourceResolver: PlaybackSourceResolver = PassthroughPlaybackSourceResolver,
```

直接准备路径改为：

```kotlin
override fun prepare(url: String) {
    launchMutation {
        loadedMediaKey = null
        engine.prepare(sourceResolver.resolve(url))
    }
}
```

队列 `loadCurrent` 中保持端点、恢复位置、倍速和自动播放顺序，只替换准备调用：

```kotlin
val requestUrl = endpoint.requestUrlFor(item.logicalUrl)
val source = sourceResolver.resolve(requestUrl)
loadedMediaKey = item.mediaKey
engine.prepare(source)
updatePlayback(engine.state.value)
engine.setPlaybackSpeed(queue.playbackSpeed)
if (autoPlay) engine.play()
```

`recoverCurrentEndpointLocked` 已通过 `loadCurrent` 重入，因此不得另建第二条解析路径。

- [ ] **Step 4: 在 AppContainer 中创建进程级解析器并注入协调器**

给 `DefaultAppContainer` 增加可测试的构造参数：

```kotlin
class DefaultAppContainer(
    context: Context,
    private val playbackEngineFactory: PlaybackEngineFactory =
        PlaybackEngineFactory { AndroidVlcPlaybackEngine(context.applicationContext) },
    private val playbackSourceResolver: PlaybackSourceResolver =
        DefaultPlaybackSourceResolver(),
) : AppContainer
```

创建协调器时注入同一实例：

```kotlin
PlaybackCoordinator(
    engine = engine,
    queueRepository = playbackQueueRepository,
    positionStore = playbackPositionStore,
    session = sessionManager,
    sourceResolver = playbackSourceResolver,
    scope = scope,
).start()
```

同一 `DefaultAppContainer` 的服务重建继续复用解析器缓存；不得把缓存放入数据库或 DataStore。

- [ ] **Step 5: 运行协调器和服务层回归测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.local.mediaviewer.queue.PlaybackCoordinatorTest" --tests "com.local.mediaviewer.service.VlcSessionPlayerTest" --tests "com.local.mediaviewer.service.PlaybackSessionCallbackTest"
```

Expected: `BUILD SUCCESSFUL`；既有端点恢复、队列切换、恢复位置和 MediaSession 行为全部通过。

- [ ] **Step 6: 只提交协调器、容器和对应测试**

```powershell
git add -- app/src/main/java/com/local/mediaviewer/playback/PlaybackEngine.kt app/src/main/java/com/local/mediaviewer/queue/PlaybackCoordinator.kt app/src/main/java/com/local/mediaviewer/app/AppContainer.kt app/src/test/java/com/local/mediaviewer/queue/PlaybackCoordinatorTest.kt
git diff --cached --check
git diff --cached --name-only
git commit -m "feat: route fragmented MP4 demux strategy"
```

提交前确认暂存清单只有上述四个文件。

### Task 4: 将策略安全映射为 LibVLC Media 选项

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/playback/VlcMediaOptions.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/playback/AndroidVlcPlaybackEngine.kt`
- Test: `app/src/test/java/com/local/mediaviewer/playback/VlcMediaOptionsTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/LibVlcEngineCreationTest.kt`

**Interfaces:**
- Consumes: `PlaybackSource.demuxStrategy`。
- Produces: `VlcMediaOptions.forSource(source: PlaybackSource): List<String>`；`AndroidVlcPlaybackEngine.prepare(source: PlaybackSource)`。

- [ ] **Step 1: 写入精确的选项映射失败测试**

```kotlin
package com.local.mediaviewer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VlcMediaOptionsTest {
    @Test
    fun `avformat strategy adds only avformat demux option`() {
        val options = VlcMediaOptions.forSource(
            PlaybackSource(
                url = "http://media.example/movie.mp4",
                demuxStrategy = PlaybackDemuxStrategy.AVFORMAT,
            ),
        )

        assertEquals(listOf(":demux=avformat"), options)
    }

    @Test
    fun `default strategy never forces avformat`() {
        val options = VlcMediaOptions.forSource(
            PlaybackSource("http://media.example/flat.mp4"),
        )

        assertEquals(emptyList<String>(), options)
        assertFalse(options.contains(":demux=avformat"))
    }
}
```

- [ ] **Step 2: 运行映射测试并确认因策略映射尚不存在而失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.local.mediaviewer.playback.VlcMediaOptionsTest"
```

Expected: `FAIL`，编译错误指出 `VlcMediaOptions` 未定义。

- [ ] **Step 3: 实现最小的纯函数映射并在引擎设置媒体前使用**

新文件内容：

```kotlin
package com.local.mediaviewer.playback

internal object VlcMediaOptions {
    fun forSource(source: PlaybackSource): List<String> =
        when (source.demuxStrategy) {
            PlaybackDemuxStrategy.DEFAULT -> emptyList()
            PlaybackDemuxStrategy.AVFORMAT -> listOf(":demux=avformat")
        }
}
```

`AndroidVlcPlaybackEngine` 保留字符串入口并委托到播放源入口：

```kotlin
override fun prepare(url: String) {
    prepare(PlaybackSource(url))
}

override fun prepare(source: PlaybackSource) {
    check(!closed.get()) { "PlaybackEngine is closed" }
    mutableState.value = PlaybackState(
        status = PlaybackStatus.OPENING,
        playbackSpeed = mutableState.value.playbackSpeed,
    )
    val media = Media(libVlc, Uri.parse(source.url))
    val decoderConfiguration = VlcVideoDecoderPolicy.compatibility
    media.setHWDecoderEnabled(
        decoderConfiguration.hardwareDecodingEnabled,
        decoderConfiguration.forceHardwareDecoding,
    )
    decoderConfiguration.mediaOptions.forEach(media::addOption)
    VlcMediaOptions.forSource(source).forEach(media::addOption)
    media.addOption(":network-caching=1500")
    mediaPlayer.media = media
    media.release()
}
```

不得把 `:demux=avformat` 放入 `LibVLC` 全局参数或 `VlcVideoDecoderPolicy`。

- [ ] **Step 4: 扩展真实 LibVLC Media 创建冒烟测试**

在 `LibVlcEngineCreationTest` 增加以下 imports：

```kotlin
import com.local.mediaviewer.playback.PlaybackDemuxStrategy
import com.local.mediaviewer.playback.PlaybackSource
```

在现有 `createPrepareAndCloseNativeEngine` 中，默认准备后再加入一次 `AVFORMAT` 准备：

```kotlin
engine.prepare("http://127.0.0.1:8080/middle/flat.mp4")
assertEquals(PlaybackStatus.OPENING, engine.state.value.status)
engine.prepare(
    PlaybackSource(
        url = "http://127.0.0.1:8080/middle/fragmented.mp4",
        demuxStrategy = PlaybackDemuxStrategy.AVFORMAT,
    ),
)
assertEquals(PlaybackStatus.OPENING, engine.state.value.status)
```

保留现有重复 close、关闭后 prepare 抛异常等断言。

- [ ] **Step 5: 运行 JVM 映射测试并编译 Android 测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.local.mediaviewer.playback.VlcMediaOptionsTest" :app:compileDebugAndroidTestKotlin
```

Expected: `BUILD SUCCESSFUL`；映射测试通过，真实 LibVLC 冒烟测试源集成功编译。

- [ ] **Step 6: 只提交 LibVLC 选项适配与测试**

```powershell
git add -- app/src/main/java/com/local/mediaviewer/playback/VlcMediaOptions.kt app/src/main/java/com/local/mediaviewer/playback/AndroidVlcPlaybackEngine.kt app/src/test/java/com/local/mediaviewer/playback/VlcMediaOptionsTest.kt app/src/androidTest/java/com/local/mediaviewer/LibVlcEngineCreationTest.kt
git diff --cached --check
git diff --cached --name-only
git commit -m "fix: use avformat for fragmented MP4 playback"
```

提交前确认暂存清单只有上述四个文件。

### Task 5: 全量门禁与真实播放验收

**Files:**
- Create: `docs/verification/2026-08-03-fragmented-mp4-progress.md`

**Interfaces:**
- Consumes: Tasks 1–4 的实现、测试和 Debug APK。
- Produces: 可审计的自动测试、模拟器、用户 LAN 样本及对照样本验收记录。

- [ ] **Step 1: 运行所有本缺陷相关的聚焦 JVM 测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.local.mediaviewer.playback.IsoBmffFragmentDetectorTest" --tests "com.local.mediaviewer.playback.PlaybackSourceResolverTest" --tests "com.local.mediaviewer.playback.VlcMediaOptionsTest" --tests "com.local.mediaviewer.queue.PlaybackCoordinatorTest" --tests "com.local.mediaviewer.service.VlcSessionPlayerTest" --tests "com.local.mediaviewer.service.PlaybackSessionCallbackTest"
```

Expected: `BUILD SUCCESSFUL`，所有指定测试通过。

- [ ] **Step 2: 运行完整静态门禁并生成 Debug APK**

Run each command separately and preserve its exit code:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

Expected: 四条命令均为 `BUILD SUCCESSFUL`；APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

- [ ] **Step 3: 在可用模拟器或真机运行 LibVLC 冒烟测试**

Run:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am instrument -w -e class com.local.mediaviewer.LibVlcEngineCreationTest com.local.mediaviewer.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: 安装成功，instrumentation 输出 `OK`，默认和 `AVFORMAT` Media 都能创建并关闭。

若没有可用设备，记录 `NOT RUN`，不得用 `compileDebugAndroidTestKotlin` 替代运行结果。

- [ ] **Step 4: 复测用户 LAN fMP4 与普通 MP4 对照文件**

使用应用设置页连接 `http://192.168.1.17:8081`，依次完成以下操作并记录时间数字、墙钟经过时间和结果：

1. 打开用户提供的目标视频；从画面开始运动计时，墙钟约 5 秒和 10 秒时分别记录 UI 当前时间，总时长应约 `01:51`，10 秒时不得进入 `01:40` 以后。
2. 暂停三秒，确认时间数字和滑块不增长；恢复五秒，确认位置从暂停点继续。
3. 向前 seek 后核对画面、声音和时间，再向后 seek 并重复核对。
4. 播放同目录 GPAC fMP4 `5_6239897357152951964.mp4`，确认不会在数秒内跳到约 `00:35 / 00:37`。
5. 播放普通平坦 MP4 `5_6239902382264688558.mp4`，确认墙钟约七秒时 UI 仍约为 `00:07 / 00:19`。

若 LAN 地址不可达，记录 `BLOCKED` 并附连接错误；不得修改服务器或样本文件来规避该验收。

- [ ] **Step 5: 编写中文验证记录**

在 `docs/verification/2026-08-03-fragmented-mp4-progress.md` 中写入以下固定结构，并复制 Steps 1–4 的真实命令、退出码、时间读数和设备信息：

```markdown
# 分片 MP4 播放进度兼容验证

- 日期：2026-08-03
- 设计：`docs/superpowers/specs/2026-08-03-fragmented-mp4-progress-design.md`
- 实施计划：`docs/superpowers/plans/2026-08-03-fragmented-mp4-progress.md`

## 1. 自动测试

逐条记录聚焦测试、完整 JVM 测试、AndroidTest 编译、lint 和 Debug APK 构建的命令、退出码与结论。

## 2. 设备与 LibVLC 冒烟

记录设备型号、API、APK 路径、instrumentation 命令和真实结果；未运行时明确写 `NOT RUN`。

## 3. 用户 LAN 样本

记录目标 fMP4 在 5 秒、10 秒、暂停、恢复、向前 seek、向后 seek 时的墙钟与 UI 读数；不可访问时明确写 `BLOCKED` 和原因。

## 4. 对照样本

分别记录另一份 GPAC fMP4 与普通平坦 MP4 的墙钟、UI 读数和结论。

## 5. 范围核对

确认没有服务器修改、媒体重封装、依赖升级、全局 `avformat`、MediaSession 位置链路或 UI 进度计算改动。
```

- [ ] **Step 6: 使用完成前验证技能核对最新证据**

执行 `superpowers:verification-before-completion`，并至少运行：

```powershell
git diff --check
git status --short
git log --oneline -6
```

核对实现提交只包含计划列出的文件，用户原有未提交改动仍然保留；不得因为工作树不干净而重置、清理或纳入提交。

- [ ] **Step 7: 只提交验证记录**

```powershell
git add -- docs/verification/2026-08-03-fragmented-mp4-progress.md
git diff --cached --check
git diff --cached --name-only
git commit -m "docs: verify fragmented MP4 progress fix"
```

提交前确认暂存清单只有验证记录；最终报告分别列出自动门禁、设备测试和 LAN 样本状态。
