# GIF 动图浏览 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使用现有 Coil 图片链路让网络 GIF 在 Android 单图和条漫模式中按动画显示，同时保持现有手势、缓存和错误处理。

**Architecture:** 在版本目录中加入与 Coil 3.5.0 同版本的 `coil-gif`，并在唯一的 `MediaImageLoaderFactory` 中显式注册 `AnimatedImageDecoder.Factory`。单图与条漫继续使用现有 `SubcomposeAsyncImage`，因此不新增 GIF 专用页面或状态机。

**Tech Stack:** Android API 29-36、Kotlin 2.3.21、Coil 3.5.0、AnimatedImageDecoder、Jetpack Compose、MockWebServer 5.3.0、JUnit 4。

## Global Constraints

- `coil-gif` 必须与现有 Coil 版本 `3.5.0` 一致。
- 项目 `minSdk` 为 29，动画解码使用 `AnimatedImageDecoder.Factory`。
- GIF 必须走现有 `MediaImageLoaderFactory`、`ImageDecodePolicy`、内存缓存和请求代次。
- 单图左右滑、放大后平移、条漫缩放不重新加载和图片失败重试行为不得改变。
- 不增加 GIF 播放按钮、速度控制、强制循环次数、磁盘缓存或 VLC 图片路由。
- 只运行本计划新增并曾经失败的定向测试；不运行完整测试套件。

---

### Task 1: 添加并注册 Coil GIF 动画解码器

**Files:**
- Modify: `app/src/test/java/com/local/mediaviewer/image/MediaImageLoaderFactoryTest.kt`
- Modify: `gradle/libs.versions.toml:55-57`
- Modify: `app/build.gradle.kts:98-100`
- Modify: `app/src/main/java/com/local/mediaviewer/image/MediaImageLoaderFactory.kt:13-34`

**Interfaces:**
- Consumes: `MediaImageLoaderFactory.create(context: Context): ImageLoader`。
- Produces: `ImageLoader.components.decoderFactories` 包含 `coil3.gif.AnimatedImageDecoder.Factory`。

- [ ] **Step 1: 写加载器缺少 GIF 解码器的失败测试**

在 `MediaImageLoaderFactoryTest` 增加：

```kotlin
@Test
fun `ImageLoader 注册 GIF 动画解码器`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val loader = MediaImageLoaderFactory.create(context)
    try {
        assertTrue(
            loader.components.decoderFactories.any { factory ->
                factory.javaClass.name ==
                    "coil3.gif.AnimatedImageDecoder\$Factory"
            },
        )
    } finally {
        loader.shutdown()
    }
}
```

测试使用类名字符串，因此在尚未引入 `coil-gif` 时仍可编译并准确表达缺失组件。

- [ ] **Step 2: 运行新增测试并确认 RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests '*MediaImageLoaderFactoryTest*GIF 动画解码器*'
```

Expected: FAIL，`decoderFactories` 中不存在 `AnimatedImageDecoder.Factory`。

- [ ] **Step 3: 增加同版本依赖并显式注册解码器**

在 `gradle/libs.versions.toml` 的 Coil 库中增加：

```toml
coil-gif = { module = "io.coil-kt.coil3:coil-gif", version.ref = "coil" }
```

在 `app/build.gradle.kts` 增加：

```kotlin
implementation(libs.coil.gif)
```

在 `MediaImageLoaderFactory.kt` 增加导入：

```kotlin
import coil3.gif.AnimatedImageDecoder
```

并在现有组件注册块中把动画解码器放在网络 fetcher 前：

```kotlin
.components {
    add(AnimatedImageDecoder.Factory())
    add(
        OkHttpNetworkFetcherFactory(
            callFactory = { callFactory },
        ),
    )
}
```

显式注册避免依赖服务发现顺序，并利用项目 `minSdk = 29`，不增加旧系统分支。

- [ ] **Step 4: 只重跑刚才失败的加载器测试并确认 GREEN**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests '*MediaImageLoaderFactoryTest*GIF 动画解码器*'
```

Expected: PASS；现有内存缓存、禁用磁盘缓存和网络 fetcher 配置不变。

- [ ] **Step 5: 提交 GIF 解码能力**

```powershell
git add -- `
  gradle/libs.versions.toml `
  app/build.gradle.kts `
  app/src/main/java/com/local/mediaviewer/image/MediaImageLoaderFactory.kt `
  app/src/test/java/com/local/mediaviewer/image/MediaImageLoaderFactoryTest.kt
git commit -m "feat: add animated GIF decoding"
```

---

### Task 2: 用真实网络 GIF 验证动画结果

**Files:**
- Create: `app/src/androidTest/java/com/local/mediaviewer/GifImageLoaderInstrumentedTest.kt`

**Interfaces:**
- Consumes: `MediaImageLoaderFactory.create` 和 `MediaImageLoaderFactory.createRequest`。
- Produces: Android 设备上的网络 GIF 加载结果为实现 `android.graphics.drawable.Animatable` 的 Drawable，并可启动动画。

- [ ] **Step 1: 创建最小真实 GIF 网络测试**

创建 `GifImageLoaderInstrumentedTest.kt`：

```kotlin
package com.local.mediaviewer

import android.content.Context
import android.graphics.drawable.Animatable
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.asDrawable
import coil3.request.SuccessResult
import com.local.mediaviewer.image.MediaImageLoaderFactory
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GifImageLoaderInstrumentedTest {
    @Test
    fun networkGifDecodesAsRunningAnimation() = runBlocking {
        val context =
            ApplicationProvider.getApplicationContext<Context>()
        val server = MockWebServer()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "image/gif")
                .body(
                    Buffer().write(
                        Base64.decode(
                            TWO_FRAME_GIF_BASE64,
                            Base64.DEFAULT,
                        ),
                    ),
                )
                .build(),
        )
        server.start()
        val loader = MediaImageLoaderFactory.create(context)
        try {
            val result = loader.execute(
                MediaImageLoaderFactory.createRequest(
                    context = context,
                    url = server.url("/animated.gif").toString(),
                ),
            )
            assertTrue(result is SuccessResult)
            val drawable = (result as SuccessResult)
                .image
                .asDrawable(context.resources)
            assertTrue(drawable is Animatable)
            val animation = drawable as Animatable
            animation.start()
            assertTrue(animation.isRunning)
        } finally {
            loader.shutdown()
            server.close()
        }
    }

    private companion object {
        const val TWO_FRAME_GIF_BASE64 =
            "R0lGODlhAgACAIEAAP8AAAAAAAAAAAAAACH/C05FVFNDQVBFMi4w" +
                "AwEAAAAh+QQIDAAAACwAAAAAAgACAAAIBgABCAQQEAAh+QQIDAAA" +
                "ACwAAAAAAgACAIEAAP8AAAAAAAAAAAAIBgABCAQQEAA7"
    }
}
```

该 fixture 是 2×2、红蓝两帧、每帧 120 ms、循环播放的真实 GIF；测试不依赖共享媒体 fixture，避免改变现有目录内容和条漫计数。

- [ ] **Step 2: 编译 Android 测试源码**

Run:

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin
```

Expected: PASS，证明 `coil-gif`、`asDrawable`、MockWebServer 和动画接口在 Android 测试变体中可用。

- [ ] **Step 3: 在连接的 Android 设备上运行唯一新增测试**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.GifImageLoaderInstrumentedTest#networkGifDecodesAsRunningAnimation'
```

Expected: PASS，网络 GIF 解码为可运行的动画 Drawable。没有连接 Android 设备时，结果必须记录为 `BLOCKED_NOT_RUN_DYNAMIC`；编译成功不能替代动态动画验收。

- [ ] **Step 4: 提交真实 GIF 回归测试**

```powershell
git add -- `
  app/src/androidTest/java/com/local/mediaviewer/GifImageLoaderInstrumentedTest.kt
git commit -m "test: verify network GIF animation decoding"
```

---

### Task 3: 做本范围基础功能交付检查

**Files:**
- Verify only: `app/src/main/java/com/local/mediaviewer/ui/image/SingleImageViewer.kt`
- Verify only: `app/src/main/java/com/local/mediaviewer/ui/image/ComicReader.kt`
- Verify only: `app/src/main/java/com/local/mediaviewer/image/MediaImageLoaderFactory.kt`

**Interfaces:**
- Consumes: Task 1 注册的全局 GIF 解码器和 Task 2 的动态证据。
- Produces: 单图与条漫继续共用同一个加载器，无 GIF 专用 UI 分叉。

- [ ] **Step 1: 静态确认两个阅读模式都使用共享加载器**

Run:

```powershell
rg -n "SubcomposeAsyncImage|imageLoader = imageLoader|MediaImageLoaderFactory.createRequest" `
  app/src/main/java/com/local/mediaviewer/ui/image/SingleImageViewer.kt `
  app/src/main/java/com/local/mediaviewer/ui/image/ComicReader.kt
```

Expected: 两个模式都把共享 `imageLoader` 传给 `SubcomposeAsyncImage`，并继续通过 `MediaImageLoaderFactory.createRequest` 构造请求；无需修改两个 UI 文件。

- [ ] **Step 2: 只重跑本计划曾经失败的加载器测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests '*MediaImageLoaderFactoryTest*GIF 动画解码器*'
```

Expected: PASS。

- [ ] **Step 3: 检查工作树与提交边界**

Run:

```powershell
git status --short
git log --oneline -4
```

Expected: 只有用户原有未跟踪文件；本计划的生产改动和测试分别存在于独立提交中。若设备测试未运行，最终报告必须明确列出动态 GIF 播放仍待设备验收。
