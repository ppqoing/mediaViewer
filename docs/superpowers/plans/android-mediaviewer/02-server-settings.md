# 服务器设置与 URL 校验 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以 Preferences DataStore 持久化逻辑服务器地址，并用单一校验器严格接受设计允许的 HTTP 根地址。

**Architecture:** `ServerUrlValidator` 是无 Android 依赖的纯函数边界，负责解析、拒绝和规范化；Repository 只保存已经验证的逻辑 URL 与最近成功 IPv4。Repository 永远不把 DNS 解析出的请求地址覆盖到逻辑地址。

**Tech Stack:** Kotlin、OkHttp `HttpUrl`、AndroidX DataStore Preferences 1.2.1、Coroutines Test。

## Global Constraints

- 默认逻辑地址为 `http://192.168.1.17:8080`。
- 只允许 `http`；拒绝用户名、密码、查询串、片段和非根路径。
- 端口可省略，省略时使用 HTTP 80。
- 保存域名或 IPv4 的逻辑地址，不保存某次解析 IP 作为逻辑地址。
- 公网和私网 IPv4 均允许，不显示安全警告。
- 用户可见校验错误使用简体中文。

---

### Task 2: URL 契约与 DataStore Repository

**Files:**

- Create: `app/src/main/java/com/local/mediaviewer/model/ServerConfig.kt`
- Create: `app/src/main/java/com/local/mediaviewer/model/ValidatedServerUrl.kt`
- Create: `app/src/main/java/com/local/mediaviewer/settings/ServerUrlValidator.kt`
- Create: `app/src/main/java/com/local/mediaviewer/settings/ServerSettingsRepository.kt`
- Create: `app/src/main/java/com/local/mediaviewer/settings/DataStoreServerSettingsRepository.kt`
- Test: `app/src/test/java/com/local/mediaviewer/settings/ServerUrlValidatorTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/settings/DataStoreServerSettingsRepositoryTest.kt`

**Interfaces:**

- Consumes:

```kotlin
sealed interface AppResult<out T>
sealed interface AppError
```

- Produces:

```kotlin
data class ServerConfig(
    val logicalBaseUrl: String = DEFAULT_SERVER_URL,
    val lastSuccessfulIpv4: String? = null,
) {
    companion object {
        const val DEFAULT_SERVER_URL = "http://192.168.1.17:8080"
    }
}

data class ValidatedServerUrl(
    val logicalBaseUrl: String,
    val host: String,
    val port: Int,
    val isIpv4Literal: Boolean,
)

object ServerUrlValidator {
    fun validate(input: String): AppResult<ValidatedServerUrl>
}

interface ServerSettingsRepository {
    val config: Flow<ServerConfig>
    suspend fun current(): ServerConfig
    suspend fun save(config: ServerConfig)
}
```

- [ ] **Step 1: 写 URL 校验失败测试**

`ServerUrlValidatorTest.kt`：

```kotlin
package com.local.mediaviewer.settings

import com.local.mediaviewer.core.AppResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlValidatorTest {
    @Test
    fun `接受默认私网 IPv4`() {
        val result = ServerUrlValidator.validate(" http://192.168.1.17:8080/ ")
        val value = (result as AppResult.Success).value
        assertEquals("http://192.168.1.17:8080", value.logicalBaseUrl)
        assertEquals("192.168.1.17", value.host)
        assertEquals(8080, value.port)
        assertTrue(value.isIpv4Literal)
    }

    @Test
    fun `接受公网 IPv4 与 DNS A 记录主机名`() {
        val publicIp = (ServerUrlValidator.validate("http://8.8.8.8") as AppResult.Success).value
        val dns = (ServerUrlValidator.validate("http://Media.Example.COM:8090") as AppResult.Success).value
        assertEquals(80, publicIp.port)
        assertTrue(publicIp.isIpv4Literal)
        assertEquals("http://media.example.com:8090", dns.logicalBaseUrl)
        assertFalse(dns.isIpv4Literal)
    }

    @Test
    fun `拒绝设计之外的 URL 组成部分`() {
        val rejected = listOf(
            "https://example.com",
            "ftp://example.com",
            "http://user@example.com",
            "http://example.com/path",
            "http://example.com?x=1",
            "http://example.com#part",
            "example.com:8080",
            "http://999.1.1.1",
        )
        rejected.forEach { input ->
            assertTrue("$input should fail", ServerUrlValidator.validate(input) is AppResult.Failure)
        }
    }
}
```

- [ ] **Step 2: 运行测试并观察预期失败**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.settings.ServerUrlValidatorTest'
```

Expected:

```text
Kotlin compilation fails because ServerUrlValidator and model types are unresolved
```

- [ ] **Step 3: 实现模型和严格 URL 校验**

`ServerConfig.kt`：

```kotlin
package com.local.mediaviewer.model

data class ServerConfig(
    val logicalBaseUrl: String = DEFAULT_SERVER_URL,
    val lastSuccessfulIpv4: String? = null,
) {
    companion object {
        const val DEFAULT_SERVER_URL = "http://192.168.1.17:8080"
    }
}
```

`ValidatedServerUrl.kt`：

```kotlin
package com.local.mediaviewer.model

data class ValidatedServerUrl(
    val logicalBaseUrl: String,
    val host: String,
    val port: Int,
    val isIpv4Literal: Boolean,
)
```

`ServerUrlValidator.kt`：

```kotlin
package com.local.mediaviewer.settings

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.ValidatedServerUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ServerUrlValidator {
    fun validate(input: String): AppResult<ValidatedServerUrl> {
        val raw = input.trim()
        val url = raw.toHttpUrlOrNull()
            ?: return invalid("请输入完整地址，例如 http://192.168.1.17:8080")

        if (url.scheme != "http") return invalid("服务器地址只允许使用 http")
        if (url.encodedUsername.isNotEmpty() || url.encodedPassword.isNotEmpty()) {
            return invalid("服务器地址不能包含用户名或密码")
        }
        if (url.encodedPath != "/") return invalid("服务器地址必须是根地址，不能包含路径")
        if (url.query != null) return invalid("服务器地址不能包含查询参数")
        if (url.fragment != null) return invalid("服务器地址不能包含片段")

        val ipv4Like = url.host.all { it.isDigit() || it == '.' }
        val ipv4 = parseIpv4(url.host)
        if (ipv4Like && ipv4 == null) return invalid("IPv4 地址格式无效")

        return AppResult.Success(
            ValidatedServerUrl(
                logicalBaseUrl = url.toString().removeSuffix("/"),
                host = url.host,
                port = url.port,
                isIpv4Literal = ipv4 != null,
            ),
        )
    }

    internal fun parseIpv4(host: String): String? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        if (parts.any { it.isEmpty() || it.length > 3 || it.any(Char::isLetter) }) return null
        val octets = parts.map { it.toIntOrNull() ?: return null }
        if (octets.any { it !in 0..255 }) return null
        return octets.joinToString(".")
    }

    private fun invalid(message: String) =
        AppResult.Failure(AppError.InvalidServerUrl(message))
}
```

- [ ] **Step 4: 运行 URL 测试并确认通过**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.settings.ServerUrlValidatorTest'
```

Expected:

```text
ServerUrlValidatorTest passes
```

- [ ] **Step 5: 写 DataStore Repository 失败测试**

`DataStoreServerSettingsRepositoryTest.kt`：

```kotlin
package com.local.mediaviewer.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.local.mediaviewer.model.ServerConfig
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreServerSettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `首次读取返回默认地址并可保存逻辑地址与最近 IPv4`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val store = PreferenceDataStoreFactory.create(
            scope = TestScope(dispatcher),
            produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
        )
        val repository = DataStoreServerSettingsRepository(store)

        assertEquals(ServerConfig(), repository.current())

        val saved = ServerConfig(
            logicalBaseUrl = "http://media.example.com:8080",
            lastSuccessfulIpv4 = "203.0.113.8",
        )
        repository.save(saved)
        assertEquals(saved, repository.current())
    }
}
```

- [ ] **Step 6: 运行 Repository 测试并观察预期失败**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.settings.DataStoreServerSettingsRepositoryTest'
```

Expected:

```text
Kotlin compilation fails because DataStoreServerSettingsRepository is unresolved
```

- [ ] **Step 7: 实现 DataStore Repository**

`ServerSettingsRepository.kt`：

```kotlin
package com.local.mediaviewer.settings

import com.local.mediaviewer.model.ServerConfig
import kotlinx.coroutines.flow.Flow

interface ServerSettingsRepository {
    val config: Flow<ServerConfig>
    suspend fun current(): ServerConfig
    suspend fun save(config: ServerConfig)
}
```

`DataStoreServerSettingsRepository.kt`：

```kotlin
package com.local.mediaviewer.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.local.mediaviewer.model.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.serverSettingsDataStore by preferencesDataStore(name = "server_settings")

class DataStoreServerSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : ServerSettingsRepository {
    private object Keys {
        val logicalBaseUrl = stringPreferencesKey("logical_base_url")
        val lastSuccessfulIpv4 = stringPreferencesKey("last_successful_ipv4")
    }

    override val config: Flow<ServerConfig> = dataStore.data.map { preferences ->
        ServerConfig(
            logicalBaseUrl = preferences[Keys.logicalBaseUrl] ?: ServerConfig.DEFAULT_SERVER_URL,
            lastSuccessfulIpv4 = preferences[Keys.lastSuccessfulIpv4],
        )
    }

    override suspend fun current(): ServerConfig = config.first()

    override suspend fun save(config: ServerConfig) {
        dataStore.edit { preferences ->
            preferences[Keys.logicalBaseUrl] = config.logicalBaseUrl
            config.lastSuccessfulIpv4?.let {
                preferences[Keys.lastSuccessfulIpv4] = it
            } ?: preferences.remove(Keys.lastSuccessfulIpv4)
        }
    }
}
```

- [ ] **Step 8: 运行本任务门禁**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.settings.*'
.\gradlew.bat lintDebug
```

Expected:

```text
All settings tests pass
Lint reports 0 errors
```

- [ ] **Step 9: 提交**

```powershell
git add app/src/main/java/com/local/mediaviewer/model `
  app/src/main/java/com/local/mediaviewer/settings `
  app/src/test/java/com/local/mediaviewer/settings
git commit -m "feat: persist validated server settings"
```
