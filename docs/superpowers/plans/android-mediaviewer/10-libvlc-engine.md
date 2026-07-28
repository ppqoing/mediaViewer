# LibVLC 播放引擎 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 封装一个同一时刻单实例、可测试状态、支持 HTTP seek 并正确释放原生资源的 LibVLC 播放引擎。

**Architecture:** `PlaybackEngine` 隔离 UI 与 LibVLC；原生事件先映射成内部 `EngineEvent`，再由纯 reducer 更新 `PlaybackState`。Android 实现管理 `SurfaceView`、音频焦点、耳机断开、进程后台暂停和资源关闭，ViewModel 只通过接口驱动。

**Tech Stack:** `org.videolan.android:libvlc-all:4.0.0-eap29`、Android AudioManager、ProcessLifecycleOwner、StateFlow。

## Global Constraints

- 视频、音频和未知非图片文件都由 LibVLC 尝试播放。
- 视频必须使用 `SurfaceView`，不得使用 `TextureView`。
- 媒体直接使用 HTTP URL，不预下载。
- 支持 play、pause、duration、position、buffering、error 和 seek。
- 耳机断开、失去音频焦点或应用进入后台时暂停。
- 不提供后台播放或通知栏媒体控制。
- 离开播放页释放 MediaPlayer、LibVLC 和视频 Surface。
- 同一时刻只创建一个播放引擎实例。

---

### Task 10: PlaybackEngine 与 AndroidVlcPlaybackEngine

**Files:**

- Modify: `app/src/main/java/com/local/mediaviewer/app/AppContainer.kt`
- Create: `app/src/main/java/com/local/mediaviewer/playback/PlaybackState.kt`
- Create: `app/src/main/java/com/local/mediaviewer/playback/PlaybackEngine.kt`
- Create: `app/src/main/java/com/local/mediaviewer/playback/EngineEventReducer.kt`
- Create: `app/src/main/java/com/local/mediaviewer/playback/PlaybackInterruptions.kt`
- Create: `app/src/main/java/com/local/mediaviewer/playback/AndroidVlcPlaybackEngine.kt`
- Test: `app/src/test/java/com/local/mediaviewer/playback/EngineEventReducerTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/playback/PlaybackInterruptionsTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/LibVlcEngineCreationTest.kt`

**Interfaces:**

- Consumes: HTTP 请求 URL 字符串和 Android `SurfaceView`。
- Produces:

```kotlin
enum class PlaybackStatus {
    IDLE, OPENING, BUFFERING, PLAYING, PAUSED, ENDED, ERROR
}

data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPercent: Float = 0f,
    val isSeekable: Boolean = false,
    val errorMessage: String? = null,
)

interface PlaybackEngine : AutoCloseable {
    val state: StateFlow<PlaybackState>
    fun prepare(url: String)
    fun attachVideoSurface(surfaceView: SurfaceView)
    fun detachVideoSurface()
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    override fun close()
}

fun interface PlaybackEngineFactory {
    fun create(): PlaybackEngine
}
```

- [ ] **Step 1: 写原生事件 reducer 失败测试**

`EngineEventReducerTest.kt`：

```kotlin
package com.local.mediaviewer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineEventReducerTest {
    @Test
    fun `时间长度缓冲和 seekable 增量更新`() {
        val initial = PlaybackState(status = PlaybackStatus.OPENING)
        val updated = listOf(
            EngineEvent.DurationChanged(120_000),
            EngineEvent.TimeChanged(30_000),
            EngineEvent.Buffering(42.5f),
            EngineEvent.SeekableChanged(true),
            EngineEvent.Playing,
        ).fold(initial, EngineEventReducer::reduce)

        assertEquals(120_000L, updated.durationMs)
        assertEquals(30_000L, updated.positionMs)
        assertEquals(42.5f, updated.bufferedPercent)
        assertTrue(updated.isSeekable)
        assertEquals(PlaybackStatus.PLAYING, updated.status)
    }

    @Test
    fun `结束和错误进入终态`() {
        assertEquals(
            PlaybackStatus.ENDED,
            EngineEventReducer.reduce(
                PlaybackState(),
                EngineEvent.EndReached,
            ).status,
        )
        val error = EngineEventReducer.reduce(
            PlaybackState(),
            EngineEvent.Error("无法解码"),
        )
        assertEquals(PlaybackStatus.ERROR, error.status)
        assertEquals("无法解码", error.errorMessage)
    }
}
```

- [ ] **Step 2: 运行 reducer 测试并观察预期失败**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.playback.EngineEventReducerTest'
```

Expected:

```text
Kotlin compilation fails because playback engine state types are unresolved
```

- [ ] **Step 3: 实现播放状态、事件和纯 reducer**

`PlaybackState.kt`：

```kotlin
package com.local.mediaviewer.playback

enum class PlaybackStatus {
    IDLE,
    OPENING,
    BUFFERING,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR,
}

data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPercent: Float = 0f,
    val isSeekable: Boolean = false,
    val errorMessage: String? = null,
)
```

`EngineEventReducer.kt`：

```kotlin
package com.local.mediaviewer.playback

sealed interface EngineEvent {
    data object Opening : EngineEvent
    data class Buffering(val percent: Float) : EngineEvent
    data object Playing : EngineEvent
    data object Paused : EngineEvent
    data class TimeChanged(val positionMs: Long) : EngineEvent
    data class DurationChanged(val durationMs: Long) : EngineEvent
    data class SeekableChanged(val seekable: Boolean) : EngineEvent
    data object EndReached : EngineEvent
    data class Error(val message: String) : EngineEvent
}

object EngineEventReducer {
    fun reduce(state: PlaybackState, event: EngineEvent): PlaybackState =
        when (event) {
            EngineEvent.Opening -> state.copy(
                status = PlaybackStatus.OPENING,
                errorMessage = null,
            )
            is EngineEvent.Buffering -> state.copy(
                status = if (event.percent < 100f) {
                    PlaybackStatus.BUFFERING
                } else {
                    state.status
                },
                bufferedPercent = event.percent.coerceIn(0f, 100f),
            )
            EngineEvent.Playing -> state.copy(status = PlaybackStatus.PLAYING)
            EngineEvent.Paused -> state.copy(status = PlaybackStatus.PAUSED)
            is EngineEvent.TimeChanged -> state.copy(
                positionMs = event.positionMs.coerceAtLeast(0L),
            )
            is EngineEvent.DurationChanged -> state.copy(
                durationMs = event.durationMs.coerceAtLeast(0L),
            )
            is EngineEvent.SeekableChanged -> state.copy(
                isSeekable = event.seekable,
            )
            EngineEvent.EndReached -> state.copy(
                status = PlaybackStatus.ENDED,
            )
            is EngineEvent.Error -> state.copy(
                status = PlaybackStatus.ERROR,
                errorMessage = event.message,
            )
        }
}
```

- [ ] **Step 4: 定义引擎和工厂接口**

`PlaybackEngine.kt`：

```kotlin
package com.local.mediaviewer.playback

import android.view.SurfaceView
import kotlinx.coroutines.flow.StateFlow

interface PlaybackEngine : AutoCloseable {
    val state: StateFlow<PlaybackState>
    fun prepare(url: String)
    fun attachVideoSurface(surfaceView: SurfaceView)
    fun detachVideoSurface()
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    override fun close()
}

fun interface PlaybackEngineFactory {
    fun create(): PlaybackEngine
}
```

- [ ] **Step 5: 写耳机断开与后台暂停失败测试**

`PlaybackInterruptionsTest.kt`：

```kotlin
package com.local.mediaviewer.playback

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlaybackInterruptionsTest {
    @Test
    fun `耳机断开广播触发暂停`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var pauses = 0
        val interruptions = PlaybackInterruptions(
            context = context,
            onPauseRequested = { pauses += 1 },
        )
        interruptions.start()

        context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        assertEquals(1, pauses)
        interruptions.close()
    }
}
```

- [ ] **Step 6: 实现音频焦点、耳机和进程生命周期协调**

`PlaybackInterruptions.kt`：

```kotlin
package com.local.mediaviewer.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class PlaybackInterruptions(
    context: Context,
    private val onPauseRequested: () -> Unit,
) : AutoCloseable, DefaultLifecycleObserver {
    private val appContext = context.applicationContext
    private val audioManager =
        appContext.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(
        AudioManager.AUDIOFOCUS_GAIN,
    )
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build(),
        )
        .setOnAudioFocusChangeListener { change ->
            if (
                change == AudioManager.AUDIOFOCUS_LOSS ||
                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
            ) {
                onPauseRequested()
            }
        }
        .build()
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                onPauseRequested()
            }
        }
    }
    private var started = false

    fun start(): Boolean {
        if (!started) {
            ContextCompat.registerReceiver(
                appContext,
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
            started = true
        }
        return audioManager.requestAudioFocus(focusRequest) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun onStop(owner: LifecycleOwner) {
        onPauseRequested()
    }

    override fun close() {
        if (!started) return
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        appContext.unregisterReceiver(noisyReceiver)
        audioManager.abandonAudioFocusRequest(focusRequest)
        started = false
    }
}
```

API 29 及以上使用带 `AudioFocusRequest` 的实现，不写旧 API 分支。

- [ ] **Step 7: 实现 LibVLC 适配器**

`AndroidVlcPlaybackEngine.kt`：

```kotlin
package com.local.mediaviewer.playback

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class AndroidVlcPlaybackEngine(
    context: Context,
) : PlaybackEngine {
    private val closed = AtomicBoolean(false)
    private val libVlc = LibVLC(
        context.applicationContext,
        arrayListOf("--network-caching=1500"),
    )
    private val mediaPlayer = MediaPlayer(libVlc)
    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    private val interruptions = PlaybackInterruptions(context, ::pause)

    init {
        mediaPlayer.setEventListener(::onVlcEvent)
    }

    override fun prepare(url: String) {
        check(!closed.get()) { "PlaybackEngine is closed" }
        mutableState.value = PlaybackState(status = PlaybackStatus.OPENING)
        val media = Media(libVlc, Uri.parse(url))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":network-caching=1500")
        mediaPlayer.media = media
        media.release()
    }

    override fun attachVideoSurface(surfaceView: SurfaceView) {
        check(!closed.get()) { "PlaybackEngine is closed" }
        val vout = mediaPlayer.vlcVout
        if (vout.areViewsAttached()) vout.detachViews()
        vout.setVideoView(surfaceView)
        vout.attachViews()
    }

    override fun detachVideoSurface() {
        val vout = mediaPlayer.vlcVout
        if (vout.areViewsAttached()) vout.detachViews()
    }

    override fun play() {
        if (closed.get()) return
        if (interruptions.start()) mediaPlayer.play()
    }

    override fun pause() {
        if (!closed.get() && mediaPlayer.isPlaying) mediaPlayer.pause()
    }

    override fun seekTo(positionMs: Long) {
        if (!closed.get() && mutableState.value.isSeekable) {
            mediaPlayer.time = positionMs.coerceIn(
                0L,
                mutableState.value.durationMs.coerceAtLeast(0L),
            )
        }
    }

    private fun onVlcEvent(event: MediaPlayer.Event) {
        val mapped = when (event.type) {
            MediaPlayer.Event.Opening -> EngineEvent.Opening
            MediaPlayer.Event.Buffering ->
                EngineEvent.Buffering(event.buffering)
            MediaPlayer.Event.Playing -> EngineEvent.Playing
            MediaPlayer.Event.Paused -> EngineEvent.Paused
            MediaPlayer.Event.TimeChanged ->
                EngineEvent.TimeChanged(event.timeChanged)
            MediaPlayer.Event.LengthChanged ->
                EngineEvent.DurationChanged(event.lengthChanged)
            MediaPlayer.Event.SeekableChanged ->
                EngineEvent.SeekableChanged(event.seekable)
            MediaPlayer.Event.EndReached -> EngineEvent.EndReached
            MediaPlayer.Event.EncounteredError ->
                EngineEvent.Error("VLC 无法播放此媒体")
            MediaPlayer.Event.Vout -> {
                mediaPlayer.updateVideoSurfaces()
                return
            }
            else -> return
        }
        mutableState.value = EngineEventReducer.reduce(
            mutableState.value,
            mapped,
        )
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        interruptions.close()
        mediaPlayer.setEventListener(null)
        if (mediaPlayer.vlcVout.areViewsAttached()) {
            mediaPlayer.vlcVout.detachViews()
        }
        mediaPlayer.stop()
        mediaPlayer.release()
        libVlc.release()
    }
}
```

`4.0.0-eap29` AAR 的公开 getter 已对应为 Kotlin 属性
`event.buffering`、`event.timeChanged`、`event.lengthChanged`、
`event.seekable` 与 `mediaPlayer.vlcVout`；保持上述直接调用，不使用反射。

- [ ] **Step 8: 将引擎工厂接入 AppContainer**

在 `AppContainer` 接口追加：

```kotlin
val playbackEngineFactory: PlaybackEngineFactory
```

在 `DefaultAppContainer` 追加：

```kotlin
override val playbackEngineFactory = PlaybackEngineFactory {
    AndroidVlcPlaybackEngine(context.applicationContext)
}
```

添加 import：

```kotlin
import com.local.mediaviewer.playback.AndroidVlcPlaybackEngine
import com.local.mediaviewer.playback.PlaybackEngineFactory
```

- [ ] **Step 9: 写 x86_64 原生创建/释放测试**

`LibVlcEngineCreationTest.kt`：

```kotlin
package com.local.mediaviewer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.local.mediaviewer.playback.AndroidVlcPlaybackEngine
import com.local.mediaviewer.playback.PlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibVlcEngineCreationTest {
    @Test
    fun createAndCloseNativeEngine() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = AndroidVlcPlaybackEngine(context)
        assertEquals(PlaybackStatus.IDLE, engine.state.value.status)
        engine.close()
        engine.close()
    }
}
```

- [ ] **Step 10: 运行本任务门禁**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.playback.EngineEventReducerTest' `
  --tests 'com.local.mediaviewer.playback.PlaybackInterruptionsTest'
.\gradlew.bat lintDebug assembleDebug
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.LibVlcEngineCreationTest
```

Expected:

```text
Reducer and interruption tests pass
LibVLC native libraries load on API 36 x86_64
Repeated close does not crash
Lint reports 0 errors
```

- [ ] **Step 11: 提交**

```powershell
git add app/src/main/java/com/local/mediaviewer/app/AppContainer.kt `
  app/src/main/java/com/local/mediaviewer/playback `
  app/src/test/java/com/local/mediaviewer/playback `
  app/src/androidTest/java/com/local/mediaviewer/LibVlcEngineCreationTest.kt
git commit -m "feat: add lifecycle-safe LibVLC engine"
```
