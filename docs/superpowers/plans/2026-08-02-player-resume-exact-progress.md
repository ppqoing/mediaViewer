# VLC 类播放器权威进度快照修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 Android 应用内的视频和音频时间数字、进度滑块只显示 LibVLC 播放内核的权威位置，消除暂停恢复后 Media3 客户端墙钟外推造成的超前和二次乘速。

**Architecture:** 采用 VLC、ExoPlayer 等播放器常用的“播放内核时钟为唯一事实来源，UI 周期采样绝对位置”模式。`PlaybackCoordinator` 保留 LibVLC `TimeChanged` 快照，播放服务通过自定义 `SessionCommand` 返回带媒体身份的位置快照；应用控制器每次请求完成后等待 250ms 再请求下一次，并用该快照替代 `MediaController.currentPosition`，其余播放状态仍走 Media3。

**Tech Stack:** Kotlin 2.3.21、Android Media3 1.10.1、LibVLC 4.0.0-eap29、Kotlin Coroutines、JUnit 4、Robolectric、AndroidX Instrumentation、Gradle Android 插件。

**Design:** `docs/superpowers/specs/2026-08-02-player-resume-exact-progress-design.md`

**Root-cause evidence:** `docs/analysis/2026-08-02-player-resume-progress-root-cause.md`

## Global Constraints

- LibVLC `TimeChanged` 经 `PlaybackCoordinator.sessionState` 发布的绝对位置是应用 UI 的唯一可信播放时钟。
- 播放状态与播放时钟必须分离：`playWhenReady`、`isPlaying`、缓冲或倍速变化不得自行推进 UI 位置。
- 应用 UI 的位置发布路径不得读取或回退到 `MediaController.currentPosition`。
- `VlcSessionPlayer` 继续使用 `PositionSupplier.getConstant`；不撤销提交 `19fb089` 的会话侧保护。
- 每个位置响应必须携带稳定 `mediaKey`；旧媒体、旧连接或无法解码的响应不得进入当前 UI。
- 首次连接立即采样一次；每次请求完成后等待 250ms，再发起下一次，同一连接最多存在一个位置请求。
- 请求失败时保留当前媒体最后一个有效快照并在下一轮重试，不断开播放器、不停止播放，也不使用墙钟补偿。
- 同一媒体允许位置向后更新，以支持向后 seek；不得增加单调递增限制。
- 视频与音频共用该位置通道；不得把视频 Surface、亮度或画面比例逻辑带入音频页。
- 不修改 libvlc 版本、Media3 版本、`--network-caching=1500`、`refreshVideoOutput()` 或 `updateVideoSurfaces()`。
- 不新增第三方依赖，不修改 Room schema、应用 ID、版本号、ABI 或签名规则。
- 只做基础功能性审查。每项测试按计划首次执行；后续只重新运行未通过的测试，不重复已通过的定向测试。
- 不修改或提交现有未跟踪的 `.superpowers/brainstorm/`、`docs/analysis/`、历史验证文档和无关 `dist` 内容。
- 每次提交只暂存任务明确列出的文件，禁止使用 `git add .`。

---

## 通用播放器实现原则

1. **单一权威时钟：** 播放内核决定位置，UI 不根据“正在播放”状态自行累计时间。
2. **周期采样而非高频事件广播：** UI 以固定节奏读取绝对位置，避免把每个解码事件跨 Binder 推送。
3. **状态与位置解耦：** Media3 继续负责队列、命令、通知和状态；自定义位置通道只负责时间轴。
4. **身份校验：** 每个采样结果绑定媒体 ID，防止切歌或重连时迟到响应串到新媒体。
5. **停滞即保持：** 暂停、缓冲或恢复等待期间，如果内核没有新位置，UI 保持最后快照而不是墙钟空转。
6. **绝对位置允许回退：** seek 后直接接受新的内核位置，不做单调增长或平滑纠错。

---

## 文件结构与职责

### 新增文件

- `app/src/main/java/com/local/mediaviewer/service/PlaybackPositionSnapshotCodec.kt`：定义跨 MediaSession 传输的位置快照、命令 action、Bundle 编解码和会话状态到快照的转换。
- `app/src/main/java/com/local/mediaviewer/player/ExactPlaybackPositionStore.kt`：只缓存与当前媒体匹配的权威位置，并处理切换、重连及向后 seek。
- `app/src/test/java/com/local/mediaviewer/service/PlaybackPositionSnapshotCodecTest.kt`：验证快照转换和 Bundle 边界。
- `app/src/test/java/com/local/mediaviewer/player/ExactPlaybackPositionStoreTest.kt`：验证媒体身份、失败保持、清理和向后 seek。
- `docs/verification/2026-08-02-player-resume-exact-progress.md`：记录 RED/GREEN、定向回归、设备边界和 Release 产物证据。

### 修改文件

- `app/src/main/java/com/local/mediaviewer/service/PlaybackSessionCallback.kt:20-104`：公开并处理读取权威位置的自定义命令。
- `app/src/test/java/com/local/mediaviewer/service/PlaybackSessionCallbackTest.kt:27-295`：验证命令可用性、成功返回和空队列错误。
- `app/src/main/java/com/local/mediaviewer/player/Media3PlaybackController.kt:55-568`：串行轮询权威位置、校验连接和媒体，并从 UI 快照路径移除 `currentPosition`。
- `app/src/androidTest/java/com/local/mediaviewer/MediaSessionControlsTest.kt:51-510`：覆盖真实 MediaController ↔ MediaSession 自定义命令链路和暂停位置保持。
- `app/src/androidTest/java/com/local/mediaviewer/testing/BackgroundPlaybackTestHarness.kt:50-334`：允许测试注入位置冻结的播放内核，默认仍使用真实 LibVLC。
- `docs/verification/2026-08-02-player-progress-controls-layout.md`：只追加旧修复被本次根因修复取代的交叉引用，不改写既有历史结果。

---

### Task 1：定义播放器权威位置快照契约

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/service/PlaybackPositionSnapshotCodec.kt`
- Create: `app/src/test/java/com/local/mediaviewer/service/PlaybackPositionSnapshotCodecTest.kt`

**Interfaces:**
- Consumes: `PlaybackSessionState.currentItem`、`PlaybackSessionState.queue.currentMediaKey`、`PlaybackState.positionMs`、`PlaybackState.durationMs`。
- Produces: `ACTION_GET_EXACT_PLAYBACK_POSITION: String`。
- Produces: `PlaybackPositionSnapshot(mediaKey: String, positionMs: Long, durationMs: Long)`。
- Produces: `PlaybackSessionState.toPlaybackPositionSnapshot(): PlaybackPositionSnapshot?`。
- Produces: `PlaybackPositionSnapshotCodec.encode(snapshot): Bundle` 和 `decode(bundle): PlaybackPositionSnapshot?`。

- [ ] **Step 1：编写快照转换和 Bundle 边界失败测试**

新增以下测试，测试名称保持中文或反引号形式：

```kotlin
@RunWith(RobolectricTestRunner::class)
class PlaybackPositionSnapshotCodecTest {
    @Test
    fun `会话位置转换会裁剪负数和超过时长的位置`() {
        val session = sessionState(
            mediaKey = "video-a",
            positionMs = 75_000L,
            durationMs = 60_000L,
        )

        assertEquals(
            PlaybackPositionSnapshot("video-a", 60_000L, 60_000L),
            session.toPlaybackPositionSnapshot(),
        )
    }

    @Test
    fun `快照 Bundle 往返保留媒体位置和时长`() {
        val expected = PlaybackPositionSnapshot("audio-b", 8_500L, 90_000L)

        assertEquals(
            expected,
            PlaybackPositionSnapshotCodec.decode(
                PlaybackPositionSnapshotCodec.encode(expected),
            ),
        )
    }

    @Test
    fun `损坏 Bundle 和空媒体不会产生快照`() {
        assertNull(PlaybackSessionState().toPlaybackPositionSnapshot())
        assertNull(PlaybackPositionSnapshotCodec.decode(Bundle.EMPTY))
        assertNull(
            PlaybackPositionSnapshotCodec.decode(
                Bundle().apply {
                    putString("media_key", "video-a")
                    putLong("position_ms", -1L)
                    putLong("duration_ms", 60_000L)
                },
            ),
        )
    }
}
```

测试辅助函数使用以下完整结构，不得只伪造 Bundle 而绕过真实转换入口：

```kotlin
private fun sessionState(
    mediaKey: String,
    positionMs: Long,
    durationMs: Long,
): PlaybackSessionState {
    val item = QueueMediaItem(
        mediaKey = mediaKey,
        name = mediaKey,
        logicalUrl = "https://example.test/$mediaKey",
        kind = MediaKind.VIDEO,
    )
    return PlaybackSessionState(
        playback = PlaybackState(
            status = PlaybackStatus.PLAYING,
            positionMs = positionMs,
            durationMs = durationMs,
            isSeekable = true,
        ),
        playWhenReady = true,
        queue = PlaybackQueue(
            items = listOf(item),
            currentMediaKey = mediaKey,
        ),
        currentItem = item,
    )
}
```

- [ ] **Step 2：运行新测试并确认 RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests '*PlaybackPositionSnapshotCodecTest'
```

Expected: FAIL，编译错误指出 `PlaybackPositionSnapshot`、编解码器和转换函数尚不存在。

- [ ] **Step 3：实现最小快照契约**

新增：

```kotlin
package com.local.mediaviewer.service

import android.os.Bundle
import com.local.mediaviewer.queue.PlaybackSessionState

const val ACTION_GET_EXACT_PLAYBACK_POSITION =
    "com.local.mediaviewer.action.GET_EXACT_PLAYBACK_POSITION"

data class PlaybackPositionSnapshot(
    val mediaKey: String,
    val positionMs: Long,
    val durationMs: Long,
)

fun PlaybackSessionState.toPlaybackPositionSnapshot(): PlaybackPositionSnapshot? {
    val mediaKey = currentItem?.mediaKey
        ?: queue.currentMediaKey
        ?: return null
    if (mediaKey.isBlank()) return null
    val durationMs = playback.durationMs.coerceAtLeast(0L)
    val positionMs = playback.positionMs.coerceAtLeast(0L).let { position ->
        if (durationMs > 0L) position.coerceAtMost(durationMs) else position
    }
    return PlaybackPositionSnapshot(mediaKey, positionMs, durationMs)
}

object PlaybackPositionSnapshotCodec {
    private const val KEY_MEDIA_KEY = "media_key"
    private const val KEY_POSITION_MS = "position_ms"
    private const val KEY_DURATION_MS = "duration_ms"

    fun encode(snapshot: PlaybackPositionSnapshot): Bundle {
        val durationMs = snapshot.durationMs.coerceAtLeast(0L)
        val positionMs = snapshot.positionMs.coerceAtLeast(0L).let { position ->
            if (durationMs > 0L) position.coerceAtMost(durationMs) else position
        }
        return Bundle().apply {
            putString(KEY_MEDIA_KEY, snapshot.mediaKey)
            putLong(KEY_POSITION_MS, positionMs)
            putLong(KEY_DURATION_MS, durationMs)
        }
    }

    fun decode(bundle: Bundle): PlaybackPositionSnapshot? {
        val mediaKey = bundle.getString(KEY_MEDIA_KEY)
            ?.takeIf(String::isNotBlank)
            ?: return null
        if (!bundle.containsKey(KEY_POSITION_MS) ||
            !bundle.containsKey(KEY_DURATION_MS)
        ) return null
        val positionMs = bundle.getLong(KEY_POSITION_MS)
        val durationMs = bundle.getLong(KEY_DURATION_MS)
        if (positionMs < 0L || durationMs < 0L) return null
        return PlaybackPositionSnapshot(
            mediaKey = mediaKey,
            positionMs = if (durationMs > 0L) {
                positionMs.coerceAtMost(durationMs)
            } else {
                positionMs
            },
            durationMs = durationMs,
        )
    }
}
```

- [ ] **Step 4：只重新运行失败测试并确认 GREEN**

Run 与 Step 2 相同。Expected: PASS。

- [ ] **Step 5：检查并提交快照契约**

```powershell
git diff --check -- app/src/main/java/com/local/mediaviewer/service/PlaybackPositionSnapshotCodec.kt app/src/test/java/com/local/mediaviewer/service/PlaybackPositionSnapshotCodecTest.kt
git add -- app/src/main/java/com/local/mediaviewer/service/PlaybackPositionSnapshotCodec.kt app/src/test/java/com/local/mediaviewer/service/PlaybackPositionSnapshotCodecTest.kt
git commit -m "feat(android): define exact playback position snapshots"
```

---

### Task 2：让 MediaSession 返回播放内核位置

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/service/PlaybackSessionCallback.kt:20-104`
- Modify: `app/src/test/java/com/local/mediaviewer/service/PlaybackSessionCallbackTest.kt:98-295`

**Interfaces:**
- Consumes: Task 1 的 `ACTION_GET_EXACT_PLAYBACK_POSITION`、`toPlaybackPositionSnapshot()` 和 `PlaybackPositionSnapshotCodec.encode()`。
- Produces: 成功时 `SessionResult(RESULT_SUCCESS, encodedSnapshot)`。
- Produces: 无当前媒体时 `SessionResult(RESULT_ERROR_INVALID_STATE)` 和默认空 extras。

- [ ] **Step 1：编写命令公开和返回值失败测试**

在 `PlaybackSessionCallbackTest` 增加：

```kotlin
@Test
fun `exact position command returns coordinator snapshot and rejects empty queue`() = runTest {
    val engine = ServiceTestEngine()
    val coordinator = serviceTestCoordinator(scope = this, engine = engine)
    val callback = PlaybackSessionCallback(coordinator, this)
    val sessionFixture = mediaSession(coordinator, this)
    val controller = controllerInfo()
    val command = SessionCommand(ACTION_GET_EXACT_PLAYBACK_POSITION, Bundle.EMPTY)

    assertTrue(
        callback.onConnect(sessionFixture.session, controller)
            .availableSessionCommands.contains(command),
    )
    assertEquals(
        SessionResult.RESULT_ERROR_INVALID_STATE,
        callback.onCustomCommand(
            sessionFixture.session,
            controller,
            command,
            Bundle.EMPTY,
        ).get().resultCode,
    )

    coordinator.replaceQueue(listOf(serviceTestItem("video-a")), "video-a")
    advanceUntilIdle()
    engine.emit(
        PlaybackState(
            status = PlaybackStatus.PLAYING,
            positionMs = 12_500L,
            durationMs = 60_000L,
            isSeekable = true,
        ),
    )
    advanceUntilIdle()

    val result = callback.onCustomCommand(
        sessionFixture.session,
        controller,
        command,
        Bundle.EMPTY,
    ).get()
    assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
    assertEquals(
        PlaybackPositionSnapshot("video-a", 12_500L, 60_000L),
        PlaybackPositionSnapshotCodec.decode(result.extras),
    )

    sessionFixture.session.release()
    sessionFixture.player.release()
    coordinator.close()
}
```

- [ ] **Step 2：运行回调测试并确认 RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests '*PlaybackSessionCallbackTest'
```

Expected: 新测试 FAIL；可用命令中不存在该 action，或回调返回 `ERROR_NOT_SUPPORTED`。

- [ ] **Step 3：公开命令并同步返回当前快照**

在 `PlaybackSessionCallback` 增加命令字段并加入连接结果：

```kotlin
private val exactPositionCommand = SessionCommand(
    ACTION_GET_EXACT_PLAYBACK_POSITION,
    Bundle.EMPTY,
)
```

`onConnect` 的命令构造链明确增加：

```kotlin
.add(retryPersistenceCommand)
.add(exactPositionCommand)
.build()
```

在 `onCustomCommand` 的 `when` 中加入：

```kotlin
ACTION_GET_EXACT_PLAYBACK_POSITION -> {
    val snapshot = coordinator.sessionState.value
        .toPlaybackPositionSnapshot()
    Futures.immediateFuture(
        if (snapshot == null) {
            SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE)
        } else {
            SessionResult(
                SessionResult.RESULT_SUCCESS,
                PlaybackPositionSnapshotCodec.encode(snapshot),
            )
        },
    )
}
```

该分支不启动新协程、不访问 LibVLC 对象，也不改变队列或播放状态。

- [ ] **Step 4：只重新运行失败的回调测试并确认 GREEN**

Run 与 Step 2 相同。Expected: PASS，现有停止、重载和重试命令测试也保持通过。

- [ ] **Step 5：提交服务端位置命令**

```powershell
git diff --check -- app/src/main/java/com/local/mediaviewer/service/PlaybackSessionCallback.kt app/src/test/java/com/local/mediaviewer/service/PlaybackSessionCallbackTest.kt
git add -- app/src/main/java/com/local/mediaviewer/service/PlaybackSessionCallback.kt app/src/test/java/com/local/mediaviewer/service/PlaybackSessionCallbackTest.kt
git commit -m "feat(android): expose exact playback position command"
```

---

### Task 3：建立带媒体身份的客户端位置缓存

**Files:**
- Create: `app/src/main/java/com/local/mediaviewer/player/ExactPlaybackPositionStore.kt`
- Create: `app/src/test/java/com/local/mediaviewer/player/ExactPlaybackPositionStoreTest.kt`

**Interfaces:**
- Consumes: Task 1 的 `PlaybackPositionSnapshot`。
- Produces: `accept(currentMediaKey: String?, candidate: PlaybackPositionSnapshot): Boolean`。
- Produces: `positionFor(currentMediaKey: String?): Long`，无匹配快照时返回 `0L`。
- Produces: `clear()`，供断开、关闭和重连时使用。

- [ ] **Step 1：编写媒体切换、失败保持和向后 seek 失败测试**

```kotlin
class ExactPlaybackPositionStoreTest {
    @Test
    fun `只接受当前媒体并在切换时清除旧位置`() {
        val store = ExactPlaybackPositionStore()

        assertFalse(
            store.accept(
                "video-b",
                PlaybackPositionSnapshot("video-a", 9_000L, 60_000L),
            ),
        )
        assertEquals(0L, store.positionFor("video-b"))

        assertTrue(
            store.accept(
                "video-b",
                PlaybackPositionSnapshot("video-b", 12_000L, 60_000L),
            ),
        )
        assertEquals(12_000L, store.positionFor("video-b"))
        assertEquals(0L, store.positionFor("video-c"))
    }

    @Test
    fun `暂停请求失败保持快照且向后 seek 可以覆盖`() {
        val store = ExactPlaybackPositionStore()
        store.accept(
            "video-a",
            PlaybackPositionSnapshot("video-a", 20_000L, 60_000L),
        )

        assertEquals(20_000L, store.positionFor("video-a"))
        store.accept(
            "video-a",
            PlaybackPositionSnapshot("video-a", 5_000L, 60_000L),
        )
        assertEquals(5_000L, store.positionFor("video-a"))

        store.clear()
        assertEquals(0L, store.positionFor("video-a"))
    }
}
```

“请求失败保持”通过不调用 `accept` 表达；缓存不得建立自己的计时器或墙钟字段。

- [ ] **Step 2：运行新测试并确认 RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests '*ExactPlaybackPositionStoreTest'
```

Expected: FAIL，`ExactPlaybackPositionStore` 尚不存在。

- [ ] **Step 3：实现最小身份缓存**

```kotlin
package com.local.mediaviewer.player

import com.local.mediaviewer.service.PlaybackPositionSnapshot

internal class ExactPlaybackPositionStore {
    private var latest: PlaybackPositionSnapshot? = null

    fun accept(
        currentMediaKey: String?,
        candidate: PlaybackPositionSnapshot,
    ): Boolean {
        if (currentMediaKey == null || candidate.mediaKey != currentMediaKey) {
            return false
        }
        latest = candidate
        return true
    }

    fun positionFor(currentMediaKey: String?): Long {
        val snapshot = latest
        if (currentMediaKey == null || snapshot?.mediaKey != currentMediaKey) {
            latest = null
            return 0L
        }
        return snapshot.positionMs
    }

    fun clear() {
        latest = null
    }
}
```

- [ ] **Step 4：只重新运行失败测试并确认 GREEN**

Run 与 Step 2 相同。Expected: PASS。

- [ ] **Step 5：提交客户端位置缓存**

```powershell
git diff --check -- app/src/main/java/com/local/mediaviewer/player/ExactPlaybackPositionStore.kt app/src/test/java/com/local/mediaviewer/player/ExactPlaybackPositionStoreTest.kt
git add -- app/src/main/java/com/local/mediaviewer/player/ExactPlaybackPositionStore.kt app/src/test/java/com/local/mediaviewer/player/ExactPlaybackPositionStoreTest.kt
git commit -m "feat(android): track exact playback positions by media"
```

---

### Task 4：用权威位置替换 MediaController 墙钟估值

**Files:**
- Modify: `app/src/main/java/com/local/mediaviewer/player/Media3PlaybackController.kt:55-568`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/MediaSessionControlsTest.kt:51-510`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/testing/BackgroundPlaybackTestHarness.kt:50-334`

**Interfaces:**
- Consumes: Tasks 1-3 的命令 action、编解码器和 `ExactPlaybackPositionStore`。
- Preserves: `Media3StateMapper`、队列命令、播放器通知、连接状态机和本地视频输出绑定。
- Produces: 应用 UI 的 `PlaybackState.positionMs` 只来自匹配当前媒体的权威快照。

- [ ] **Step 1：为 Android MediaSession 测试注入可冻结的播放内核**

让 `BackgroundPlaybackTestHarness` 和 `BackgroundPlaybackAppContainer` 接受默认值为 `null` 的 `PlaybackEngineFactory`：

```kotlin
class BackgroundPlaybackTestHarness(
    private val playbackEngineFactory: PlaybackEngineFactory? = null,
) : Closeable {
    val container = BackgroundPlaybackAppContainer(
        context = application,
        requestBaseUrl = server.url("/").trimEnd('/'),
        playbackEngineFactory = playbackEngineFactory,
    )
}

class BackgroundPlaybackAppContainer(
    context: Context,
    requestBaseUrl: String,
    private val playbackEngineFactory: PlaybackEngineFactory? = null,
) : AppContainer, Closeable {
    override fun createPlaybackCoordinator(scope: CoroutineScope): PlaybackCoordinator {
        playbackEngineCreationCount += 1
        val engine = CountingPlaybackEngine(
            playbackEngineFactory?.create()
                ?: AndroidVlcPlaybackEngine(appContext),
        ) {
            playbackEngineCloseCount += 1
        }
        return PlaybackCoordinator(
            engine = engine,
            queueRepository = queueRepository,
            positionStore = positionStore,
            session = sessionManager,
            scope = scope,
        ).start()
    }
}
```

默认构造路径保持真实 `AndroidVlcPlaybackEngine`，现有设备测试行为不得改变。

- [ ] **Step 2：编写能够稳定复现客户端外推的失败测试**

在 `MediaSessionControlsTest` 增加仅供本类使用的 `FrozenPositionEngine`。它只响应显式命令和 `emit`，不建立计时器：

```kotlin
private class FrozenPositionEngine : PlaybackEngine {
    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    fun emit(state: PlaybackState) {
        mutableState.value = state
    }

    override fun prepare(url: String) {
        mutableState.value = PlaybackState(
            status = PlaybackStatus.PAUSED,
            durationMs = 60_000L,
            isSeekable = true,
        )
    }

    override fun attachVideoOutput(host: ViewGroup) = Unit

    override fun detachVideoOutput() = Unit

    override fun setVideoScaleMode(mode: VideoScaleMode) = Unit

    override fun setPlaybackSpeed(speed: Float) {
        mutableState.value = mutableState.value.copy(playbackSpeed = speed)
    }

    override fun play() {
        mutableState.value = mutableState.value.copy(
            status = PlaybackStatus.PLAYING,
        )
    }

    override fun pause() {
        mutableState.value = mutableState.value.copy(
            status = PlaybackStatus.PAUSED,
        )
    }

    override fun stop() {
        mutableState.value = PlaybackState()
    }

    override fun seekTo(positionMs: Long) {
        mutableState.value = mutableState.value.copy(
            positionMs = positionMs.coerceAtLeast(0L),
        )
    }

    override fun close() = Unit
}
```

新增测试：

```kotlin
@Test
fun appProgressUsesFrozenEnginePositionAfterPauseResumeAndAtDoubleSpeed() {
    val engine = FrozenPositionEngine()
    BackgroundPlaybackTestHarness(
        playbackEngineFactory = PlaybackEngineFactory { engine },
    ).use { harness ->
        val appController =
            harness.container.playbackController as Media3PlaybackController
        harness.connectController().use { systemController ->
            systemController.run {
                setMediaItems(harness.videoQueue())
                prepare()
                play()
            }
            harness.waitUntil("queue reaches app controller") {
                appController.sessionState.value.currentItem != null
            }

            engine.emit(
                PlaybackState(
                    status = PlaybackStatus.PLAYING,
                    positionMs = 8_000L,
                    durationMs = 60_000L,
                    isSeekable = true,
                    playbackSpeed = 1f,
                ),
            )
            harness.waitUntil("first frozen position reaches app controller") {
                appController.state.value.positionMs in 8_000L..8_250L
            }
            Thread.sleep(750L)
            assertEquals(8_000L, appController.state.value.positionMs)

            systemController.run { setPlaybackSpeed(2f) }
            engine.emit(
                PlaybackState(
                    status = PlaybackStatus.PLAYING,
                    positionMs = 12_000L,
                    durationMs = 60_000L,
                    isSeekable = true,
                    playbackSpeed = 2f,
                ),
            )
            harness.waitUntil("double speed snapshot reaches app controller") {
                appController.state.value.positionMs in 12_000L..12_250L
            }
            Thread.sleep(500L)
            assertEquals(12_000L, appController.state.value.positionMs)

            systemController.run { pause() }
            engine.emit(
                engine.state.value.copy(status = PlaybackStatus.PAUSED),
            )
            harness.waitUntil("pause reaches app controller") {
                appController.state.value.status == PlaybackStatus.PAUSED
            }
            systemController.run { play() }
            engine.emit(
                engine.state.value.copy(status = PlaybackStatus.PLAYING),
            )
            harness.waitUntil("resume reaches app controller") {
                appController.state.value.status == PlaybackStatus.PLAYING
            }
            Thread.sleep(750L)
            assertEquals(12_000L, appController.state.value.positionMs)
        }
    }
}
```

该测试经过真实 `MediaController` 和 `MediaSession`，但把播放内核位置冻结，因此旧实现会按墙钟推进并稳定 RED；它不依赖视频解码速度或目视判断。

- [ ] **Step 3：运行复现测试并确认 RED**

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaSessionControlsTest#appProgressUsesFrozenEnginePositionAfterPauseResumeAndAtDoubleSpeed'
```

Expected: FAIL，至少一个 `assertEquals` 得到大于 8,000 或 12,000 的 Media3 外推位置。记录实际值；不得扩大容差来让错误行为通过。没有设备时先运行 `compileDebugAndroidTestKotlin`，并把动态复现记为 `NOT RUN`。

- [ ] **Step 4：把位置观察器改为立即且串行的自定义命令采样**

在 `Media3PlaybackController` 增加：

```kotlin
private val exactPositionStore = ExactPlaybackPositionStore()
private val exactPositionCommand = SessionCommand(
    ACTION_GET_EXACT_PLAYBACK_POSITION,
    Bundle.EMPTY,
)
```

将 `startPositionObserver` 改为：

```kotlin
private fun startPositionObserver(handle: MediaControllerHandle) {
    positionObserver?.cancel()
    positionObserverOwner = handle
    exactPositionStore.clear()
    positionObserver = scope.launch {
        while (isActive && !closed && connectionMachine.isCurrent(handle)) {
            val result = try {
                handle.controller.sendCustomCommand(
                    exactPositionCommand,
                    Bundle.EMPTY,
                ).await()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (!connectionMachine.isCurrent(handle)) break
            if (result?.resultCode == SessionResult.RESULT_SUCCESS) {
                PlaybackPositionSnapshotCodec.decode(result.extras)?.let { snapshot ->
                    exactPositionStore.accept(
                        handle.controller.currentMediaItem?.mediaId,
                        snapshot,
                    )
                }
            }
            publish(handle.controller)
            delay(POSITION_OBSERVER_INTERVAL_MS)
        }
    }
}
```

普通请求异常被限制在本轮位置请求；不得调用 `connectionMachine.onConnectionFailed`。必须重新抛出 `CancellationException`，让断线、关闭和作用域取消立即终止轮询。

- [ ] **Step 5：从 UI 状态快照中删除 `MediaController.currentPosition`**

把 `Player.snapshot()` 从表达式函数改为块函数：

```kotlin
private fun Player.snapshot(): Media3StateSnapshot {
    val currentMediaKey = currentMediaItem?.mediaId
    return Media3StateSnapshot(
        playbackState = playbackState,
        playWhenReady = playWhenReady,
        isPlaying = isPlaying,
        positionMs = exactPositionStore.positionFor(currentMediaKey),
        durationMs = duration,
        bufferedPositionMs = bufferedPosition,
        isSeekable = isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
        errorMessage = playerError?.message,
        items = (0 until mediaItemCount).map {
            MediaItemMapper.fromMedia3(getMediaItemAt(it))
        },
        currentMediaItemIndex = currentMediaItemIndex,
        canSkipPrevious = isCommandAvailable(
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        ),
        canSkipNext = isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM),
        repeatMode = repeatMode,
        shuffleModeEnabled = shuffleModeEnabled,
        playbackSpeed = playbackParameters.speed,
    )
}
```

在释放当前 `positionObserverOwner`、控制器关闭和断线重连时调用 `exactPositionStore.clear()`；释放旧的非当前 handle 时不得清除新连接的缓存。

- [ ] **Step 6：只重新运行失败的复现测试并确认 GREEN**

Run 与 Step 3 相同。Expected: PASS；1.0x、2.0x 和暂停恢复三个阶段都保持播放内核的冻结位置。

- [ ] **Step 7：执行静态唯一时钟门禁**

```powershell
rg -n "currentPosition" app/src/main/java/com/local/mediaviewer/player/Media3PlaybackController.kt
```

Expected: 无输出。`currentMediaItemIndex` 不匹配该大小写字符串，不影响门禁。

- [ ] **Step 8：只重验 Task 4 其他未通过的测试**

设备可用时只运行仍失败的测试方法，不重跑已经 GREEN 的冻结位置用例。设备不可用时运行：

```powershell
.\gradlew.bat compileDebugAndroidTestKotlin
```

Expected: 编译 PASS；未运行的设备断言仍记录为 `NOT RUN`。

- [ ] **Step 9：运行本次新增 JVM 测试集合**

如果 Tasks 1-3 的测试均已有新鲜 PASS 结果，不重复运行。只有存在未通过项时，按失败类运行：

```powershell
.\gradlew.bat testDebugUnitTest --tests '*PlaybackPositionSnapshotCodecTest' --tests '*PlaybackSessionCallbackTest' --tests '*ExactPlaybackPositionStoreTest'
```

- [ ] **Step 10：提交客户端真实位置接入和测试夹具**

```powershell
git diff --check -- app/src/main/java/com/local/mediaviewer/player/Media3PlaybackController.kt app/src/androidTest/java/com/local/mediaviewer/MediaSessionControlsTest.kt app/src/androidTest/java/com/local/mediaviewer/testing/BackgroundPlaybackTestHarness.kt
git add -- app/src/main/java/com/local/mediaviewer/player/Media3PlaybackController.kt app/src/androidTest/java/com/local/mediaviewer/MediaSessionControlsTest.kt app/src/androidTest/java/com/local/mediaviewer/testing/BackgroundPlaybackTestHarness.kt
git commit -m "fix(android): use exact engine positions in player UI"
```

---

### Task 5：记录基础验证并构建 ARM64 Release

**Files:**
- Create: `docs/verification/2026-08-02-player-resume-exact-progress.md`
- Modify: `docs/verification/2026-08-02-player-progress-controls-layout.md`
- Update expected artifact: `dist/mediaviewer-v1.1.0-arm64-v8a-release.apk`
- Update expected artifact: `dist/mediaviewer-v1.1.0-arm64-v8a-release.apk.sha256`

**Interfaces:**
- Consumes: Tasks 1-4 的提交和新鲜测试结果。
- Produces: 自动测试、设备测试、未运行项和 Release 产物互相分离的证据记录。

- [ ] **Step 1：先运行未覆盖的基础 JVM 门禁**

Tasks 1-4 已通过的定向类不重复运行。执行一次其余 JVM 套件门禁：

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: 全部 PASS。若失败，记录失败类和用例，修复后只重跑失败类；最终 Release 脚本仍会按其既定供应链门禁重新执行完整 JVM 测试。

- [ ] **Step 2：运行基础 Android 编译、Lint 和 Debug 构建**

```powershell
.\gradlew.bat compileDebugAndroidTestKotlin lintDebug assembleDebug
```

Expected: exit 0；Lint 无 error。已经通过的设备测试不重复执行。

- [ ] **Step 3：执行真机人工场景或明确 NOT RUN**

在 arm64 真机上用同一真实视频分别执行 1.0x 和 2.0x：

1. 播放至少 10 秒；
2. 暂停并等待 5 秒；
3. 恢复，观察时间数字、滑块、声音和画面；
4. 向前 seek，再向后 seek；
5. 再次暂停和恢复。

通过标准：暂停期间时间和滑块不动；恢复后不领先画面持续加速；2.0x 不发生 UI 二次乘速；向后 seek 可立即回退。没有 arm64 真机时整项写为 `NOT RUN`。

- [ ] **Step 4：编写新的验证记录并交叉引用旧记录**

新文档必须记录：

- 根因文档、设计规格、实施计划和源提交；
- 每个 RED 的实际失败原因；
- 每个 GREEN 的命令、时间和测试数量；
- `rg currentPosition` 唯一时钟门禁；
- Android 编译、Lint、Debug 构建结果；
- 真机结果或 `NOT RUN`；
- VLC4 报告层仍是未证实的独立风险，不将其写成已修复。

在旧验证文档末尾只追加：“`19fb089` 的会话侧修复未覆盖 MediaController 客户端外推，后续修复及结果见新文档。”保留旧记录中的历史 PASS/NOT RUN 原文。

- [ ] **Step 5：提交验证记录**

```powershell
git add -- docs/verification/2026-08-02-player-resume-exact-progress.md docs/verification/2026-08-02-player-progress-controls-layout.md
git commit -m "docs(android): record exact progress verification"
```

- [ ] **Step 6：运行个人 ARM64 Release 构建脚本**

确认工作树满足脚本门禁后运行：

```powershell
.\scripts\Build-PersonalRelease.ps1 -SdkRoot 'C:\Users\Administrator\AppData\Local\Android\Sdk'
```

Expected: `testDebugUnitTest`、`lintRelease`、`assembleRelease`、ZIP 对齐、签名、ABI 和体积门禁全部成功；产物唯一 Native ABI 为 `arm64-v8a`，体积不超过 70 MiB。

若脚本因现有未跟踪文件要求干净工作树，只临时保护脚本明确冲突的文件并在构建后恢复；不得删除或提交 `.superpowers/brainstorm/`、`docs/analysis/` 和其他用户文件。

- [ ] **Step 7：独立校验最终产物**

```powershell
$apk = Resolve-Path -LiteralPath '.\dist\mediaviewer-v1.1.0-arm64-v8a-release.apk'
Get-Item -LiteralPath $apk | Select-Object FullName,Length,LastWriteTime
Get-FileHash -LiteralPath $apk -Algorithm SHA256
Get-Content -LiteralPath "$apk.sha256"
```

再使用 Android SDK 的 `apkanalyzer` 或 `aapt2` 读取应用 ID、版本和 ABI，并使用 `apksigner verify --verbose --print-certs` 检查签名。验证文档记录证书用途限制，不在日志中打印密码。

- [ ] **Step 8：追加 Release 证据并提交**

在新验证文档追加源提交、APK 绝对路径、字节数、SHA-256、ABI、对齐、签名方案和真机安装状态：

```powershell
git add -- docs/verification/2026-08-02-player-resume-exact-progress.md
git commit -m "docs(android): record exact progress release"
```

- [ ] **Step 9：完成前基础功能性审查**

```powershell
git diff a30c8f8..HEAD --check
git status --short
git log -8 --oneline
```

逐项确认：

- UI 位置路径没有 `MediaController.currentPosition`；
- 自定义位置命令只读状态，不修改播放；
- 暂停、恢复、2.0x、向前/向后 seek 和媒体切换有测试或明确设备边界；
- 视频与音频共享修复，视频专属逻辑没有进入音频；
- VLC4/vout 未证实因素没有被误写为已解决；
- 无关未跟踪文件没有被暂存或提交；
- 未运行的真机项目没有被写成 PASS。

---

## 最终交付内容

- 权威位置快照、Bundle 编解码和 MediaSession 自定义命令。
- 带媒体身份校验的应用控制器串行采样实现。
- JVM、Robolectric 和 Android MediaSession 回归测试。
- 中文验证记录及旧验证记录的历史更正引用。
- 更新后的 `dist/mediaviewer-v1.1.0-arm64-v8a-release.apk` 和 `.sha256`。
- 自动测试、设备测试、真机人工检查和未运行项分开报告。
