# 端到端验证与 APK 交付 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 API 36 x86_64 模拟器上完成目录、图片、音频、视频、HTTP Range、旋转与启动验收，并交付可复现的 Debug APK、SHA-256、中文文档和实际验收记录。

**Architecture:** 仪器测试在应用缓存目录中确定性生成 PNG、PCM WAV 与 H.264 MP4，再由 MockWebServer 以 Caddy JSON 和 HTTP Range 形式提供；Compose 全导航使用 `FakeAppContainer`，原生播放使用真实 `AndroidVlcPlaybackEngine`。PowerShell 验收脚本统一执行 JVM、Lint、仪器测试、真实 Caddy 双根烟测、安装、启动、复制 APK 和记录环境。

**Tech Stack:** AndroidX Test、Compose UI Test、MockWebServer 5.3.0、Android MediaCodec/MediaMuxer、LibVLC、PowerShell 7、Gradle 9.5.0。

## Global Constraints

- 测试媒体只在模拟器的应用缓存目录生成，不读取、复制或记录 `I:\MiddleDir`、`G:\pik` 中的文件。
- 目录夹具必须与 Caddy JSON 字段完全一致，并同时覆盖 `/middle/`、`/pik/`。
- 媒体夹具必须支持无 Range 的 `200`、合法单段 Range 的 `206`、越界 Range 的 `416` 和 `HEAD`。
- LibVLC 测试使用真实引擎和真实 `SurfaceView`，不得通过 mock 假装原生解码成功。
- 全导航测试使用替身播放器，避免把原生解码时序混入 Compose 路由断言。
- 真实服务器验收只验证状态码、JSON 可解析性和应用内目录解析，不输出响应体或用户文件名。
- 最终只交付 Debug APK；不生成、导入或记录正式签名密钥。
- `dist/` 产物不提交 Git；验收记录、README 与第三方许可说明提交 Git。
- 任一门禁失败时立即停止，不生成“通过”记录，也不覆盖已有成功交付件。

---

### Task 13: 夹具服务器、全流程测试、模拟器验收与交付

**Files:**

- Create: `app/src/androidTest/java/com/local/mediaviewer/testing/MediaFixtureFactory.kt`
- Create: `app/src/androidTest/java/com/local/mediaviewer/testing/MediaFixtureServer.kt`
- Create: `app/src/androidTest/java/com/local/mediaviewer/testing/FakeAppContainer.kt`
- Create: `app/src/androidTest/java/com/local/mediaviewer/MediaFixtureServerTest.kt`
- Create: `app/src/androidTest/java/com/local/mediaviewer/MediaViewerNavigationTest.kt`
- Create: `app/src/androidTest/java/com/local/mediaviewer/MediaPlaybackInstrumentedTest.kt`
- Create: `app/src/androidTest/java/com/local/mediaviewer/RealServerSmokeTest.kt`
- Create: `scripts/Invoke-AndroidVerification.ps1`
- Create: `scripts/Write-ApkChecksum.ps1`
- Create: `README.md`
- Create: `THIRD_PARTY_NOTICES.md`
- Create after all gates pass: `docs/verification/2026-07-28-android-mediaviewer.md`
- Generate, do not commit: `dist/mediaviewer-debug.apk`
- Generate, do not commit: `dist/mediaviewer-debug.apk.sha256`

**Interfaces:**

- Consumes:

```kotlin
interface AppContainer
interface BrowserRepository
interface ServerSessionManager
interface ServerSettingsRepository
interface PlaybackEngine
fun interface PlaybackEngineFactory
interface PlaybackPositionStore
class AndroidVlcPlaybackEngine
class DefaultConnectionProbe
class DefaultCaddyDirectoryClient
object ServerUrlValidator
```

- Produces:

```kotlin
data class MediaFixtures(
    val png: File,
    val wav: File,
    val mp4: File,
)

class MediaFixtureFactory(private val directory: File) {
    fun create(): MediaFixtures
}

class MediaFixtureServer(
    private val fixtures: MediaFixtures,
) : Closeable {
    fun start()
    fun url(path: String): String
    fun rangeRequestCount(path: String): Int
    override fun close()
}
```

- [ ] **Step 1: 核对任务 01 已固定的仪器测试依赖**

在 `app/build.gradle.kts` 的 `dependencies` 中逐行确认以下配置仍存在：

```kotlin
androidTestImplementation(platform(libs.okhttp.bom))
androidTestImplementation(libs.mockwebserver)
androidTestImplementation(libs.androidx.test.junit)
androidTestImplementation(libs.androidx.test.runner)
androidTestImplementation(libs.androidx.test.rules)
androidTestImplementation(libs.androidx.test.espresso)
androidTestImplementation(libs.androidx.compose.ui.test.junit4)
debugImplementation(libs.androidx.compose.ui.test.manifest)
androidTestUtil(libs.androidx.test.orchestrator)
```

这些配置已经由任务 01 创建；本步骤不新增版本目录别名。MockWebServer 只在
`testImplementation` 与 `androidTestImplementation` 中出现，不进入最终 APK
的运行时依赖图。

- [ ] **Step 2: 先写 Range 夹具服务器失败测试**

`MediaFixtureServerTest.kt`：

```kotlin
package com.local.mediaviewer

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.local.mediaviewer.testing.MediaFixtureFactory
import com.local.mediaviewer.testing.MediaFixtureServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MediaFixtureServerTest {
    @Test
    fun caddyDirectoriesAndByteRangesAreServed() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val directory = File(context.cacheDir, "fixture-server-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val fixtures = MediaFixtureFactory(directory).create()
        MediaFixtureServer(fixtures).use { server ->
            server.start()
            val client = OkHttpClient()

            val listing = client.newCall(
                Request.Builder()
                    .url(server.url("/middle/"))
                    .header("Accept", "application/json")
                    .build(),
            ).execute()
            listing.use {
                assertEquals(200, it.code)
                val json = requireNotNull(it.body).string()
                assertTrue(json.contains("\"name\":\"sample.mp4\""))
                assertTrue(json.contains("\"name\":\"sample.wav\""))
                assertTrue(json.contains("\"name\":\"sample.png\""))
            }

            val ranged = client.newCall(
                Request.Builder()
                    .url(server.url("/middle/sample.mp4"))
                    .header("Range", "bytes=4-11")
                    .build(),
            ).execute()
            ranged.use {
                assertEquals(206, it.code)
                assertEquals(
                    "bytes 4-11/${fixtures.mp4.length()}",
                    it.header("Content-Range"),
                )
                assertEquals(8L, requireNotNull(it.body).contentLength())
            }

            val suffix = client.newCall(
                Request.Builder()
                    .url(server.url("/middle/sample.mp4"))
                    .header("Range", "bytes=-4")
                    .build(),
            ).execute()
            suffix.use {
                assertEquals(206, it.code)
                assertEquals(4L, requireNotNull(it.body).contentLength())
            }

            val head = client.newCall(
                Request.Builder()
                    .url(server.url("/middle/sample.mp4"))
                    .head()
                    .build(),
            ).execute()
            head.use {
                assertEquals(200, it.code)
                assertEquals(fixtures.mp4.length().toString(), it.header("Content-Length"))
            }

            val unsatisfiable = client.newCall(
                Request.Builder()
                    .url(server.url("/middle/sample.mp4"))
                    .header("Range", "bytes=${fixtures.mp4.length()}-")
                    .build(),
            ).execute()
            unsatisfiable.use {
                assertEquals(416, it.code)
                assertEquals(
                    "bytes */${fixtures.mp4.length()}",
                    it.header("Content-Range"),
                )
            }
            assertEquals(3, server.rangeRequestCount("/middle/sample.mp4"))
        }
    }
}
```

- [ ] **Step 3: 运行夹具测试并观察预期失败**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaFixtureServerTest
```

Expected:

```text
Kotlin compilation fails because MediaFixtureFactory and MediaFixtureServer are unresolved
```

- [ ] **Step 4: 实现确定性 PNG、WAV、MP4 生成器**

`MediaFixtureFactory.kt`：

```kotlin
package com.local.mediaviewer.testing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

data class MediaFixtures(
    val png: File,
    val wav: File,
    val mp4: File,
)

class MediaFixtureFactory(
    private val directory: File,
) {
    fun create(): MediaFixtures {
        check(directory.mkdirs() || directory.isDirectory)
        val png = File(directory, "sample.png")
        val wav = File(directory, "sample.wav")
        val mp4 = File(directory, "sample.mp4")
        writePng(png)
        writeWav(wav)
        writeMp4(mp4)
        check(png.length() > 0L)
        check(wav.length() > 44L)
        check(mp4.length() > 0L)
        return MediaFixtures(png, wav, mp4)
    }

    private fun writePng(file: File) {
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(24, 32, 48))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(80, 180, 255)
        }
        canvas.drawRect(32f, 32f, 288f, 148f, paint)
        paint.color = Color.WHITE
        paint.textSize = 28f
        canvas.drawText("mediaviewer", 68f, 102f, paint)
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
    }

    private fun writeWav(file: File) {
        val sampleRate = 8_000
        val durationSeconds = 4
        val sampleCount = sampleRate * durationSeconds
        val dataSize = sampleCount * 2
        DataOutputStream(FileOutputStream(file)).use { output ->
            output.writeBytes("RIFF")
            output.writeLittleEndianInt(36 + dataSize)
            output.writeBytes("WAVE")
            output.writeBytes("fmt ")
            output.writeLittleEndianInt(16)
            output.writeLittleEndianShort(1)
            output.writeLittleEndianShort(1)
            output.writeLittleEndianInt(sampleRate)
            output.writeLittleEndianInt(sampleRate * 2)
            output.writeLittleEndianShort(2)
            output.writeLittleEndianShort(16)
            output.writeBytes("data")
            output.writeLittleEndianInt(dataSize)
            repeat(sampleCount) { index ->
                val angle = 2.0 * PI * 440.0 * index / sampleRate
                val value = (sin(angle) * Short.MAX_VALUE * 0.25).toInt()
                output.writeLittleEndianShort(value)
            }
        }
    }

    private fun writeMp4(file: File) {
        val width = 160
        val height = 120
        val framesPerSecond = 10
        val frameCount = 40
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            .apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
                )
                setInteger(MediaFormat.KEY_BIT_RATE, 240_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, framesPerSecond)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val muxer = MediaMuxer(
            file.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        )
        var muxerStarted = false
        var trackIndex = -1
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        try {
            val info = MediaCodec.BufferInfo()
            var inputFrame = 0
            var inputEnded = false
            var outputEnded = false
            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        if (inputFrame < frameCount) {
                            val input = requireNotNull(codec.getInputBuffer(inputIndex))
                            input.clear()
                            writeI420Frame(input, width, height, inputFrame)
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                width * height * 3 / 2,
                                inputFrame * 1_000_000L / framesPerSecond,
                                0,
                            )
                            inputFrame += 1
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                frameCount * 1_000_000L / framesPerSecond,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEnded = true
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxerStarted)
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    else -> if (outputIndex >= 0) {
                        val output = requireNotNull(codec.getOutputBuffer(outputIndex))
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            info.size = 0
                        }
                        if (info.size > 0) {
                            check(muxerStarted)
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, output, info)
                        }
                        outputEnded =
                            info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            if (muxerStarted) {
                muxer.stop()
            }
            muxer.release()
        }
    }

    private fun writeI420Frame(
        buffer: java.nio.ByteBuffer,
        width: Int,
        height: Int,
        frame: Int,
    ) {
        repeat(height) { y ->
            repeat(width) { x ->
                val luma = 32 + ((x + y + frame * 4) % 192)
                buffer.put(luma.toByte())
            }
        }
        val chromaSize = width * height / 4
        repeat(chromaSize) { buffer.put((96 + frame % 32).toByte()) }
        repeat(chromaSize) { buffer.put((160 - frame % 32).toByte()) }
    }
}

private fun DataOutputStream.writeLittleEndianInt(value: Int) {
    writeByte(value and 0xff)
    writeByte(value ushr 8 and 0xff)
    writeByte(value ushr 16 and 0xff)
    writeByte(value ushr 24 and 0xff)
}

private fun DataOutputStream.writeLittleEndianShort(value: Int) {
    writeByte(value and 0xff)
    writeByte(value ushr 8 and 0xff)
}
```

编码器输入固定为 I420：先写 `width * height` 个 Y，再写两个各 `width * height / 4` 的 U/V 平面。生成器只调用 Android 平台 API，不依赖本机 ffmpeg。

- [ ] **Step 5: 实现 Caddy JSON 与 HTTP Range 夹具服务器**

`MediaFixtureServer.kt`：

```kotlin
package com.local.mediaviewer.testing

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import java.io.Closeable
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class MediaFixtureServer(
    private val fixtures: MediaFixtures,
) : Closeable {
    private val server = MockWebServer()
    private val rangeCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val files = mapOf(
        "sample.mp4" to fixtures.mp4,
        "sample.wav" to fixtures.wav,
        "sample.png" to fixtures.png,
    )

    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                responseFor(request)
        }
        server.start()
    }

    fun url(path: String): String = server.url(path).toString()

    fun rangeRequestCount(path: String): Int =
        rangeCounts[path]?.get() ?: 0

    override fun close() {
        server.close()
    }

    private fun responseFor(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        if (path == "/middle/" || path == "/pik/") {
            return MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .body(directoryJson())
                .build()
        }
        val prefix = when {
            path.startsWith("/middle/") -> "/middle/"
            path.startsWith("/pik/") -> "/pik/"
            else -> return MockResponse(code = 404)
        }
        val file = files[path.removePrefix(prefix)]
            ?: return MockResponse(code = 404)
        return mediaResponse(request, path, file)
    }

    private fun directoryJson(): String =
        files.entries.joinToString(prefix = "[", postfix = "]") { (name, file) ->
            val mode = 420
            """{"name":"$name","size":${file.length()},"url":"$name","mod_time":"2026-07-28T00:00:00Z","mode":$mode,"is_dir":false,"is_symlink":false}"""
        }

    private fun mediaResponse(
        request: RecordedRequest,
        path: String,
        file: File,
    ): MockResponse {
        val bytes = file.readBytes()
        val contentType = when (file.extension) {
            "mp4" -> "video/mp4"
            "wav" -> "audio/wav"
            "png" -> "image/png"
            else -> "application/octet-stream"
        }
        if (request.method == "HEAD") {
            return MockResponse.Builder()
                .code(200)
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("Content-Type", contentType)
                .setHeader("Content-Length", bytes.size)
                .build()
        }
        val rangeHeader = request.headers["Range"]
        if (rangeHeader == null) {
            return MockResponse.Builder()
                .code(200)
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("Content-Type", contentType)
                .body(Buffer().write(bytes))
                .build()
        }
        rangeCounts.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
        val match = Regex("""bytes=(\d*)-(\d*)""").matchEntire(rangeHeader)
            ?: return rangeNotSatisfiable(bytes.size)
        val startText = match.groupValues[1]
        val endText = match.groupValues[2]
        if (startText.isEmpty() && endText.isEmpty()) {
            return rangeNotSatisfiable(bytes.size)
        }
        val start: Long
        val end: Long
        if (startText.isEmpty()) {
            val suffixLength = endText.toLongOrNull()
                ?: return rangeNotSatisfiable(bytes.size)
            if (suffixLength <= 0L) {
                return rangeNotSatisfiable(bytes.size)
            }
            start = (bytes.size - suffixLength).coerceAtLeast(0L)
            end = bytes.lastIndex.toLong()
        } else {
            start = startText.toLongOrNull()
                ?: return rangeNotSatisfiable(bytes.size)
            if (start >= bytes.size) {
                return rangeNotSatisfiable(bytes.size)
            }
            val requestedEnd = endText
                .takeIf(String::isNotEmpty)
                ?.toLongOrNull()
            if (endText.isNotEmpty() && requestedEnd == null) {
                return rangeNotSatisfiable(bytes.size)
            }
            end = minOf(
                requestedEnd ?: bytes.lastIndex.toLong(),
                bytes.lastIndex.toLong(),
            )
        }
        if (end < start) {
            return rangeNotSatisfiable(bytes.size)
        }
        val length = (end - start + 1).toInt()
        return MockResponse.Builder()
            .code(206)
            .setHeader("Accept-Ranges", "bytes")
            .setHeader("Content-Type", contentType)
            .setHeader("Content-Range", "bytes $start-$end/${bytes.size}")
            .body(Buffer().write(bytes, start.toInt(), length))
            .build()
    }

    private fun rangeNotSatisfiable(size: Int): MockResponse =
        MockResponse.Builder()
            .code(416)
            .setHeader("Content-Range", "bytes */$size")
            .build()
}
```

该实现不打印请求头、目录正文或媒体正文。测试类只读取已知夹具名称和字节数。

- [ ] **Step 6: 运行 Range 夹具测试并确认通过**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaFixtureServerTest
```

Expected:

```text
MediaFixtureServerTest passes
/middle/ returns valid Caddy-shaped JSON
bytes=4-11 returns 206 and an 8-byte body
bytes=-4 returns 206, HEAD returns the full length, and an out-of-range start returns 416
```

- [ ] **Step 7: 实现完整导航所需 FakeAppContainer**

`FakeAppContainer.kt`：

```kotlin
package com.local.mediaviewer.testing

import android.content.Context
import android.view.SurfaceView
import coil3.ImageLoader
import com.local.mediaviewer.app.AppContainer
import com.local.mediaviewer.browser.Breadcrumb
import com.local.mediaviewer.browser.BrowserPage
import com.local.mediaviewer.browser.BrowserRepository
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.image.MediaImageLoaderFactory
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.RootShare
import com.local.mediaviewer.model.ServerConfig
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.network.ConnectionTestResult
import com.local.mediaviewer.playback.PlaybackEngine
import com.local.mediaviewer.playback.PlaybackEngineFactory
import com.local.mediaviewer.playback.PlaybackPositionStore
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import com.local.mediaviewer.settings.ServerSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

class FakeAppContainer(context: Context) : AppContainer, AutoCloseable {
    private val endpoint = SessionEndpoint(
        logicalBaseUrl = "http://media.test:8080",
        requestBaseUrl = "http://127.0.0.1:8080",
        ipv4 = "127.0.0.1",
    )

    override val settingsRepository: ServerSettingsRepository =
        FakeServerSettingsRepository()
    override val sessionManager: ServerSessionManager =
        FakeServerSessionManager(endpoint)
    override val browserRepository: BrowserRepository =
        FakeBrowserRepository(endpoint)
    override val playbackEngineFactory = PlaybackEngineFactory {
        FakePlaybackEngine()
    }
    override val playbackPositionStore: PlaybackPositionStore =
        InMemoryPlaybackPositionStore()
    override val imageLoader: ImageLoader =
        MediaImageLoaderFactory.create(context)

    override fun close() {
        imageLoader.shutdown()
    }
}

private class FakeServerSettingsRepository : ServerSettingsRepository {
    private val mutable = MutableStateFlow(ServerConfig("http://media.test:8080"))
    override val config: Flow<ServerConfig> = mutable
    override suspend fun current(): ServerConfig = mutable.value
    override suspend fun save(config: ServerConfig) {
        mutable.value = config
    }
}

private class FakeServerSessionManager(
    private val endpoint: SessionEndpoint,
) : ServerSessionManager {
    private val mutable = MutableStateFlow<ServerSessionState>(
        ServerSessionState.Connected(endpoint, listOf(endpoint.ipv4)),
    )
    override val state: StateFlow<ServerSessionState> = mutable

    override suspend fun connectSaved() {
        mutable.value = ServerSessionState.Connected(
            endpoint,
            listOf(endpoint.ipv4),
        )
    }

    override suspend fun testCandidate(
        input: String,
    ): AppResult<ConnectionTestResult> =
        error("导航测试不进入设置探测：$input")

    override suspend fun saveCandidate(result: ConnectionTestResult) {
        error("导航测试不保存设置：${result.endpoint.logicalBaseUrl}")
    }

    override suspend fun refreshAfterRequestFailure(): AppResult<SessionEndpoint> =
        AppResult.Success(endpoint)
}

private class FakeBrowserRepository(
    private val endpoint: SessionEndpoint,
) : BrowserRepository {
    override suspend fun openRoot(root: RootShare): AppResult<BrowserPage> {
        val logical = endpoint.logicalBaseUrl + root.path
        val request = endpoint.requestBaseUrl + root.path
        val folder = entry(
            name = "示例目录",
            logicalUrl = logical + "nested/",
            requestUrl = request + "nested/",
            kind = MediaKind.DIRECTORY,
        )
        return AppResult.Success(
            BrowserPage(
                root = root,
                logicalDirectoryUrl = logical,
                requestDirectoryUrl = request,
                breadcrumbs = listOf(Breadcrumb(root.displayName, logical)),
                entries = listOf(folder),
            ),
        )
    }

    override suspend fun openDirectory(
        root: RootShare,
        logicalUrl: String,
        breadcrumbs: List<Breadcrumb>,
    ): AppResult<BrowserPage> {
        val requestDirectoryUrl = endpoint.requestUrlFor(logicalUrl)
        return AppResult.Success(
            BrowserPage(
                root = root,
                logicalDirectoryUrl = logicalUrl,
                requestDirectoryUrl = requestDirectoryUrl,
                breadcrumbs = breadcrumbs,
                entries = listOf(
                    entry(
                        "样例.mp4",
                        logicalUrl + "sample.mp4",
                        requestDirectoryUrl + "sample.mp4",
                        MediaKind.VIDEO,
                    ),
                    entry(
                        "样例.wav",
                        logicalUrl + "sample.wav",
                        requestDirectoryUrl + "sample.wav",
                        MediaKind.AUDIO,
                    ),
                    entry(
                        "样例.png",
                        logicalUrl + "sample.png",
                        requestDirectoryUrl + "sample.png",
                        MediaKind.IMAGE,
                    ),
                ),
            ),
        )
    }

    private fun entry(
        name: String,
        logicalUrl: String,
        requestUrl: String,
        kind: MediaKind,
    ) = DirectoryEntry(
        name = name,
        size = 1_536L,
        modifiedAt = Instant.parse("2026-07-28T00:00:00Z"),
        mode = 420L,
        isDirectory = kind == MediaKind.DIRECTORY,
        isSymlink = false,
        logicalUrl = logicalUrl,
        requestUrl = requestUrl,
        kind = kind,
    )
}

private class FakePlaybackEngine : PlaybackEngine {
    private val mutable = MutableStateFlow(
        PlaybackState(
            status = PlaybackStatus.IDLE,
            durationMs = 60_000L,
            isSeekable = true,
        ),
    )
    override val state: StateFlow<PlaybackState> = mutable
    override fun prepare(url: String) {
        mutable.value = mutable.value.copy(status = PlaybackStatus.PAUSED)
    }
    override fun attachVideoSurface(surfaceView: SurfaceView) = Unit
    override fun detachVideoSurface() = Unit
    override fun play() {
        mutable.value = mutable.value.copy(status = PlaybackStatus.PLAYING)
    }
    override fun pause() {
        mutable.value = mutable.value.copy(status = PlaybackStatus.PAUSED)
    }
    override fun seekTo(positionMs: Long) {
        mutable.value = mutable.value.copy(positionMs = positionMs)
    }
    override fun close() = Unit
}

private class InMemoryPlaybackPositionStore : PlaybackPositionStore {
    private val positions = mutableMapOf<String, Long>()
    override suspend fun resumePosition(mediaKey: String): Long? = positions[mediaKey]
    override suspend fun record(
        mediaKey: String,
        positionMs: Long,
        durationMs: Long,
        updatedAtEpochMs: Long,
        ended: Boolean,
    ) {
        if (ended) positions.remove(mediaKey) else positions[mediaKey] = positionMs
    }
    override suspend fun clear(mediaKey: String) {
        positions.remove(mediaKey)
    }
}
```

`FakeServerSessionManager` 对不属于导航验收的设置写操作直接失败，使测试不会静默掩盖错误路由。

- [ ] **Step 8: 写首页、嵌套目录与三类媒体导航测试**

`MediaViewerNavigationTest.kt`：

```kotlin
package com.local.mediaviewer

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.local.mediaviewer.app.MediaViewerApp
import com.local.mediaviewer.testing.FakeAppContainer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaViewerNavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var container: FakeAppContainer

    @Before
    fun setUp() {
        container = FakeAppContainer(ApplicationProvider.getApplicationContext())
        rule.setContent { MediaViewerApp(container) }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("MiddleDir")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun tearDown() {
        container.close()
    }

    @Test
    fun homeOpensNestedVideo() {
        openNestedDirectory()
        rule.onNodeWithText("样例.mp4").performClick()
        rule.onNodeWithText("样例.mp4").assertIsDisplayed()
        rule.onNodeWithTag("vlc_surface").assertExists()
    }

    @Test
    fun homeOpensNestedAudio() {
        openNestedDirectory()
        rule.onNodeWithText("样例.wav").performClick()
        rule.onNodeWithText("样例.wav").assertIsDisplayed()
        rule.onNodeWithTag("seek").assertExists()
    }

    @Test
    fun homeOpensNestedImage() {
        openNestedDirectory()
        rule.onNodeWithText("样例.png").performClick()
        rule.onNodeWithText("样例.png").assertIsDisplayed()
        rule.onNodeWithTag("media_image").assertExists()
    }

    private fun openNestedDirectory() {
        rule.onNodeWithText("MiddleDir").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("示例目录")
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("示例目录").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("样例.mp4")
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("MiddleDir").assertExists()
        rule.onNodeWithText("示例目录").assertExists()
    }
}
```

- [ ] **Step 9: 运行完整导航测试**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaViewerNavigationTest
```

Expected:

```text
3 tests pass
Home -> MiddleDir -> 示例目录 -> video/audio/image routes are reachable
```

- [ ] **Step 10: 写真实 LibVLC、Range、seek 与旋转仪器测试**

`MediaPlaybackInstrumentedTest.kt`：

```kotlin
package com.local.mediaviewer

import android.content.Context
import android.content.pm.ActivityInfo
import android.view.SurfaceView
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.local.mediaviewer.image.MediaImageLoaderFactory
import com.local.mediaviewer.playback.AndroidVlcPlaybackEngine
import com.local.mediaviewer.playback.MediaViewerDatabase
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.playback.RoomPlaybackPositionStore
import com.local.mediaviewer.testing.MediaFixtureFactory
import com.local.mediaviewer.testing.MediaFixtureServer
import com.local.mediaviewer.ui.player.FullscreenController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class MediaPlaybackInstrumentedTest {
    private lateinit var context: Context
    private lateinit var server: MediaFixtureServer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val directory = File(context.cacheDir, "playback-fixtures").apply {
            deleteRecursively()
            mkdirs()
        }
        server = MediaFixtureServer(MediaFixtureFactory(directory).create())
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun videoUsesRangePlaysSeeksAndReattachesAfterActivityRecreation() {
        val engine = AndroidVlcPlaybackEngine(context)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val surface = SurfaceView(activity)
                    activity.setContentView(surface)
                    engine.attachVideoSurface(surface)
                }
                engine.prepare(server.url("/middle/sample.mp4"))
                engine.play()
                waitUntil(20_000) {
                    engine.state.value.durationMs > 0L &&
                        engine.state.value.isSeekable &&
                        engine.state.value.status in setOf(
                            PlaybackStatus.PLAYING,
                            PlaybackStatus.PAUSED,
                            PlaybackStatus.ENDED,
                        )
                }
                val duration = engine.state.value.durationMs
                val target = duration / 2
                engine.seekTo(target)
                engine.play()
                waitUntil(10_000) {
                    abs(engine.state.value.positionMs - target) < 2_000L
                }
                val positionBeforeRecreation = engine.state.value.positionMs
                scenario.onActivity {
                    engine.detachVideoSurface()
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    val replacementSurface = SurfaceView(activity)
                    activity.setContentView(replacementSurface)
                    engine.attachVideoSurface(replacementSurface)
                }
                engine.play()
                waitUntil(10_000) {
                    engine.state.value.status != PlaybackStatus.ERROR &&
                        engine.state.value.durationMs == duration &&
                        engine.state.value.positionMs >=
                            (positionBeforeRecreation - 1_000L).coerceAtLeast(0L)
                }
                assertTrue(server.rangeRequestCount("/middle/sample.mp4") > 0)
            }
        } finally {
            engine.close()
        }
    }

    @Test
    fun audioUsesRangeAndBecomesSeekable() {
        val engine = AndroidVlcPlaybackEngine(context)
        try {
            engine.prepare(server.url("/pik/sample.wav"))
            engine.play()
            waitUntil(20_000) {
                engine.state.value.durationMs > 0L &&
                    engine.state.value.isSeekable &&
                    engine.state.value.status != PlaybackStatus.ERROR
            }
            val target = engine.state.value.durationMs / 2
            engine.seekTo(target)
            waitUntil(10_000) {
                abs(engine.state.value.positionMs - target) < 2_000L ||
                    engine.state.value.status == PlaybackStatus.ENDED
            }
            assertTrue(server.rangeRequestCount("/pik/sample.wav") > 0)
        } finally {
            engine.close()
        }
    }

    @Test
    fun fixtureImageLoadsWithMemoryOnlyCoil() = runBlocking {
        val loader = MediaImageLoaderFactory.create(context)
        try {
            val request = ImageRequest.Builder(context)
                .data(server.url("/pik/sample.png"))
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
            val result = loader.execute(request)
            assertTrue(result is SuccessResult)
            assertNull(loader.diskCache)
        } finally {
            loader.shutdown()
        }
    }

    @Test
    fun playbackPositionSurvivesDatabaseReopen() = runBlocking {
        val databaseName = "playback-restart-test.db"
        val mediaKey = "http://media.test:8080/middle/sample.mp4"
        context.deleteDatabase(databaseName)
        try {
            val firstDatabase = Room.databaseBuilder(
                context,
                MediaViewerDatabase::class.java,
                databaseName,
            ).build()
            RoomPlaybackPositionStore(firstDatabase.playbackPositionDao()).record(
                mediaKey = mediaKey,
                positionMs = 15_000L,
                durationMs = 60_000L,
                updatedAtEpochMs = 1_722_124_800_000L,
                ended = false,
            )
            firstDatabase.close()

            val reopenedDatabase = Room.databaseBuilder(
                context,
                MediaViewerDatabase::class.java,
                databaseName,
            ).build()
            try {
                val reopenedStore = RoomPlaybackPositionStore(
                    reopenedDatabase.playbackPositionDao(),
                )
                assertEquals(15_000L, reopenedStore.resumePosition(mediaKey))
            } finally {
                reopenedDatabase.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun fullscreenControllerRequestsLandscapeAndRestores() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controller = FullscreenController(activity)
                try {
                    controller.enter()
                    assertEquals(
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                        activity.requestedOrientation,
                    )
                    assertTrue(controller.isFullscreen.value)
                } finally {
                    controller.close()
                }
                assertEquals(
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                    activity.requestedOrientation,
                )
            }
        }
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        error("在 ${timeoutMs}ms 内未达到播放条件，当前状态未通过验收")
    }
}
```

两个测试都用 `try/finally` 包围引擎生命周期，断言失败时仍会释放 LibVLC。
这些原生断言验证 LibVLC 实际读取本地 HTTP 服务器，并至少产生一次 Range 请求。

- [ ] **Step 11: 运行真实媒体仪器测试**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaPlaybackInstrumentedTest
```

Expected:

```text
5 tests pass on Pixel_3a_API_36_extension_level_17_x86_64
sample.mp4 opens, reports duration, seeks, reattaches after Activity recreation, and issues Range
sample.wav opens, reports duration, seeks, and issues Range
sample.png loads through the memory-only ImageLoader
Room playback position survives closing and reopening the database
FullscreenController requests sensor landscape and restores orientation
No PlaybackStatus.ERROR is observed
```

- [ ] **Step 12: 写真实服务器双根烟测**

`RealServerSmokeTest.kt`：

```kotlin
package com.local.mediaviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.RootShare
import com.local.mediaviewer.network.DefaultCaddyDirectoryClient
import com.local.mediaviewer.network.DefaultConnectionProbe
import com.local.mediaviewer.network.DefaultDirectoryJsonParser
import com.local.mediaviewer.network.OkHttpDirectoryProbeTransport
import com.local.mediaviewer.network.SystemIpv4Resolver
import com.local.mediaviewer.settings.ServerUrlValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealServerSmokeTest {
    @Test
    fun bothConfiguredRootsReturnCaddyDirectories() = runBlocking {
        val baseUrl = InstrumentationRegistry.getArguments()
            .getString("realServerBaseUrl")
        assumeTrue(
            "仅在传入 realServerBaseUrl 时执行真实服务器烟测",
            !baseUrl.isNullOrBlank(),
        )
        val server = (
            ServerUrlValidator.validate(requireNotNull(baseUrl)) as AppResult.Success
        ).value
        val addresses = (
            SystemIpv4Resolver().resolve(server.host) as AppResult.Success
        ).value
        val result = DefaultConnectionProbe(
            OkHttpDirectoryProbeTransport(),
            DefaultDirectoryJsonParser(),
        ).probe(server, addresses)
        val endpoint = (result as AppResult.Success).value.endpoint
        val client = DefaultCaddyDirectoryClient()

        for (root in RootShare.entries) {
            val logicalUrl = endpoint.logicalBaseUrl + root.path
            val requestUrl = endpoint.requestBaseUrl + root.path
            val listing = client.listDirectory(logicalUrl, requestUrl)
            assertTrue(
                "${root.path} 必须返回合法 Caddy JSON",
                listing is AppResult.Success,
            )
        }
    }
}
```

该测试不读取媒体正文，不断言真实目录中存在特定文件，也不输出解析后的条目名称。

- [ ] **Step 13: 实现 APK 复制与 SHA-256 脚本**

`scripts/Write-ApkChecksum.ps1`：

```powershell
[CmdletBinding()]
param(
    [string]$ApkPath = (
        Join-Path $PSScriptRoot '..\app\build\outputs\apk\debug\app-debug.apk'
    )
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$sourceApk = [IO.Path]::GetFullPath($ApkPath)
$distDirectory = Join-Path $repositoryRoot 'dist'
$targetApk = Join-Path $distDirectory 'mediaviewer-debug.apk'
$checksumPath = Join-Path $distDirectory 'mediaviewer-debug.apk.sha256'

if (-not (Test-Path -LiteralPath $sourceApk -PathType Leaf)) {
    throw "未找到 Debug APK：$sourceApk"
}

New-Item -ItemType Directory -Path $distDirectory -Force | Out-Null
Copy-Item -LiteralPath $sourceApk -Destination $targetApk -Force
$hash = (Get-FileHash -LiteralPath $targetApk -Algorithm SHA256).Hash.ToLowerInvariant()
$utf8NoBom = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText(
    $checksumPath,
    "$hash  mediaviewer-debug.apk`n",
    $utf8NoBom
)

$verified = (Get-FileHash -LiteralPath $targetApk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($verified -ne $hash) {
    throw '复制后的 APK SHA-256 二次校验失败'
}

[PSCustomObject]@{
    Apk = $targetApk
    Sha256File = $checksumPath
    Sha256 = $hash
}
```

手工验证命令：

```powershell
.\scripts\Write-ApkChecksum.ps1
$expected = (Get-Content .\dist\mediaviewer-debug.apk.sha256).Split(' ')[0]
$actual = (Get-FileHash .\dist\mediaviewer-debug.apk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($expected -ne $actual) { throw 'SHA-256 不一致' }
```

- [ ] **Step 14: 实现统一模拟器验收脚本**

`scripts/Invoke-AndroidVerification.ps1`：

```powershell
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$SdkRoot,

    [Parameter(Mandatory)]
    [string]$AvdName,

    [Parameter(Mandatory)]
    [string]$RealServerBaseUrl
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$adb = Join-Path $SdkRoot 'platform-tools\adb.exe'
$emulator = Join-Path $SdkRoot 'emulator\emulator.exe'
$gradle = Join-Path $repositoryRoot 'gradlew.bat'

foreach ($required in @($adb, $emulator, $gradle)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "缺少必需文件：$required"
    }
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory)]
        [string]$FilePath,

        [Parameter(Mandatory)]
        [string[]]$Arguments
    )
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "命令失败，退出码 $LASTEXITCODE：$FilePath $($Arguments -join ' ')"
    }
}

function Find-AvdSerial {
    param(
        [Parameter(Mandatory)]
        [string]$ExpectedAvdName
    )
    $deviceLines = & $adb devices
    foreach ($line in $deviceLines) {
        if ($line -notmatch '^(emulator-\d+)\s+device$') {
            continue
        }
        $candidateSerial = $Matches[1]
        $reportedName = (
            & $adb -s $candidateSerial emu avd name 2>$null |
                Select-Object -First 1
        )
        if (
            $null -ne $reportedName -and
            $reportedName.Trim() -eq $ExpectedAvdName
        ) {
            return $candidateSerial
        }
    }
    return $null
}

$hadAndroidSerial = Test-Path Env:ANDROID_SERIAL
$previousAndroidSerial = $env:ANDROID_SERIAL
Push-Location $repositoryRoot
try {
    $gitStatusBeforeVerification = & git status --porcelain
    if ($LASTEXITCODE -ne 0) {
        throw '无法读取当前 Git 工作树状态'
    }
    if (-not [string]::IsNullOrWhiteSpace(
        ($gitStatusBeforeVerification -join [Environment]::NewLine)
    )) {
        throw '验收前工作树必须干净，请先提交测试、脚本和文档'
    }

    $serial = Find-AvdSerial -ExpectedAvdName $AvdName
    if ($null -eq $serial) {
        Start-Process `
            -FilePath $emulator `
            -ArgumentList @(
                '-avd', $AvdName,
                '-no-snapshot-save',
                '-no-boot-anim'
            ) `
            -WindowStyle Hidden | Out-Null
    }

    $bootDeadline = [DateTime]::UtcNow.AddMinutes(4)
    do {
        $serial = Find-AvdSerial -ExpectedAvdName $AvdName
        $bootCompleted = ''
        if ($null -ne $serial) {
            $bootCompleted = (
                & $adb -s $serial shell getprop sys.boot_completed 2>$null
            ).Trim()
        }
        if ($bootCompleted -eq '1') {
            break
        }
        Start-Sleep -Seconds 2
    } while ($bootCompleted -ne '1' -and [DateTime]::UtcNow -lt $bootDeadline)
    if ($bootCompleted -ne '1' -or $null -eq $serial) {
        throw "模拟器 $AvdName 在 4 分钟内未完成启动"
    }
    $env:ANDROID_SERIAL = $serial

    Invoke-Checked $gradle @(
        'testDebugUnitTest',
        'lintDebug',
        'assembleDebug',
        '--stacktrace'
    )
    Invoke-Checked $gradle @(
        'connectedDebugAndroidTest',
        '--stacktrace'
    )
    Invoke-Checked $gradle @(
        'connectedDebugAndroidTest',
        '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.RealServerSmokeTest',
        "-Pandroid.testInstrumentationRunnerArguments.realServerBaseUrl=$RealServerBaseUrl",
        '--stacktrace'
    )

    $apk = Join-Path $repositoryRoot 'app\build\outputs\apk\debug\app-debug.apk'
    Invoke-Checked $adb @('-s', $serial, 'install', '-r', $apk)
    Invoke-Checked $adb @(
        '-s',
        $serial,
        'shell',
        'am',
        'force-stop',
        'com.local.mediaviewer'
    )
    Invoke-Checked $adb @(
        '-s',
        $serial,
        'shell',
        'am',
        'start',
        '-W',
        '-n',
        'com.local.mediaviewer/.MainActivity'
    )
    $pidValue = (
        & $adb -s $serial shell pidof com.local.mediaviewer
    ).Trim()
    if ([string]::IsNullOrWhiteSpace($pidValue)) {
        throw 'APK 已安装，但 com.local.mediaviewer 未保持运行'
    }

    foreach ($rootPath in @('/middle/', '/pik/')) {
        $response = Invoke-WebRequest `
            -Uri ($RealServerBaseUrl.TrimEnd('/') + $rootPath) `
            -Headers @{ Accept = 'application/json' } `
            -TimeoutSec 15
        if ($response.StatusCode -ne 200) {
            throw "$rootPath 返回 HTTP $($response.StatusCode)"
        }
        $content = [string]$response.Content
        try {
            $null = ConvertFrom-Json -InputObject $content
        } catch {
            throw "$rootPath 未返回合法 JSON"
        }
    }

    $delivery = & (Join-Path $PSScriptRoot 'Write-ApkChecksum.ps1')
    $apiLevel = (
        & $adb -s $serial shell getprop ro.build.version.sdk
    ).Trim()
    $abi = (
        & $adb -s $serial shell getprop ro.product.cpu.abi
    ).Trim()
    if ($apiLevel -ne '36') {
        throw "验收设备 API 必须为 36，实际为 $apiLevel"
    }
    if ($abi -ne 'x86_64') {
        throw "验收设备 ABI 必须为 x86_64，实际为 $abi"
    }
    $revision = (& git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw '无法读取当前 Git 修订号'
    }
    $completedAt = [DateTimeOffset]::Now.ToString('yyyy-MM-dd HH:mm:ss zzz')
    $verificationDirectory = Join-Path $repositoryRoot 'docs\verification'
    $verificationPath = Join-Path $verificationDirectory '2026-07-28-android-mediaviewer.md'
    New-Item -ItemType Directory -Path $verificationDirectory -Force | Out-Null
    $record = @"
# mediaviewer Android 验收记录

- 完成时间：$completedAt
- Git 修订：$revision
- AVD：$AvdName
- Android API：$apiLevel
- ABI：$abi
- 真实服务器：$RealServerBaseUrl
- 应用进程 PID：$pidValue
- APK：dist/mediaviewer-debug.apk
- SHA-256：$($delivery.Sha256)

## 自动门禁

- JVM 单元测试：通过
- Robolectric API 29：通过
- Android Lint：0 error
- Debug APK 构建：通过
- Compose 全导航：通过
- PNG/WAV/MP4 自生成夹具：通过
- HTTP Range 206：通过
- LibVLC 视频、音频与 seek：通过
- 横屏旋转：通过
- API 36 x86_64 安装与启动：通过

## 真实服务器

- /middle/：HTTP 200，Caddy JSON 可解析，应用内解析通过
- /pik/：HTTP 200，Caddy JSON 可解析，应用内解析通过

验收过程未读取媒体正文，未在日志或本记录中写入真实目录条目名称。
"@
    $utf8NoBom = [Text.UTF8Encoding]::new($false)
    [IO.File]::WriteAllText($verificationPath, $record, $utf8NoBom)

    Write-Host "验收通过：$verificationPath"
    Write-Host "APK：$($delivery.Apk)"
    Write-Host "SHA-256：$($delivery.Sha256)"
} finally {
    if ($hadAndroidSerial) {
        $env:ANDROID_SERIAL = $previousAndroidSerial
    } else {
        Remove-Item Env:ANDROID_SERIAL -ErrorAction SilentlyContinue
    }
    Pop-Location
}
```

脚本只复用名称与 `AvdName` 完全一致的在线模拟器；否则启动该 AVD，并通过
`ANDROID_SERIAL` 将 Gradle 和所有 adb 操作限定到它。脚本不关闭用户原先已运行的模拟器。

- [ ] **Step 15: 编写中文 README**

`README.md` 必须包含以下完整内容，版本号与命令保持一致：

````markdown
# mediaviewer

`mediaviewer` 是一个 Android 10 及以上的私有媒体浏览器。它通过 HTTP
读取 Caddy 目录 JSON，并使用 HTTP Range 随机读取原始视频、音频和图片。

## 已实现能力

- 固定入口：`MiddleDir`（`/middle/`）和 `pik`（`/pik/`）
- 支持嵌套目录、IPv4 字面地址、DNS A 记录、私网 IPv4 和公网 IPv4
- 视频、音频和未知文件由内嵌 LibVLC 尝试播放
- 图片支持双指缩放、拖动和双击复位
- 播放位置每 5 秒及暂停、退出、后台时保存
- 不足 10 秒不恢复，完成或达到 95% 时清除进度
- 图片只使用内存缓存，不写 Coil 磁盘缓存

## 默认服务器

默认地址是 `http://192.168.1.17:8080`。设置页只接受 HTTP 根地址，并且
只有 `/middle/` 与 `/pik/` 都能返回合法 Caddy JSON 时才允许保存。

应用支持 DNS 主机名。每次启动及首次连接失败后重新解析 IPv4 A 记录，
按系统返回顺序探测，选择第一个两个根目录都可用的 IPv4。保存的仍是原始
逻辑域名，播放进度不会绑定某个临时 IPv4。IPv6 不参与探测。

## 构建环境

- JDK 21
- Android SDK 36
- Android Build Tools 36.0.0
- Gradle 9.5.0 Wrapper

在 PowerShell 中构建：

```powershell
$env:ANDROID_HOME = 'C:\Users\Administrator\AppData\Local\Android\Sdk'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

## 模拟器验收

```powershell
.\scripts\Invoke-AndroidVerification.ps1 `
  -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk' `
  -AvdName 'Pixel_3a_API_36_extension_level_17_x86_64' `
  -RealServerBaseUrl 'http://192.168.1.17:8080'
```

验收脚本会生成 `dist/mediaviewer-debug.apk`、
`dist/mediaviewer-debug.apk.sha256` 与
`docs/verification/2026-07-28-android-mediaviewer.md`。

## 安装

```powershell
& 'C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe' `
  install -r .\dist\mediaviewer-debug.apk
```

也可以把 APK 复制到 Android 设备后手工安装。系统询问时允许从当前文件
管理器安装未知应用。

## 使用

1. 确认 Android 设备能够访问服务器的 HTTP 端口。
2. 启动 `mediaviewer`，等待首页显示当前 IPv4。
3. 点击 `MiddleDir` 或 `pik` 浏览目录。
4. 点击文件播放或查看；视频页可拖动进度条并切换全屏。
5. 服务器地址变化时进入设置，输入 HTTP 根地址，点击“测试连接”，成功后保存。

## 常见问题

- “域名没有可用的 IPv4”：确认 DNS 存在 A 记录；AAAA 记录不会被使用。
- “两个媒体目录未同时通过”：确认 `/middle/` 与 `/pik/` 都开启 Caddy
  文件浏览，并且带 `Accept: application/json` 时返回 JSON。
- “无法连接服务器”：确认 Windows 防火墙允许服务端口，手机与服务器路由可达，
  公网使用时确认端口转发和 DDNS 指向当前公网 IPv4。
- “媒体无法播放”：LibVLC 会尝试识别未知文件，但损坏文件或不支持的编码仍会失败。
- “没有断点续播”：少于 10 秒不恢复；播放达到 95% 或自然结束后会清除记录。

## 明确不支持

HTTPS、身份认证、后台播放、画中画、投屏、Android TV、下载、离线媒体缓存、
播放列表、缩略图和服务端写操作不在本应用范围内。

第三方组件与许可见 `THIRD_PARTY_NOTICES.md`。
````

外层实施文档与内层 README 都使用代码围栏，因此落地 `README.md` 时去掉最外层围栏，保留其中 PowerShell 围栏。

- [ ] **Step 16: 编写第三方许可说明**

`THIRD_PARTY_NOTICES.md`：

```markdown
# 第三方组件与许可

`mediaviewer` 使用以下主要开源组件。各组件版权归原作者所有；本文件不替代
组件仓库中的完整许可文本。

| 组件 | 固定版本 | 许可 |
| --- | --- | --- |
| AndroidX Core、Activity、Lifecycle、Navigation、Room、DataStore、Compose、Test | 见 `gradle/libs.versions.toml` | Apache License 2.0 |
| Kotlin、Kotlin Coroutines、Kotlin Serialization、KSP | 见 `gradle/libs.versions.toml` | Apache License 2.0 |
| OkHttp、Okio、MockWebServer | 5.3.0 | Apache License 2.0 |
| Coil | 3.5.0 | Apache License 2.0 |
| LibVLC Android | 4.0.0-eap29 | GNU LGPL 2.1 或更高版本 |
| JUnit 4 | 4.13.2 | Eclipse Public License 1.0 |
| Robolectric | 4.16.1 | MIT License |

项目与许可原文：

- AndroidX：https://source.android.com/docs/setup/about/licenses
- Kotlin：https://github.com/JetBrains/kotlin
- OkHttp：https://github.com/square/okhttp
- Coil：https://github.com/coil-kt/coil
- LibVLC：https://code.videolan.org/videolan/vlc-android
- GNU LGPL 2.1：https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
- JUnit 4：https://github.com/junit-team/junit4
- Robolectric：https://github.com/robolectric/robolectric

Debug APK 动态/静态包含方式以 Gradle 最终依赖报告为准。发布或重新分发 APK 前，
应同时保留本说明，并遵守 LibVLC/VLC 对应版本附带的完整 LGPL 通知与源码获取要求。
```

Run:

```powershell
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath
```

检查输出只含已计划的运行时库；MockWebServer、JUnit、Robolectric 和 Compose 测试库不得出现在 `debugRuntimeClasspath`。

- [ ] **Step 17: 执行提交前自动门禁**

Run:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

Expected:

```text
All JVM and Robolectric tests pass
Lint reports 0 errors
All instrumented tests pass on API 36 x86_64
```

- [ ] **Step 18: 提交测试、脚本和交付文档**

```powershell
git add app/src/androidTest `
  scripts `
  README.md `
  THIRD_PARTY_NOTICES.md
git status --short
git commit -m "test: add Android end-to-end verification"
```

提交前确认 `git status --short` 只列出上述 Task 13 文件，且不列出：

```text
dist/mediaviewer-debug.apk
dist/mediaviewer-debug.apk.sha256
```

- [ ] **Step 19: 在干净提交上执行统一验收并检查 APK 范围**

Run:

```powershell
.\scripts\Invoke-AndroidVerification.ps1 `
  -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk' `
  -AvdName 'Pixel_3a_API_36_extension_level_17_x86_64' `
  -RealServerBaseUrl 'http://192.168.1.17:8080'
& 'C:\Users\Administrator\AppData\Local\Android\Sdk\build-tools\36.0.0\aapt.exe' `
  dump badging .\dist\mediaviewer-debug.apk
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath
git status --short
```

Expected:

```text
package name is com.local.mediaviewer
application label is mediaviewer
sdkVersion is 29
targetSdkVersion is 36
LibVLC is present in debugRuntimeClasspath
MockWebServer is absent from debugRuntimeClasspath
dist files are ignored by Git
Only docs/verification/2026-07-28-android-mediaviewer.md is uncommitted
The verification record names the clean Task 13 implementation commit
```

在模拟器中人工确认：

1. 首页显示 `MiddleDir`、`pik` 和已连接 IPv4；
2. 两个真实根目录都能进入，空目录显示中文空态；
3. 任取一个真实视频只验证可打开、可拖动，不把名称写入验收记录；
4. 返回首页后进程仍运行；
5. 设置页保留逻辑服务器地址，而不是解析出的 IPv4。

- [ ] **Step 20: 提交实际验收记录**

```powershell
git add docs/verification/2026-07-28-android-mediaviewer.md
git status --short
git commit -m "docs: record Android mediaviewer acceptance"
```

提交后确认：

```text
git status --short produces no output
dist/mediaviewer-debug.apk exists and remains ignored
dist/mediaviewer-debug.apk.sha256 exists and remains ignored
```

最终向用户交付：

```text
dist/mediaviewer-debug.apk
dist/mediaviewer-debug.apk.sha256
README.md
docs/verification/2026-07-28-android-mediaviewer.md
```
