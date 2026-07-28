# 图片查看器 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 提供可加载原始 HTTP 图片、缩放、拖动、双击复位且不产生磁盘副本的独立查看器。

**Architecture:** AppContainer 共享一个明确关闭磁盘缓存的 `ImageLoader`；`ImageViewerViewModel` 仅管理请求 URL、重试代次和一次端点刷新。缩放使用纯 reducer 与 Compose graphics layer，不引入第三方手势库。

**Tech Stack:** Coil Compose 3.5.0、Coil OkHttp、Compose 手势、StateFlow。

## Global Constraints

- 图片直接从当前会话 HTTP URL 加载。
- 只允许进程内内存缓存；Coil `diskCache` 和请求 `diskCachePolicy` 均禁用。
- OkHttp 客户端不得配置 HTTP 磁盘 Cache。
- 支持双指缩放、拖动和双击复位。
- 显示加载进度、失败信息和人工重试。
- 首次加载失败重新解析端点并重试一次。
- 页面使用深色背景；离开后释放 Composable 持有的图像引用。

---

### Task 12: Coil 配置、图片会话与缩放页面

**Files:**

- Modify: `app/src/main/java/com/local/mediaviewer/app/AppContainer.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`
- Create: `app/src/main/java/com/local/mediaviewer/image/MediaImageLoaderFactory.kt`
- Create: `app/src/main/java/com/local/mediaviewer/image/ImageViewerViewModel.kt`
- Create: `app/src/main/java/com/local/mediaviewer/image/ZoomState.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/image/ImageViewerScreen.kt`
- Test: `app/src/test/java/com/local/mediaviewer/image/MediaImageLoaderFactoryTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/image/ImageViewerViewModelTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/image/ZoomStateTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/ImageViewerScreenTest.kt`

**Interfaces:**

- Consumes:

```kotlin
data class ImageRoute(
    val name: String,
    val logicalUrl: String,
    val requestUrl: String,
)

interface ServerSessionManager
```

- Produces:

```kotlin
data class ImageViewerUiState(
    val requestUrl: String,
    val requestGeneration: Int = 0,
    val isRefreshingEndpoint: Boolean = false,
    val errorMessage: String? = null,
)

data class ZoomTransform(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
)
```

- [ ] **Step 1: 写无磁盘缓存失败测试**

`MediaImageLoaderFactoryTest.kt`：

```kotlin
package com.local.mediaviewer.image

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.request.CachePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class MediaImageLoaderFactoryTest {
    @Test
    fun `ImageLoader 有内存缓存且明确禁用磁盘缓存`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = MediaImageLoaderFactory.create(context)

        assertNull(loader.diskCache)
        assertEquals(CachePolicy.DISABLED, loader.defaults.diskCachePolicy)
        requireNotNull(loader.memoryCache)

        loader.shutdown()
    }
}
```

- [ ] **Step 2: 运行缓存测试并观察预期失败**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.image.MediaImageLoaderFactoryTest'
```

Expected:

```text
Kotlin compilation fails because MediaImageLoaderFactory is unresolved
```

- [ ] **Step 3: 实现共享 ImageLoader**

`MediaImageLoaderFactory.kt`：

```kotlin
package com.local.mediaviewer.image

import android.content.Context
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import okhttp3.OkHttpClient

object MediaImageLoaderFactory {
    fun create(context: Context): ImageLoader {
        val callFactory = OkHttpClient.Builder()
            .cache(null)
            .build()
        return ImageLoader.Builder(context.applicationContext)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
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
}
```

- [ ] **Step 4: 写端点刷新状态失败测试**

`ImageViewerViewModelTest.kt`：

```kotlin
package com.local.mediaviewer.image

import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.network.ConnectionTestResult
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImageViewerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun before() = Dispatchers.setMain(dispatcher)
    @After fun after() = Dispatchers.resetMain()

    @Test
    fun `首次失败刷新端点而第二次失败只显示错误`() = runTest(dispatcher) {
        val session = ImageFakeSession(
            SessionEndpoint(
                "http://media.example:8080",
                "http://192.0.2.2:8080",
                "192.0.2.2",
            ),
        )
        val viewModel = ImageViewerViewModel(
            logicalUrl = "http://media.example:8080/pik/a.png",
            initialRequestUrl = "http://192.0.2.1:8080/pik/a.png",
            session = session,
        )

        viewModel.onLoadError()
        advanceUntilIdle()
        assertEquals(
            "http://192.0.2.2:8080/pik/a.png",
            viewModel.uiState.value.requestUrl,
        )
        assertEquals(1, session.refreshCalls)

        viewModel.onLoadError()
        advanceUntilIdle()
        assertEquals(1, session.refreshCalls)
        assertEquals("图片加载失败", viewModel.uiState.value.errorMessage)

        viewModel.retry()
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(2, viewModel.uiState.value.requestGeneration)
    }
}

private class ImageFakeSession(
    private val endpoint: SessionEndpoint,
) : ServerSessionManager {
    private val mutable = MutableStateFlow<ServerSessionState>(
        ServerSessionState.Connected(endpoint, listOf(endpoint.ipv4)),
    )
    override val state: StateFlow<ServerSessionState> = mutable
    var refreshCalls = 0
    override suspend fun connectSaved() = Unit
    override suspend fun testCandidate(input: String) =
        error("not used")
    override suspend fun saveCandidate(result: ConnectionTestResult) = Unit
    override suspend fun refreshAfterRequestFailure(): AppResult<SessionEndpoint> {
        refreshCalls += 1
        return AppResult.Success(endpoint)
    }
}
```

- [ ] **Step 5: 实现图片请求状态**

`ImageViewerViewModel.kt`：

```kotlin
package com.local.mediaviewer.image

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.session.ServerSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ImageViewerUiState(
    val requestUrl: String,
    val requestGeneration: Int = 0,
    val isRefreshingEndpoint: Boolean = false,
    val errorMessage: String? = null,
)

class ImageViewerViewModel(
    private val logicalUrl: String,
    initialRequestUrl: String,
    private val session: ServerSessionManager,
) : ViewModel() {
    private var endpointRetryUsed = false
    private val mutableUiState = MutableStateFlow(
        ImageViewerUiState(initialRequestUrl),
    )
    val uiState: StateFlow<ImageViewerUiState> = mutableUiState.asStateFlow()

    fun onLoadError() {
        if (endpointRetryUsed) {
            mutableUiState.value = mutableUiState.value.copy(
                errorMessage = "图片加载失败",
            )
            return
        }
        endpointRetryUsed = true
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(
                isRefreshingEndpoint = true,
            )
            when (val refreshed = session.refreshAfterRequestFailure()) {
                is AppResult.Success -> {
                    mutableUiState.value = mutableUiState.value.copy(
                        requestUrl = refreshed.value.requestUrlFor(logicalUrl),
                        requestGeneration =
                            mutableUiState.value.requestGeneration + 1,
                        isRefreshingEndpoint = false,
                        errorMessage = null,
                    )
                }
                is AppResult.Failure -> {
                    mutableUiState.value = mutableUiState.value.copy(
                        isRefreshingEndpoint = false,
                        errorMessage = refreshed.error.userMessage,
                    )
                }
            }
        }
    }

    fun retry() {
        mutableUiState.value = mutableUiState.value.copy(
            requestGeneration = mutableUiState.value.requestGeneration + 1,
            errorMessage = null,
        )
    }
}
```

- [ ] **Step 6: 写缩放 reducer 失败测试**

`ZoomStateTest.kt`：

```kotlin
package com.local.mediaviewer.image

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomStateTest {
    @Test
    fun `缩放限制在一到五倍且一倍时偏移归零`() {
        val zoomed = ZoomReducer.gesture(
            ZoomTransform(),
            zoomChange = 10f,
            panChange = Offset(20f, -10f),
        )
        assertEquals(5f, zoomed.scale)
        assertEquals(Offset(20f, -10f), zoomed.offset)

        val resetByZoom = ZoomReducer.gesture(
            zoomed,
            zoomChange = 0.01f,
            panChange = Offset(100f, 100f),
        )
        assertEquals(ZoomTransform(), resetByZoom)
        assertEquals(ZoomTransform(), ZoomReducer.reset())
    }
}
```

- [ ] **Step 7: 实现缩放模型**

`ZoomState.kt`：

```kotlin
package com.local.mediaviewer.image

import androidx.compose.ui.geometry.Offset

data class ZoomTransform(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
)

object ZoomReducer {
    fun gesture(
        current: ZoomTransform,
        zoomChange: Float,
        panChange: Offset,
    ): ZoomTransform {
        val scale = (current.scale * zoomChange).coerceIn(1f, 5f)
        if (scale == 1f) return ZoomTransform()
        return ZoomTransform(
            scale = scale,
            offset = current.offset + panChange,
        )
    }

    fun reset(): ZoomTransform = ZoomTransform()
}
```

- [ ] **Step 8: 实现深色图片页面**

`ImageViewerScreen.kt`：

```kotlin
package com.local.mediaviewer.ui.image

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.local.mediaviewer.image.ImageViewerUiState
import com.local.mediaviewer.image.ZoomReducer
import com.local.mediaviewer.image.ZoomTransform

@Composable
fun ImageViewerScreen(
    name: String,
    state: ImageViewerUiState,
    imageLoader: ImageLoader,
    onLoadError: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    var zoom by remember { mutableStateOf(ZoomTransform()) }
    val context = LocalContext.current
    val request = remember(
        state.requestUrl,
        state.requestGeneration,
    ) {
        ImageRequest.Builder(context)
            .data(state.requestUrl)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
        ) {
            SubcomposeAsyncImage(
                model = request,
                imageLoader = imageLoader,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("media_image")
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = ZoomReducer.gesture(
                                zoom,
                                gestureZoom,
                                pan,
                            )
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                zoom = ZoomReducer.reset()
                            },
                        )
                    }
                    .graphicsLayer {
                        scaleX = zoom.scale
                        scaleY = zoom.scale
                        translationX = zoom.offset.x
                        translationY = zoom.offset.y
                    },
                loading = {
                    CircularProgressIndicator(
                        Modifier.align(Alignment.Center),
                    )
                },
                error = {
                    LaunchedEffect(request) { onLoadError() }
                },
                success = {
                    SubcomposeAsyncImageContent()
                },
            )
            if (state.isRefreshingEndpoint) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            state.errorMessage?.let { message ->
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(message, color = Color.White)
                    Button(onClick = onRetry) { Text("重试") }
                }
            }
        }
    }
}
```

`SubcomposeAsyncImage` 销毁时会释放页面持有的 painter；共享 ImageLoader
只保留受限内存缓存。

- [ ] **Step 9: 接入 AppContainer**

在 `AppContainer` 接口追加：

```kotlin
val imageLoader: ImageLoader
```

在 `DefaultAppContainer` 追加：

```kotlin
override val imageLoader: ImageLoader by lazy {
    MediaImageLoaderFactory.create(context.applicationContext)
}
```

添加 import：

```kotlin
import coil3.ImageLoader
import com.local.mediaviewer.image.MediaImageLoaderFactory
```

- [ ] **Step 10: 用真实图片页替换 ImageRoute 外壳**

在 `MediaViewerApp.kt` 的 `composable<ImageRoute>` 中替换为：

```kotlin
composable<ImageRoute> { entry ->
    val route = entry.toRoute<ImageRoute>()
    val viewer: ImageViewerViewModel = viewModel(
        key = "image:${route.logicalUrl}",
        factory = viewModelFactory {
            initializer {
                ImageViewerViewModel(
                    route.logicalUrl,
                    route.requestUrl,
                    container.sessionManager,
                )
            }
        },
    )
    val state by viewer.uiState.collectAsStateWithLifecycle()
    ImageViewerScreen(
        name = route.name,
        state = state,
        imageLoader = container.imageLoader,
        onLoadError = viewer::onLoadError,
        onRetry = viewer::retry,
        onBack = navController::popBackStack,
    )
}
```

删除 ImageRoute 对 `MediaRouteShell` 的调用，保留 PlayerRoute 已完成的播放器页面。

- [ ] **Step 11: 写图片页错误与重试 UI 测试**

`ImageViewerScreenTest.kt`：

```kotlin
package com.local.mediaviewer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.local.mediaviewer.image.ImageViewerUiState
import com.local.mediaviewer.image.MediaImageLoaderFactory
import com.local.mediaviewer.ui.image.ImageViewerScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ImageViewerScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun errorStateShowsChineseRetry() {
        val loader = MediaImageLoaderFactory.create(
            ApplicationProvider.getApplicationContext(),
        )
        var retries = 0
        rule.setContent {
            ImageViewerScreen(
                name = "海报.png",
                state = ImageViewerUiState(
                    requestUrl = "http://192.0.2.1/pik/poster.png",
                    errorMessage = "图片加载失败",
                ),
                imageLoader = loader,
                onLoadError = {},
                onRetry = { retries += 1 },
                onBack = {},
            )
        }
        rule.onNodeWithText("海报.png").assertIsDisplayed()
        rule.onNodeWithText("图片加载失败").assertIsDisplayed()
        rule.onNodeWithText("重试").performClick()
        rule.runOnIdle { assertEquals(1, retries) }
        loader.shutdown()
    }
}
```

- [ ] **Step 12: 运行本任务门禁**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.image.*'
.\gradlew.bat lintDebug assembleDebug
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.ImageViewerScreenTest
```

Expected:

```text
ImageLoader, endpoint retry, and zoom tests pass
Image UI test passes
ImageLoader.diskCache is null
Default and per-request diskCachePolicy are DISABLED
Lint reports 0 errors
```

- [ ] **Step 13: 提交**

```powershell
git add app/src/main/java/com/local/mediaviewer/app `
  app/src/main/java/com/local/mediaviewer/image `
  app/src/main/java/com/local/mediaviewer/ui/image `
  app/src/test/java/com/local/mediaviewer/image `
  app/src/androidTest/java/com/local/mediaviewer/ImageViewerScreenTest.kt
git commit -m "feat: add memory-only image viewer"
```
