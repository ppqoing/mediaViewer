# 视频音频播放与断点续播 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 提供视频和音频播放页面，连接 LibVLC、Room 进度、seek、横屏沉浸、生命周期保存和一次端点刷新。

**Architecture:** 路由转换成稳定的 `PlayerRequest`；`PlayerViewModel` 独占一个 `PlaybackEngine`，合并引擎状态、恢复提示和错误。视频 Composable 只额外管理 `SurfaceView` 与全屏控制，音频 Composable 复用同一控制模型但不创建视频 Surface。

**Tech Stack:** Compose Material 3、LibVLC PlaybackEngine、Room、ViewModel、WindowInsetsControllerCompat。

## Global Constraints

- 视频、音频和未知文件使用同一 LibVLC 引擎。
- 音频页不得创建视频 Surface。
- 视频使用 `SurfaceView`、可横屏全屏、播放时常亮。
- 返回键在全屏时先退出全屏，再离开播放页。
- 旋转不重建播放器实例或丢失位置。
- 每 5 秒、暂停、离开页面和应用后台时保存。
- 不足 10 秒不恢复；结束或达到 95% 清除。
- 第一次播放错误重新解析并重试一次，第二次显示中文错误。
- 不提供后台播放、通知、画中画或下载。

---

### Task 11: PlayerViewModel、VideoPlayerScreen 与 AudioPlayerScreen

**Files:**

- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/local/mediaviewer/app/AppContainer.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`
- Create: `app/src/main/java/com/local/mediaviewer/player/PlayerModels.kt`
- Create: `app/src/main/java/com/local/mediaviewer/player/PlayerViewModel.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/player/PlayerFormatters.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/player/FullscreenController.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/player/VlcSurface.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/player/PlayerControls.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/player/VideoPlayerScreen.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/player/AudioPlayerScreen.kt`
- Test: `app/src/test/java/com/local/mediaviewer/player/PlayerViewModelTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/ui/player/PlayerFormattersTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/PlayerScreenTest.kt`

**Interfaces:**

- Consumes:

```kotlin
interface PlaybackEngine
fun interface PlaybackEngineFactory
interface PlaybackPositionStore
interface ServerSessionManager
data class PlayerRoute(
    val name: String,
    val logicalUrl: String,
    val requestUrl: String,
    val mediaKey: String,
    val kind: MediaKind,
)
```

- Produces:

```kotlin
data class PlayerRequest(
    val name: String,
    val logicalUrl: String,
    val requestUrl: String,
    val mediaKey: String,
    val kind: MediaKind,
)

data class PlayerUiState(
    val name: String,
    val kind: MediaKind,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPercent: Float = 0f,
    val isSeekable: Boolean = false,
    val resumedFromMs: Long? = null,
    val errorMessage: String? = null,
)
```

- [ ] **Step 1: 写 PlayerViewModel 失败测试**

`PlayerViewModelTest.kt`：

```kotlin
package com.local.mediaviewer.player

import android.view.SurfaceView
import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.network.ConnectionTestResult
import com.local.mediaviewer.playback.PlaybackEngine
import com.local.mediaviewer.playback.PlaybackPositionStore
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun before() = Dispatchers.setMain(dispatcher)
    @After fun after() = Dispatchers.resetMain()

    @Test
    fun `准备播放并在获得时长后恢复位置`() = runTest(dispatcher) {
        val engine = FakeEngine()
        val store = FakeStore(resume = 30_000)
        val viewModel = PlayerViewModel(
            request(),
            engine,
            store,
            FakePlayerSession(),
            clock = { 123L },
        )
        advanceUntilIdle()
        assertEquals(listOf(request().requestUrl), engine.preparedUrls)
        assertEquals(1, engine.playCalls)

        engine.emit(
            PlaybackState(
                status = PlaybackStatus.PLAYING,
                durationMs = 100_000,
                positionMs = 1_000,
                isSeekable = true,
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf(30_000L), engine.seeks)
        assertEquals(30_000L, viewModel.uiState.value.resumedFromMs)
    }

    @Test
    fun `每五秒暂停和结束使用当前快照写入`() = runTest(dispatcher) {
        val engine = FakeEngine()
        val store = FakeStore()
        val viewModel = PlayerViewModel(
            request(),
            engine,
            store,
            FakePlayerSession(),
            clock = { 456L },
        )
        advanceUntilIdle()
        engine.emit(
            PlaybackState(
                PlaybackStatus.PLAYING,
                positionMs = 20_000,
                durationMs = 100_000,
                isSeekable = true,
            ),
        )
        advanceTimeBy(5_001)
        assertTrue(store.records.any { it.positionMs == 20_000L })

        viewModel.pause()
        advanceUntilIdle()
        assertTrue(store.records.size >= 2)

        engine.emit(
            PlaybackState(
                PlaybackStatus.ENDED,
                positionMs = 100_000,
                durationMs = 100_000,
            ),
        )
        advanceUntilIdle()
        assertTrue(store.records.last().ended)
    }

    @Test
    fun `第一次错误刷新 IPv4 并只重试一次`() = runTest(dispatcher) {
        val engine = FakeEngine()
        val session = FakePlayerSession(
            refreshed = SessionEndpoint(
                "http://media.example:8080",
                "http://192.0.2.2:8080",
                "192.0.2.2",
            ),
        )
        val viewModel = PlayerViewModel(
            request(),
            engine,
            FakeStore(),
            session,
            clock = { 1L },
        )
        advanceUntilIdle()

        engine.emit(
            PlaybackState(
                status = PlaybackStatus.ERROR,
                errorMessage = "第一次失败",
            ),
        )
        advanceUntilIdle()
        assertEquals(1, session.refreshCalls)
        assertTrue(engine.preparedUrls.last().startsWith("http://192.0.2.2:8080/"))

        engine.emit(
            PlaybackState(
                status = PlaybackStatus.ERROR,
                errorMessage = "仍然失败",
            ),
        )
        advanceUntilIdle()
        assertEquals(1, session.refreshCalls)
        assertEquals("仍然失败", viewModel.uiState.value.errorMessage)
    }
}

private fun request() = PlayerRequest(
    name = "movie.mp4",
    logicalUrl = "http://media.example:8080/middle/movie.mp4",
    requestUrl = "http://192.0.2.1:8080/middle/movie.mp4",
    mediaKey = "http://media.example:8080/middle/movie.mp4",
    kind = MediaKind.VIDEO,
)

private class FakeEngine : PlaybackEngine {
    private val mutable = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutable
    val preparedUrls = mutableListOf<String>()
    val seeks = mutableListOf<Long>()
    var playCalls = 0
    var pauseCalls = 0
    override fun prepare(url: String) { preparedUrls += url }
    override fun attachVideoSurface(surfaceView: SurfaceView) = Unit
    override fun detachVideoSurface() = Unit
    override fun play() { playCalls += 1 }
    override fun pause() { pauseCalls += 1 }
    override fun seekTo(positionMs: Long) { seeks += positionMs }
    override fun close() = Unit
    fun emit(state: PlaybackState) { mutable.value = state }
}

private data class SavedRecord(
    val positionMs: Long,
    val durationMs: Long,
    val ended: Boolean,
)

private class FakeStore(
    private val resume: Long? = null,
) : PlaybackPositionStore {
    val records = mutableListOf<SavedRecord>()
    override suspend fun resumePosition(mediaKey: String) = resume
    override suspend fun record(
        mediaKey: String,
        positionMs: Long,
        durationMs: Long,
        updatedAtEpochMs: Long,
        ended: Boolean,
    ) {
        records += SavedRecord(positionMs, durationMs, ended)
    }
    override suspend fun clear(mediaKey: String) = Unit
}

private class FakePlayerSession(
    private val refreshed: SessionEndpoint = SessionEndpoint(
        "http://media.example:8080",
        "http://192.0.2.1:8080",
        "192.0.2.1",
    ),
) : ServerSessionManager {
    private val mutable = MutableStateFlow<ServerSessionState>(
        ServerSessionState.Connected(refreshed, listOf(refreshed.ipv4)),
    )
    override val state: StateFlow<ServerSessionState> = mutable
    var refreshCalls = 0
    override suspend fun connectSaved() = Unit
    override suspend fun testCandidate(input: String) =
        error("not used")
    override suspend fun saveCandidate(result: ConnectionTestResult) = Unit
    override suspend fun refreshAfterRequestFailure(): AppResult<SessionEndpoint> {
        refreshCalls += 1
        return AppResult.Success(refreshed)
    }
}
```

- [ ] **Step 2: 运行 ViewModel 测试并观察预期失败**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.player.PlayerViewModelTest'
```

Expected:

```text
Kotlin compilation fails because PlayerRequest and PlayerViewModel are unresolved
```

- [ ] **Step 3: 实现播放器请求和 UI 状态**

`PlayerModels.kt`：

```kotlin
package com.local.mediaviewer.player

import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus

data class PlayerRequest(
    val name: String,
    val logicalUrl: String,
    val requestUrl: String,
    val mediaKey: String,
    val kind: MediaKind,
)

data class PlayerUiState(
    val name: String,
    val kind: MediaKind,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPercent: Float = 0f,
    val isSeekable: Boolean = false,
    val resumedFromMs: Long? = null,
    val errorMessage: String? = null,
)

fun PlayerUiState.withEngine(state: PlaybackState) = copy(
    status = state.status,
    positionMs = state.positionMs,
    durationMs = state.durationMs,
    bufferedPercent = state.bufferedPercent,
    isSeekable = state.isSeekable,
    errorMessage = state.errorMessage,
)
```

- [ ] **Step 4: 实现恢复、周期保存和一次刷新**

`PlayerViewModel.kt`：

```kotlin
package com.local.mediaviewer.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.playback.PlaybackEngine
import com.local.mediaviewer.playback.PlaybackPositionStore
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.session.ServerSessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val initialRequest: PlayerRequest,
    val engine: PlaybackEngine,
    private val positionStore: PlaybackPositionStore,
    private val session: ServerSessionManager,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private var currentRequest = initialRequest
    private var pendingResumeMs: Long? = null
    private var resumeApplied = false
    private var endpointRetryUsed = false
    private var lastStatus = PlaybackStatus.IDLE
    private val mutableUiState = MutableStateFlow(
        PlayerUiState(
            name = initialRequest.name,
            kind = initialRequest.kind,
        ),
    )
    val uiState: StateFlow<PlayerUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            pendingResumeMs = positionStore.resumePosition(
                initialRequest.mediaKey,
            )
            engine.prepare(currentRequest.requestUrl)
            engine.play()
        }
        viewModelScope.launch {
            engine.state.collect { state ->
                mutableUiState.value = mutableUiState.value.withEngine(state)
                applyResumeIfReady()
                if (
                    state.status == PlaybackStatus.ENDED &&
                    lastStatus != PlaybackStatus.ENDED
                ) {
                    saveSnapshot(ended = true)
                }
                if (
                    state.status == PlaybackStatus.ERROR &&
                    lastStatus != PlaybackStatus.ERROR
                ) {
                    recoverEndpointOnce()
                }
                lastStatus = state.status
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                saveSnapshot(ended = false)
            }
        }
    }

    fun play() = engine.play()

    fun pause() {
        engine.pause()
        viewModelScope.launch { saveSnapshot(ended = false) }
    }

    fun seekTo(positionMs: Long) = engine.seekTo(positionMs)

    fun onBackgrounded() {
        engine.pause()
        viewModelScope.launch { saveSnapshot(ended = false) }
    }

    fun leave(onSaved: () -> Unit) {
        viewModelScope.launch {
            saveSnapshot(ended = false)
            engine.close()
            onSaved()
        }
    }

    private fun applyResumeIfReady() {
        val resume = pendingResumeMs ?: return
        val state = engine.state.value
        if (
            !resumeApplied &&
            state.isSeekable &&
            state.durationMs > resume
        ) {
            engine.seekTo(resume)
            resumeApplied = true
            mutableUiState.value = mutableUiState.value.copy(
                resumedFromMs = resume,
            )
        }
    }

    private suspend fun recoverEndpointOnce() {
        if (endpointRetryUsed) return
        endpointRetryUsed = true
        when (val refreshed = session.refreshAfterRequestFailure()) {
            is AppResult.Success -> {
                currentRequest = currentRequest.copy(
                    requestUrl = refreshed.value.requestUrlFor(
                        currentRequest.logicalUrl,
                    ),
                )
                lastStatus = PlaybackStatus.IDLE
                mutableUiState.value = mutableUiState.value.copy(
                    errorMessage = null,
                )
                engine.prepare(currentRequest.requestUrl)
                engine.play()
            }
            is AppResult.Failure -> {
                mutableUiState.value = mutableUiState.value.copy(
                    status = PlaybackStatus.ERROR,
                    errorMessage = refreshed.error.userMessage,
                )
            }
        }
    }

    private suspend fun saveSnapshot(ended: Boolean) {
        val state = engine.state.value
        positionStore.record(
            mediaKey = currentRequest.mediaKey,
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            updatedAtEpochMs = clock(),
            ended = ended,
        )
    }

    override fun onCleared() {
        engine.close()
    }
}
```

- [ ] **Step 5: 运行 ViewModel 测试并确认通过**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.player.PlayerViewModelTest'
```

Expected:

```text
Resume, five-second save, pause, completion, and one-retry tests pass
```

- [ ] **Step 6: 写和实现时间格式**

`PlayerFormattersTest.kt`：

```kotlin
package com.local.mediaviewer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerFormattersTest {
    @Test
    fun `时长覆盖小时与负值`() {
        assertEquals("00:00", formatPlaybackTime(-1))
        assertEquals("01:05", formatPlaybackTime(65_000))
        assertEquals("1:02:03", formatPlaybackTime(3_723_000))
    }
}
```

`PlayerFormatters.kt`：

```kotlin
package com.local.mediaviewer.ui.player

import java.util.Locale

fun formatPlaybackTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}
```

- [ ] **Step 7: 实现 SurfaceView 适配**

`VlcSurface.kt`：

```kotlin
package com.local.mediaviewer.ui.player

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.local.mediaviewer.playback.PlaybackEngine

@Composable
fun VlcSurface(
    engine: PlaybackEngine,
    keepScreenOn: Boolean,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier.testTag("vlc_surface"),
        factory = { context ->
            SurfaceView(context).also {
                it.keepScreenOn = keepScreenOn
                engine.attachVideoSurface(it)
            }
        },
        update = { it.keepScreenOn = keepScreenOn },
        onRelease = {
            it.keepScreenOn = false
            engine.detachVideoSurface()
        },
    )
}
```

- [ ] **Step 8: 实现横屏沉浸控制**

`FullscreenController.kt`：

```kotlin
package com.local.mediaviewer.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FullscreenController(
    private val activity: Activity,
) : AutoCloseable {
    private val mutableFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = mutableFullscreen

    fun enter() {
        activity.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        WindowInsetsControllerCompat(
            activity.window,
            activity.window.decorView,
        ).hide(WindowInsetsCompat.Type.systemBars())
        mutableFullscreen.value = true
    }

    fun exit() {
        WindowInsetsControllerCompat(
            activity.window,
            activity.window.decorView,
        ).show(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        activity.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        mutableFullscreen.value = false
    }

    override fun close() {
        if (mutableFullscreen.value) exit()
    }
}
```

在 `AndroidManifest.xml` 的 `MainActivity` 声明中追加：

```xml
android:configChanges="orientation|screenSize|smallestScreenSize"
```

应用只有这一项 Activity。由该 Activity 接收方向与窗口尺寸变化，Compose
根据新 `Configuration` 重组；`FullscreenController`、导航返回栈和
`PlayerViewModel` 不因横竖屏切换销毁。进程终止仍走正常状态恢复路径。

- [ ] **Step 9: 实现共用播放控制**

`PlayerControls.kt`：

```kotlin
package com.local.mediaviewer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.playback.PlaybackStatus

@Composable
fun PlayerControls(
    state: PlayerUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
    trailingControl: @Composable () -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.resumedFromMs?.let {
            Text("已从 ${formatPlaybackTime(it)} 继续播放")
        }
        if (state.status == PlaybackStatus.BUFFERING) {
            LinearProgressIndicator(
                progress = { state.bufferedPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Slider(
            value = state.positionMs.toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
            enabled = state.isSeekable,
            modifier = Modifier.testTag("seek"),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${formatPlaybackTime(state.positionMs)} / " +
                    formatPlaybackTime(state.durationMs),
            )
            IconButton(
                onClick = if (state.status == PlaybackStatus.PLAYING) {
                    onPause
                } else {
                    onPlay
                },
            ) {
                Icon(
                    if (state.status == PlaybackStatus.PLAYING) {
                        Icons.Default.Pause
                    } else {
                        Icons.Default.PlayArrow
                    },
                    contentDescription = if (
                        state.status == PlaybackStatus.PLAYING
                    ) "暂停" else "播放",
                )
            }
            trailingControl()
        }
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
```

`Slider` 的拖动值只调用 LibVLC seek，不执行 HTTP 下载。

- [ ] **Step 10: 实现视频与音频页面**

`VideoPlayerScreen.kt`：

```kotlin
package com.local.mediaviewer.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.playback.PlaybackEngine
import com.local.mediaviewer.playback.PlaybackStatus

@Composable
fun VideoPlayerScreen(
    state: PlayerUiState,
    engine: PlaybackEngine,
    fullscreenController: FullscreenController,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val fullscreen by fullscreenController.isFullscreen.collectAsStateWithLifecycle()
    BackHandler {
        if (fullscreen) fullscreenController.exit() else onBack()
    }
    Scaffold(
        topBar = {
            if (!fullscreen) {
                TopAppBar(
                    title = { Text(state.name) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, "返回")
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            VlcSurface(
                engine = engine,
                keepScreenOn = state.status == PlaybackStatus.PLAYING,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            if (!fullscreen) {
                PlayerControls(state, onPlay, onPause, onSeek) {
                    IconButton(onClick = fullscreenController::enter) {
                        Icon(Icons.Default.Fullscreen, "全屏")
                    }
                }
            }
        }
    }
}
```

`AudioPlayerScreen.kt`：

```kotlin
package com.local.mediaviewer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.player.PlayerUiState

@Composable
fun AudioPlayerScreen(
    state: PlayerUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.AudioFile,
                "音频",
                Modifier.size(96.dp),
            )
            PlayerControls(state, onPlay, onPause, onSeek)
        }
    }
}
```

未知文件选择 `VideoPlayerScreen`，以便 LibVLC 若发现视频轨时有 Surface；已知音频才使用 `AudioPlayerScreen`。

- [ ] **Step 11: 把 Room Store 接入 AppContainer**

在 `AppContainer` 接口追加：

```kotlin
val playbackPositionStore: PlaybackPositionStore
```

在 `DefaultAppContainer` 追加：

```kotlin
private val database: MediaViewerDatabase by lazy {
    Room.databaseBuilder(
        context.applicationContext,
        MediaViewerDatabase::class.java,
        "mediaviewer.db",
    ).build()
}

override val playbackPositionStore: PlaybackPositionStore by lazy {
    RoomPlaybackPositionStore(database.playbackPositionDao())
}
```

添加精确 import：

```kotlin
import androidx.room.Room
import com.local.mediaviewer.playback.MediaViewerDatabase
import com.local.mediaviewer.playback.PlaybackPositionStore
import com.local.mediaviewer.playback.RoomPlaybackPositionStore
```

- [ ] **Step 12: 用真实播放器页面替换 PlayerRoute 外壳**

在 `MediaViewerApp.kt` 的 `composable<PlayerRoute>` 中替换为：

```kotlin
composable<PlayerRoute> { entry ->
    val route = entry.toRoute<PlayerRoute>()
    val player: PlayerViewModel = viewModel(
        key = "player:${route.mediaKey}",
        factory = viewModelFactory {
            initializer {
                PlayerViewModel(
                    PlayerRequest(
                        route.name,
                        route.logicalUrl,
                        route.requestUrl,
                        route.mediaKey,
                        route.kind,
                    ),
                    container.playbackEngineFactory.create(),
                    container.playbackPositionStore,
                    container.sessionManager,
                )
            }
        },
    )
    val state by player.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity
    val fullscreen = remember(activity) { FullscreenController(activity) }
    DisposableEffect(fullscreen) {
        onDispose { fullscreen.close() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        player.onBackgrounded()
    }
    val leave = {
        player.leave { navController.popBackStack() }
    }
    if (route.kind == MediaKind.AUDIO) {
        AudioPlayerScreen(
            state,
            player::play,
            player::pause,
            player::seekTo,
            leave,
        )
    } else {
        VideoPlayerScreen(
            state,
            player.engine,
            fullscreen,
            player::play,
            player::pause,
            player::seekTo,
            leave,
        )
    }
}
```

添加 `Activity`、`Lifecycle`、`LifecycleEventEffect`、`DisposableEffect`、
`remember`、播放器类的明确 import，并删除 PlayerRoute 使用
`MediaRouteShell` 的代码。引擎只在 `PlayerViewModel` 的 initializer 内创建；
配置变更复用同一个 ViewModel 和 `player.engine`，不会先创建一个未被使用的
LibVLC 实例。

- [ ] **Step 13: 写纯 UI 测试**

`PlayerScreenTest.kt`：

```kotlin
package com.local.mediaviewer

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.player.PlayerUiState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.ui.player.AudioPlayerScreen
import org.junit.Rule
import org.junit.Test

class PlayerScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun audioScreenShowsControlsWithoutVideoSurface() {
        rule.setContent {
            AudioPlayerScreen(
                state = PlayerUiState(
                    name = "音乐.flac",
                    kind = MediaKind.AUDIO,
                    status = PlaybackStatus.PAUSED,
                    positionMs = 30_000,
                    durationMs = 120_000,
                    isSeekable = true,
                    resumedFromMs = 30_000,
                ),
                onPlay = {},
                onPause = {},
                onSeek = {},
                onBack = {},
            )
        }
        rule.onNodeWithText("音乐.flac").assertIsDisplayed()
        rule.onNodeWithText("00:30 / 02:00").assertIsDisplayed()
        rule.onNodeWithText("已从 00:30 继续播放").assertIsDisplayed()
        rule.onNodeWithContentDescription("播放").assertIsDisplayed()
        rule.onNodeWithTag("vlc_surface").assertDoesNotExist()
    }
}
```

- [ ] **Step 14: 运行本任务门禁**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.player.*' `
  --tests 'com.local.mediaviewer.ui.player.*'
.\gradlew.bat lintDebug assembleDebug
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PlayerScreenTest
```

Expected:

```text
Player ViewModel and formatter tests pass
Audio UI test passes with no SurfaceView node
Lint reports 0 errors
Debug APK includes all LibVLC native ABIs
```

- [ ] **Step 15: 提交**

```powershell
git add app/src/main/AndroidManifest.xml `
  app/src/main/java/com/local/mediaviewer/app `
  app/src/main/java/com/local/mediaviewer/player `
  app/src/main/java/com/local/mediaviewer/ui/player `
  app/src/test/java/com/local/mediaviewer/player `
  app/src/test/java/com/local/mediaviewer/ui/player `
  app/src/androidTest/java/com/local/mediaviewer/PlayerScreenTest.kt
git commit -m "feat: add resumable video and audio player UI"
```
