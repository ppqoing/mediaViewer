# mediaviewer 工程基线 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立可由干净检出构建、测试、安装的单模块 Android 36 Compose 工程，并固定任务 02 至 13 共用的核心结果类型与两个根入口。

**Architecture:** 使用 AGP 9.3 的内建 Kotlin，不应用 `org.jetbrains.kotlin.android`；Compose、Serialization 与 KSP 使用独立插件。工程从第一项起同时具备 JVM、Robolectric、Compose 仪器测试、Lint 和 Debug APK 构建能力。

**Tech Stack:** AGP 9.3.0、Gradle 9.5.0、Kotlin 2.3.21、KSP 2.3.10、Compose BOM 2026.06.00、JDK 21、Android SDK 36。

## Global Constraints

- 应用名为 `mediaviewer`，应用 ID 为 `com.local.mediaviewer`。
- `minSdk = 29`、`compileSdk = 36`、`targetSdk = 36`。
- 默认服务器为 `http://192.168.1.17:8080`。
- 只实现单一 Android 应用模块与手工依赖装配。
- Manifest 显式允许 cleartext HTTP，并声明 `INTERNET` 权限。
- 用户可见文案使用简体中文。
- Debug APK 使用默认 Android Debug keystore，不创建发布密钥。

---

### Task 1: 建立工程、核心契约与冒烟测试

**Files:**

- Modify: `.gitignore`
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/network_security_config.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/java/com/local/mediaviewer/MediaViewerApplication.kt`
- Create: `app/src/main/java/com/local/mediaviewer/MainActivity.kt`
- Create: `app/src/main/java/com/local/mediaviewer/core/AppResult.kt`
- Create: `app/src/main/java/com/local/mediaviewer/core/AppError.kt`
- Create: `app/src/main/java/com/local/mediaviewer/core/DispatcherProvider.kt`
- Create: `app/src/main/java/com/local/mediaviewer/model/RootShare.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/theme/Theme.kt`
- Test: `app/src/test/java/com/local/mediaviewer/model/RootShareTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/MinimumApiContractTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/AppLaunchTest.kt`

**Interfaces:**

- Consumes: 无；这是任务 02 至 13 的根依赖。
- Produces:

```kotlin
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

sealed interface AppError {
    val userMessage: String
}

interface DispatcherProvider {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
}

enum class RootShare(
    val id: String,
    val displayName: String,
    val path: String,
)
```

- [ ] **Step 1: 生成 Gradle 9.5.0 Wrapper**

在 PowerShell 中只把 Gradle ZIP 放入系统临时目录；工程内只保留 Wrapper 文件：

```powershell
$bootstrap = Join-Path $env:TEMP 'mediaviewer-gradle-bootstrap'
$zip = Join-Path $bootstrap 'gradle-9.5.0-bin.zip'
$expanded = Join-Path $bootstrap 'expanded'
New-Item -ItemType Directory -Force -Path $bootstrap, $expanded | Out-Null
Invoke-WebRequest `
  -Uri 'https://services.gradle.org/distributions/gradle-9.5.0-bin.zip' `
  -OutFile $zip
Expand-Archive -LiteralPath $zip -DestinationPath $expanded -Force
& (Join-Path $expanded 'gradle-9.5.0\bin\gradle.bat') `
  wrapper `
  --gradle-version 9.5.0 `
  --distribution-type bin
```

`gradle/wrapper/gradle-wrapper.properties` 必须包含：

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.0-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 2: 写入固定插件、仓库与版本目录**

`settings.gradle.kts`：

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "mediaviewer"
include(":app")
```

`build.gradle.kts`：

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
```

`gradle.properties`：

```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.configuration-cache=true
org.gradle.parallel=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

`gradle/libs.versions.toml` 使用以下完整版本集合：

```toml
[versions]
agp = "9.3.0"
kotlin = "2.3.21"
ksp = "2.3.10"
core = "1.19.0"
activity = "1.13.0"
lifecycle = "2.11.0"
navigation = "2.9.8"
datastore = "1.2.1"
room = "2.8.4"
composeBom = "2026.06.00"
okhttp = "5.3.0"
serialization = "1.11.0"
coroutines = "1.11.0"
coil = "3.5.0"
libvlc = "4.0.0-eap29"
junit4 = "4.13.2"
robolectric = "4.16.1"
androidxTest = "1.7.0"
androidxJunit = "1.3.0"
espresso = "3.7.0"
orchestrator = "1.6.1"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "core" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-process = { module = "androidx.lifecycle:lifecycle-process", version.ref = "lifecycle" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-foundation = { module = "androidx.compose.foundation:foundation" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-compose-material-icons = { module = "androidx.compose.material:material-icons-extended" }
androidx-compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
androidx-compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
okhttp-bom = { module = "com.squareup.okhttp3:okhttp-bom", version.ref = "okhttp" }
okhttp = { module = "com.squareup.okhttp3:okhttp" }
mockwebserver = { module = "com.squareup.okhttp3:mockwebserver3" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network-okhttp = { module = "io.coil-kt.coil3:coil-network-okhttp", version.ref = "coil" }
libvlc = { module = "org.videolan.android:libvlc-all", version.ref = "libvlc" }
junit4 = { module = "junit:junit", version.ref = "junit4" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-test-core = { module = "androidx.test:core-ktx", version.ref = "androidxTest" }
androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidxTest" }
androidx-test-rules = { module = "androidx.test:rules", version.ref = "androidxTest" }
androidx-test-junit = { module = "androidx.test.ext:junit-ktx", version.ref = "androidxJunit" }
androidx-test-espresso = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }
androidx-test-orchestrator = { module = "androidx.test:orchestrator", version.ref = "orchestrator" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 3: 创建应用模块和明文 HTTP 配置**

`app/build.gradle.kts`：

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.local.mediaviewer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.local.mediaviewer"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        resources.excludes += "DebugProbesKt.bin"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(platform(libs.okhttp.bom))
    testImplementation(platform(libs.okhttp.bom))
    androidTestImplementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.mockwebserver)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.libvlc)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestUtil(libs.androidx.test.orchestrator)
}
```

`app/src/main/AndroidManifest.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".MediaViewerApplication"
        android:allowBackup="false"
        android:label="@string/app_name"
        android:networkSecurityConfig="@xml/network_security_config"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoActionBar"
        android:usesCleartextTraffic="true">
        <activity
            android:name=".MainActivity"
            android:configChanges="keyboardHidden|orientation|screenSize"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/res/xml/network_security_config.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true" />
</network-security-config>
```

`strings.xml` 至少包含：

```xml
<resources>
    <string name="app_name">mediaviewer</string>
    <string name="loading">正在加载…</string>
    <string name="retry">重试</string>
</resources>
```

- [ ] **Step 4: 写第一个失败的领域契约测试**

`RootShareTest.kt`：

```kotlin
package com.local.mediaviewer.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RootShareTest {
    @Test
    fun `固定入口与服务路径保持设计顺序`() {
        assertEquals(
            listOf(
                Triple("middle", "MiddleDir", "/middle/"),
                Triple("pik", "pik", "/pik/"),
            ),
            RootShare.entries.map { Triple(it.id, it.displayName, it.path) },
        )
    }
}
```

`MinimumApiContractTest.kt`：

```kotlin
package com.local.mediaviewer

import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [29])
class MinimumApiContractTest {
    @Test
    fun `Robolectric 可在 API 29 装载应用资源`() {
        assertEquals("mediaviewer", BuildConfig.APPLICATION_ID.substringAfterLast('.'))
    }
}
```

- [ ] **Step 5: 运行测试并观察预期失败**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.model.RootShareTest' `
  --tests 'com.local.mediaviewer.MinimumApiContractTest'
```

Expected:

```text
Kotlin compilation fails because RootShare is unresolved
```

- [ ] **Step 6: 实现最小共享类型**

`RootShare.kt`：

```kotlin
package com.local.mediaviewer.model

enum class RootShare(
    val id: String,
    val displayName: String,
    val path: String,
) {
    MIDDLE("middle", "MiddleDir", "/middle/"),
    PIK("pik", "pik", "/pik/");

    companion object {
        fun fromId(id: String): RootShare =
            entries.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("未知根目录：$id")
    }
}
```

`AppResult.kt`：

```kotlin
package com.local.mediaviewer.core

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}
```

`AppError.kt`：

```kotlin
package com.local.mediaviewer.core

sealed interface AppError {
    val userMessage: String

    data class InvalidServerUrl(
        override val userMessage: String,
    ) : AppError

    data object NoIpv4Address : AppError {
        override val userMessage = "未解析到 IPv4"
    }

    data class DnsFailure(val detail: String) : AppError {
        override val userMessage = "DNS 解析失败：$detail"
    }

    data class NetworkFailure(val detail: String) : AppError {
        override val userMessage = "网络连接失败：$detail"
    }

    data class HttpFailure(val statusCode: Int) : AppError {
        override val userMessage = "服务器返回 HTTP $statusCode"
    }

    data object InvalidDirectoryResponse : AppError {
        override val userMessage = "目录响应格式无效"
    }

    data class PlaybackFailure(val detail: String) : AppError {
        override val userMessage = "播放失败：$detail"
    }

    data object ImageLoadFailure : AppError {
        override val userMessage = "图片加载失败"
    }
}
```

`DispatcherProvider.kt`：

```kotlin
package com.local.mediaviewer.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface DispatcherProvider {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
}

object DefaultDispatcherProvider : DispatcherProvider {
    override val io = Dispatchers.IO
    override val default = Dispatchers.Default
    override val main = Dispatchers.Main
}
```

- [ ] **Step 7: 创建最小可启动 Compose 应用**

`MediaViewerApplication.kt`：

```kotlin
package com.local.mediaviewer

import android.app.Application

class MediaViewerApplication : Application()
```

`MainActivity.kt`：

```kotlin
package com.local.mediaviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import com.local.mediaviewer.ui.theme.MediaViewerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MediaViewerTheme {
                Text("mediaviewer")
            }
        }
    }
}
```

`Theme.kt`：

```kotlin
package com.local.mediaviewer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme

@Composable
fun MediaViewerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
```

- [ ] **Step 8: 写启动仪器测试**

`AppLaunchTest.kt`：

```kotlin
package com.local.mediaviewer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class AppLaunchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appNameIsDisplayed() {
        composeRule.onNodeWithText("mediaviewer").assertIsDisplayed()
    }
}
```

- [ ] **Step 9: 运行本任务门禁**

Run:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
app\build\outputs\apk\debug\app-debug.apk exists
Lint reports 0 errors
```

若已有运行中的 API 36 模拟器，再运行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Expected:

```text
AppLaunchTest passes
```

- [ ] **Step 10: 更新忽略规则并提交**

`.gitignore` 最终包含：

```gitignore
.worktrees/
.gradle/
.idea/
local.properties
**/build/
dist/*.apk
dist/*.sha256
```

提交：

```powershell
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties `
  gradle gradlew gradlew.bat app
git commit -m "build: bootstrap Android mediaviewer app"
```
