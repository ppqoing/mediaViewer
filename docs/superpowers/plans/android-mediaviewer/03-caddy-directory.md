# Caddy 目录客户端 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 Caddy 目录 JSON 严格转换为稳定排序的领域条目，同时分别保留逻辑 URL 与当前 IPv4 请求 URL。

**Architecture:** `DirectoryJsonParser` 只处理 JSON、相对 URI、媒体分类和排序；`DefaultCaddyDirectoryClient` 只处理 HTTP 和错误映射。所有 URI 都通过 OkHttp `HttpUrl.resolve` 解析服务端 `url`，不拼接文件名、不重复编码。

**Tech Stack:** OkHttp 5.3.0、Kotlin Serialization JSON 1.11.0、MockWebServer 5.3.0、`java.time.Instant`。

## Global Constraints

- 目录请求必须发送 `Accept: application/json`。
- Caddy 字段为 `name`、`size`、`url`、`mod_time`、`mode`、`is_dir`、`is_symlink`。
- 普通目录连接超时 5 秒、读取超时 15 秒。
- 文件夹排在文件前，再按名称不区分大小写稳定排序。
- 不隐藏未知扩展名；未知文件标记为 `UNKNOWN`。
- 不读取或记录错误响应正文。
- Unicode、空格、括号、emoji 和已经百分号编码的 URL 不得重复编码。

---

### Task 3: JSON 解析、分类、排序和 HTTP 目录读取

**Files:**

- Create: `app/src/main/java/com/local/mediaviewer/model/MediaKind.kt`
- Create: `app/src/main/java/com/local/mediaviewer/model/DirectoryEntry.kt`
- Create: `app/src/main/java/com/local/mediaviewer/network/CaddyEntryDto.kt`
- Create: `app/src/main/java/com/local/mediaviewer/network/MediaClassifier.kt`
- Create: `app/src/main/java/com/local/mediaviewer/network/DirectoryJsonParser.kt`
- Create: `app/src/main/java/com/local/mediaviewer/network/CaddyDirectoryClient.kt`
- Test: `app/src/test/java/com/local/mediaviewer/network/MediaClassifierTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/network/DirectoryJsonParserTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/network/CaddyDirectoryClientTest.kt`

**Interfaces:**

- Consumes:

```kotlin
sealed interface AppResult<out T>
sealed interface AppError
interface DispatcherProvider
```

- Produces:

```kotlin
@Serializable
enum class MediaKind { DIRECTORY, VIDEO, AUDIO, IMAGE, UNKNOWN }

data class DirectoryEntry(
    val name: String,
    val size: Long,
    val modifiedAt: Instant,
    val mode: Long,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val logicalUrl: String,
    val requestUrl: String,
    val kind: MediaKind,
)

interface DirectoryJsonParser {
    fun parse(
        json: String,
        logicalDirectoryUrl: String,
        requestDirectoryUrl: String,
    ): AppResult<List<DirectoryEntry>>
}

interface CaddyDirectoryClient {
    suspend fun listDirectory(
        logicalDirectoryUrl: String,
        requestDirectoryUrl: String,
    ): AppResult<List<DirectoryEntry>>
}
```

- [ ] **Step 1: 写媒体分类与 JSON 失败测试**

`MediaClassifierTest.kt`：

```kotlin
package com.local.mediaviewer.network

import com.local.mediaviewer.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaClassifierTest {
    @Test
    fun `扩展名不区分大小写且未知文件不隐藏`() {
        assertEquals(MediaKind.VIDEO, MediaClassifier.classify("电影.MKV", false))
        assertEquals(MediaKind.AUDIO, MediaClassifier.classify("音轨.FlAc", false))
        assertEquals(MediaKind.IMAGE, MediaClassifier.classify("海报.WeBp", false))
        assertEquals(MediaKind.UNKNOWN, MediaClassifier.classify("archive.bin", false))
        assertEquals(MediaKind.DIRECTORY, MediaClassifier.classify("folder.mp4", true))
    }
}
```

`DirectoryJsonParserTest.kt`：

```kotlin
package com.local.mediaviewer.network

import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryJsonParserTest {
    private val parser = DefaultDirectoryJsonParser()

    @Test
    fun `解析字段并将文件夹稳定排在文件之前`() {
        val json = """
            [
              {"name":"z.MP4","size":8,"url":"z.MP4","mod_time":"2026-07-28T01:02:03Z","mode":420,"is_dir":false,"is_symlink":false},
              {"name":"子目录","size":0,"url":"%E5%AD%90%E7%9B%AE%E5%BD%95/","mod_time":"2026-07-28T01:02:03Z","mode":493,"is_dir":true,"is_symlink":false},
              {"name":"A.mp3","size":4,"url":"A.mp3","mod_time":"2026-07-28T01:02:03Z","mode":420,"is_dir":false,"is_symlink":false}
            ]
        """.trimIndent()

        val entries = (parser.parse(
            json,
            "http://media.example:8080/middle/",
            "http://203.0.113.8:8080/middle/",
        ) as AppResult.Success).value

        assertEquals(listOf("子目录", "A.mp3", "z.MP4"), entries.map { it.name })
        assertEquals(MediaKind.DIRECTORY, entries[0].kind)
        assertEquals(
            "http://media.example:8080/middle/%E5%AD%90%E7%9B%AE%E5%BD%95/",
            entries[0].logicalUrl,
        )
        assertFalse(entries[0].logicalUrl.contains("%25E5"))
    }

    @Test
    fun `Unicode 原文只编码一次且逻辑与请求主机分离`() {
        val json = """
            [{"name":"動画 (1) 😀.mp4","size":8,"url":"動画 (1) 😀.mp4","mod_time":"2026-07-28T01:02:03Z","mode":420,"is_dir":false,"is_symlink":false}]
        """.trimIndent()
        val entry = (parser.parse(
            json,
            "http://media.example:8080/pik/",
            "http://198.51.100.7:8080/pik/",
        ) as AppResult.Success).value.single()

        assertTrue(entry.logicalUrl.startsWith("http://media.example:8080/"))
        assertTrue(entry.requestUrl.startsWith("http://198.51.100.7:8080/"))
        assertTrue(entry.logicalUrl.contains("%E5%8B%95%E7%94%BB%20%281%29%20%F0%9F%98%80.mp4"))
    }

    @Test
    fun `空数组成功而缺字段和无效时间失败`() {
        assertEquals(
            emptyList<Any>(),
            (parser.parse(
                "[]",
                "http://media.example/middle/",
                "http://192.0.2.1/middle/",
            ) as AppResult.Success).value,
        )
        assertTrue(
            parser.parse(
                """[{"name":"x"}]""",
                "http://media.example/middle/",
                "http://192.0.2.1/middle/",
            ) is AppResult.Failure,
        )
    }
}
```

- [ ] **Step 2: 运行解析测试并观察预期失败**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.network.MediaClassifierTest' `
  --tests 'com.local.mediaviewer.network.DirectoryJsonParserTest'
```

Expected:

```text
Kotlin compilation fails because parser, classifier, and models are unresolved
```

- [ ] **Step 3: 实现领域模型和媒体分类**

`MediaKind.kt`：

```kotlin
package com.local.mediaviewer.model

import kotlinx.serialization.Serializable

@Serializable
enum class MediaKind {
    DIRECTORY,
    VIDEO,
    AUDIO,
    IMAGE,
    UNKNOWN,
}
```

`DirectoryEntry.kt`：

```kotlin
package com.local.mediaviewer.model

import java.time.Instant

data class DirectoryEntry(
    val name: String,
    val size: Long,
    val modifiedAt: Instant,
    val mode: Long,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val logicalUrl: String,
    val requestUrl: String,
    val kind: MediaKind,
)
```

`MediaClassifier.kt`：

```kotlin
package com.local.mediaviewer.network

import com.local.mediaviewer.model.MediaKind
import java.util.Locale

object MediaClassifier {
    private val videos = setOf("mp4", "mkv", "avi", "mov", "webm", "m4v", "ts", "m2ts", "wmv", "flv")
    private val audio = setOf("mp3", "flac", "aac", "m4a", "ogg", "opus", "wav", "wma", "ape")
    private val images = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "heic", "heif")

    fun classify(name: String, isDirectory: Boolean): MediaKind {
        if (isDirectory) return MediaKind.DIRECTORY
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (extension) {
            in videos -> MediaKind.VIDEO
            in audio -> MediaKind.AUDIO
            in images -> MediaKind.IMAGE
            else -> MediaKind.UNKNOWN
        }
    }
}
```

- [ ] **Step 4: 实现严格 JSON 和 URI 解析**

`CaddyEntryDto.kt`：

```kotlin
package com.local.mediaviewer.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CaddyEntryDto(
    val name: String,
    val size: Long,
    val url: String,
    @SerialName("mod_time") val modifiedAt: String,
    val mode: Long,
    @SerialName("is_dir") val isDirectory: Boolean,
    @SerialName("is_symlink") val isSymlink: Boolean,
)
```

`DirectoryJsonParser.kt`：

```kotlin
package com.local.mediaviewer.network

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.DirectoryEntry
import java.time.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl

interface DirectoryJsonParser {
    fun parse(
        json: String,
        logicalDirectoryUrl: String,
        requestDirectoryUrl: String,
    ): AppResult<List<DirectoryEntry>>
}

class DefaultDirectoryJsonParser(
    private val jsonCodec: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
        explicitNulls = true
    },
) : DirectoryJsonParser {
    override fun parse(
        json: String,
        logicalDirectoryUrl: String,
        requestDirectoryUrl: String,
    ): AppResult<List<DirectoryEntry>> = try {
        val logicalBase = logicalDirectoryUrl.toHttpUrl()
        val requestBase = requestDirectoryUrl.toHttpUrl()
        val entries = jsonCodec.decodeFromString<List<CaddyEntryDto>>(json)
            .map { dto ->
                val logical = logicalBase.resolve(dto.url)
                    ?: throw IllegalArgumentException("invalid logical relative URL")
                val request = requestBase.resolve(dto.url)
                    ?: throw IllegalArgumentException("invalid request relative URL")
                DirectoryEntry(
                    name = dto.name,
                    size = dto.size,
                    modifiedAt = Instant.parse(dto.modifiedAt),
                    mode = dto.mode,
                    isDirectory = dto.isDirectory,
                    isSymlink = dto.isSymlink,
                    logicalUrl = logical.toString(),
                    requestUrl = request.toString(),
                    kind = MediaClassifier.classify(dto.name, dto.isDirectory),
                )
            }
            .sortedWith(
                compareByDescending<DirectoryEntry> { it.isDirectory }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )
        AppResult.Success(entries)
    } catch (_: SerializationException) {
        AppResult.Failure(AppError.InvalidDirectoryResponse)
    } catch (_: IllegalArgumentException) {
        AppResult.Failure(AppError.InvalidDirectoryResponse)
    } catch (_: java.time.DateTimeException) {
        AppResult.Failure(AppError.InvalidDirectoryResponse)
    }
}
```

- [ ] **Step 5: 运行解析测试并确认通过**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.network.MediaClassifierTest' `
  --tests 'com.local.mediaviewer.network.DirectoryJsonParserTest'
```

Expected:

```text
Both test classes pass
```

- [ ] **Step 6: 写目录 HTTP 集成失败测试**

`CaddyDirectoryClientTest.kt`：

```kotlin
package com.local.mediaviewer.network

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.core.DispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CaddyDirectoryClientTest {
    private lateinit var server: MockWebServer

    private val dispatchers = object : DispatcherProvider {
        override val io = Dispatchers.Unconfined
        override val default = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }

    @Before fun start() {
        server = MockWebServer()
        server.start()
    }

    @After fun stop() = server.close()

    @Test
    fun `发送 JSON Accept 并解析 200`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "application/json")
                .body("[]")
                .build(),
        )
        val client = DefaultCaddyDirectoryClient(
            client = OkHttpClient(),
            parser = DefaultDirectoryJsonParser(),
            dispatchers = dispatchers,
        )

        val result = client.listDirectory(
            logicalDirectoryUrl = "http://media.example/middle/",
            requestDirectoryUrl = server.url("/middle/").toString(),
        )

        assertEquals(emptyList<Any>(), (result as AppResult.Success).value)
        assertEquals("application/json", server.takeRequest().headers["Accept"])
    }

    @Test
    fun `HTTP 错误不解析正文`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(403)
                .body("""[{"not":"a directory"}]""")
                .build(),
        )
        val client = DefaultCaddyDirectoryClient(
            OkHttpClient(),
            DefaultDirectoryJsonParser(),
            dispatchers,
        )

        val result = client.listDirectory(
            "http://media.example/middle/",
            server.url("/middle/").toString(),
        )

        assertTrue(result is AppResult.Failure)
        assertEquals(403, ((result as AppResult.Failure).error as AppError.HttpFailure).statusCode)
    }
}
```

- [ ] **Step 7: 运行客户端测试并观察预期失败**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.network.CaddyDirectoryClientTest'
```

Expected:

```text
Kotlin compilation fails because CaddyDirectoryClient implementations are unresolved
```

- [ ] **Step 8: 实现带固定超时的目录客户端**

`CaddyDirectoryClient.kt`：

```kotlin
package com.local.mediaviewer.network

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.core.DefaultDispatcherProvider
import com.local.mediaviewer.core.DispatcherProvider
import com.local.mediaviewer.model.DirectoryEntry
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

interface CaddyDirectoryClient {
    suspend fun listDirectory(
        logicalDirectoryUrl: String,
        requestDirectoryUrl: String,
    ): AppResult<List<DirectoryEntry>>
}

class DefaultCaddyDirectoryClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val parser: DirectoryJsonParser = DefaultDirectoryJsonParser(),
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) : CaddyDirectoryClient {
    override suspend fun listDirectory(
        logicalDirectoryUrl: String,
        requestDirectoryUrl: String,
    ): AppResult<List<DirectoryEntry>> = withContext(dispatchers.io) {
        val request = Request.Builder()
            .url(requestDirectoryUrl)
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AppResult.Failure(AppError.HttpFailure(response.code))
                }
                val body = response.body.string()
                parser.parse(body, logicalDirectoryUrl, requestDirectoryUrl)
            }
        } catch (error: IOException) {
            AppResult.Failure(
                AppError.NetworkFailure(error.javaClass.simpleName),
            )
        } catch (error: IllegalArgumentException) {
            AppResult.Failure(
                AppError.NetworkFailure(error.javaClass.simpleName),
            )
        }
    }
}
```

- [ ] **Step 9: 运行本任务门禁**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.network.MediaClassifierTest' `
  --tests 'com.local.mediaviewer.network.DirectoryJsonParserTest' `
  --tests 'com.local.mediaviewer.network.CaddyDirectoryClientTest'
.\gradlew.bat lintDebug
```

Expected:

```text
All Caddy and classifier tests pass
Lint reports 0 errors
```

- [ ] **Step 10: 提交**

```powershell
git add app/src/main/java/com/local/mediaviewer/model `
  app/src/main/java/com/local/mediaviewer/network `
  app/src/test/java/com/local/mediaviewer/network
git commit -m "feat: browse Caddy JSON directories"
```
