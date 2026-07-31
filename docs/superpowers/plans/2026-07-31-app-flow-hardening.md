# MediaViewer App Flow Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以测试先行方式修复 F1–F7 / I1–I5 的 P0/P1 主流程问题，使服务器会话、目录浏览、播放器启动、设置保存、图片恢复、后台持久化提示、返回栈和冷恢复都具备有限等待、明确错误和安全恢复路径。

**Architecture:** 保持现有 `ServerSessionManager`、Media3 `MediaController`、`MediaSessionService`、LibVLC、Room、DataStore 与 Navigation Compose 架构。新增应用级会话展示状态、播放器入口状态和一次性播放通知通道；各页面 ViewModel 只拥有业务状态，`MediaViewerApp.kt` 由唯一集成负责人统一消费并接线。服务仍是唯一播放器与队列真相来源，界面不复制队列或播放引擎状态。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、Navigation Compose、Kotlin Coroutines/Flow、AndroidX Media3、Room、DataStore、JUnit 4、kotlinx-coroutines-test、Compose UI Test、Gradle Android Plugin。

## Global Constraints

- 本计划只处理已批准的 P0/P1 流程 F1–F7 和 I1–I5；不加入字幕、多音轨、投屏、画中画、在线搜索、账号、复杂鉴权、遥测或企业级审查逻辑。
- 保持后台播放语义：应用退到后台后声音继续，视频 Surface 分离并停留在离开前画面，回前台后按服务中的真实位置恢复画面。
- 保持队列语义：手动加入、插入下一项、调整顺序、删除和跨重启恢复；任何提示失败都不得清空内存队列或停止当前播放。
- 稳定身份始终使用 `mediaKey` / logical URL；IPv4 request URL 只用于当前网络请求，不进入导航来源或持久队列身份。
- **`app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt` 只有 Task 7 的同一名集成负责人可以修改。Task 1–6 不得提前改它；Task 8 发现根接线问题时必须把修复退回 Task 7 的负责人，不得由回归负责人交叉修改。**
- **F2 → F6 严格串行。** 两者共同修改 `PlaybackController.kt` 和 `Media3PlaybackController.kt`；F2 完成、测试通过并提交后，F6 才能开始。
- F1、F2、F3、F4、F5 的 **ViewModel/纯状态** 红灯与实现可以并行。Task 3/4/5 的 Screen wiring 必须等待界面地基 Tasks 1–3，不能与这些共享组件的创建并行。
- Screen 与测试文件按所有权串行交接：flow Task 3 → foundation Task 5（`BrowserScreen.kt`/`BrowserScreenTest.kt`），flow Task 4 → foundation Task 6（`SettingsScreen.kt`/`HomeSettingsScreenTest.kt`），flow Task 5 → foundation Task 7（`ImageReaderScreen.kt`/`ImageReaderScreenTest.kt`）。后继任务开始前必须包含前驱提交。
- Task 7 等待本计划 Task 1–6、界面地基计划 Tasks 1–7 和播放器计划 Tasks 1–6 全部提交；Task 8 等待 Task 7。禁止多代理同时修改共享文件。
- 所有 Gradle 命令由单一执行者串行运行，并始终追加 `'-Pkotlin.incremental=false'`，避免 Windows Kotlin 增量缓存并发损坏。
- 单元测试通过不等于真机、真实服务器、通知、锁屏或系统杀进程验收；最终报告分别列出 JVM、AndroidTest 编译、模拟器/设备、真实服务器、人工场景的 `PASS` 或 `NOT RUN`。
- 每个任务遵循红—绿—重构：先添加一个能在旧实现上以指定原因失败的测试，确认失败，再写最小实现；禁止先改生产代码后补测试。
- 每次提交只暂存任务列出的文件。工作树中的既有未跟踪规范和验证文档不属于本计划提交范围。

## Dependency and Ownership Map

| Wave | Tasks | Parallel rule | Gate |
|---|---|---|---|
| 0 | foundation Tasks 1–3 | 可与 flow 纯状态测试准备并行 | 主题与共享 Scaffold/State/Dialog/Snackbar 接口稳定 |
| 1A | Task 1 / 2 / 3-state / 4-state / 5-state | 可并行，生产文件不重叠 | 各自目标 JVM 测试通过 |
| 1B | Task 3-screen / 4-screen / 5-screen | 可互相并行；分别独占 Browser/Settings/Image 文件 | foundation Tasks 1–3 已完成 |
| 2 | Task 6 | 只能在 Task 2 提交后开始 | 播放通知编解码、服务桥和控制器测试通过 |
| 3 | foundation Tasks 5–7 / player Tasks 1–6 | 按各自计划执行；共享 Screen/Test 接收 1B 所有权 | 各前驱 flow 状态/接线提交已包含 |
| 4 | Task 7 | 单一集成负责人；独占 `MediaViewerApp.kt` | 本计划 Task 1–6、界面地基 Tasks 1–7、播放器 Tasks 1–6 全部完成 |
| 5 | Task 8 | 单一 Gradle/设备执行者 | Task 7 完成；只写测试与证据 |

---

### Task 1: F1 — Move Server Connection Ownership to an App-Level Session ViewModel

**Files:**

- Create: `app/src/main/java/com/local/mediaviewer/app/AppSessionViewModel.kt`
- Create: `app/src/test/java/com/local/mediaviewer/app/AppSessionViewModelTest.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/home/HomeViewModel.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/home/HomeViewModelTest.kt`
- Do not modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`

**Interfaces consumed:** `ServerSessionManager.state`, `ServerSessionManager.connectSaved()`, `ServerSettingsRepository.current()`, `ServerConfig.lastSuccessfulIpv4`.

**Interfaces produced:**

```kotlin
data class AppSessionUiState(
    val current: ServerSessionState = ServerSessionState.Connecting,
    val lastConnected: ServerSessionState.Connected? = null,
    val needsConfiguration: Boolean = false,
)

class AppSessionViewModel(
    private val session: ServerSessionManager,
    private val settings: ServerSettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<AppSessionUiState>
    fun retry()
}
```

`AppSessionViewModel` 在自身 `init` 中且仅在自身生命周期内调用一次 `connectSaved()`。`lastConnected` 只用于重连期间保留可见内容，不能覆盖 `current`，也不能成为新请求端点。只有 `current is Failed` 且启动前配置的 `lastSuccessfulIpv4 == null` 时才设置 `needsConfiguration = true`。

- [ ] **Step 1: Add the failing app-session ownership tests**

在 `AppSessionViewModelTest.kt` 添加以下核心测试；fake 的 `state` 使用 `MutableStateFlow<ServerSessionState>`，`connectSaved()` 只递增 `connectCalls`：

```kotlin
@Test
fun `app session connects once and retains the last connected snapshot`() = runTest(dispatcher) {
    val old = connected("192.0.2.10")
    val replacement = connected("192.0.2.11")
    val session = FakeServerSession(ServerSessionState.Connecting)
    val viewModel = AppSessionViewModel(
        session = session,
        settings = FakeServerSettings(ServerConfig(lastSuccessfulIpv4 = "192.0.2.10")),
    )

    advanceUntilIdle()
    assertEquals(1, session.connectCalls)
    session.emit(old)
    runCurrent()
    session.emit(ServerSessionState.Connecting)
    runCurrent()
    assertEquals(old, viewModel.uiState.value.lastConnected)
    assertEquals(ServerSessionState.Connecting, viewModel.uiState.value.current)

    session.emit(replacement)
    runCurrent()
    assertEquals(replacement, viewModel.uiState.value.lastConnected)
}

@Test
fun `first failure without a successful endpoint requests configuration`() = runTest(dispatcher) {
    val session = FakeServerSession(ServerSessionState.Connecting)
    val viewModel = AppSessionViewModel(
        session = session,
        settings = FakeServerSettings(ServerConfig(lastSuccessfulIpv4 = null)),
    )
    advanceUntilIdle()

    session.emit(ServerSessionState.Failed(
        error = AppError.NetworkFailure("offline"),
        resolvedIpv4s = emptyList(),
    ))
    runCurrent()

    assertTrue(viewModel.uiState.value.needsConfiguration)
    assertNull(viewModel.uiState.value.lastConnected)
}
```

同一测试文件补齐以下 fake；显式导入 `AppError`、`AppResult`、`ConnectionTestResult`、`ServerConfig`、`SessionEndpoint`、`ServerSessionManager`、`ServerSessionState`、`ServerSettingsRepository`、`Flow`、`MutableStateFlow` 与 `StateFlow`：

```kotlin
private class FakeServerSession(
    initial: ServerSessionState,
) : ServerSessionManager {
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<ServerSessionState> = mutableState
    var connectCalls = 0
        private set

    override suspend fun connectSaved() {
        connectCalls += 1
    }

    fun emit(next: ServerSessionState) {
        mutableState.value = next
    }

    override suspend fun testCandidate(
        input: String,
    ): AppResult<ConnectionTestResult> =
        error("unused testCandidate: $input")

    override suspend fun saveCandidate(result: ConnectionTestResult) {
        error("unused saveCandidate: ${result.server.logicalBaseUrl}")
    }

    override suspend fun refreshAfterRequestFailure(): AppResult<SessionEndpoint> =
        AppResult.Failure(AppError.NetworkFailure("unused"))
}

private class FakeServerSettings(
    initial: ServerConfig,
) : ServerSettingsRepository {
    private val mutableConfig = MutableStateFlow(initial)
    override val config: Flow<ServerConfig> = mutableConfig

    override suspend fun current(): ServerConfig = mutableConfig.value

    override suspend fun save(config: ServerConfig) {
        mutableConfig.value = config
    }
}
```

在 `HomeViewModelTest.kt` **替换**现有“创建时连接并映射选中 IPv4”和“失败状态显示中文错误且重试再次连接”两个测试；不能保留与 app-owned connection 相反的旧断言。给 `HomeFakeSession` 增加只修改其 `MutableStateFlow` 的 `emit(state)`：

```kotlin
@Test
fun `creating home does not connect and still maps session state`() = runTest(dispatcher) {
    val session = HomeFakeSession()
    val viewModel = HomeViewModel(session)
    advanceUntilIdle()
    assertEquals(0, session.connectCalls)

    session.emit(connected("192.168.1.17"))
    runCurrent()
    assertEquals(
        HomeUiState.Connected("192.168.1.17", listOf(HOME_SHARE)),
        viewModel.uiState.value,
    )
}

@Test
fun `explicit home retry connects exactly once`() = runTest(dispatcher) {
    val session = HomeFakeSession()
    val viewModel = HomeViewModel(session)
    viewModel.retry()
    advanceUntilIdle()
    assertEquals(1, session.connectCalls)
}

private fun connected(ipv4: String) = ServerSessionState.Connected(
    endpoint = SessionEndpoint(
        logicalBaseUrl = "http://media.example:8080",
        requestBaseUrl = "http://$ipv4:8080",
        ipv4 = ipv4,
    ),
    resolvedIpv4s = listOf(ipv4),
    shares = listOf(HOME_SHARE),
)
```

- [ ] **Step 2: Run the focused tests and confirm the intended red state**

```powershell
$env:ANDROID_HOME='C:\Users\Administrator\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --tests 'com.local.mediaviewer.app.AppSessionViewModelTest' --tests 'com.local.mediaviewer.home.HomeViewModelTest' '-Pkotlin.incremental=false'
```

Expected: compilation fails because `AppSessionViewModel` / `AppSessionUiState` do not exist；新 Home 断言在旧实现上以 `connectCalls expected 0 but was 1` 失败。旧的两个自动连接断言已经被替换，不会在绿灯阶段形成矛盾。

- [ ] **Step 3: Implement the minimum app-level reducer and remove Home ownership**

在 `AppSessionViewModel.kt` 用一个 reducer 保留最后成功状态：

```kotlin
internal fun reduceAppSession(
    previous: AppSessionUiState,
    next: ServerSessionState,
    hasSuccessfulEndpoint: Boolean,
): AppSessionUiState = AppSessionUiState(
    current = next,
    lastConnected = when (next) {
        is ServerSessionState.Connected -> next
        else -> previous.lastConnected
    },
    needsConfiguration =
        next is ServerSessionState.Failed &&
            previous.lastConnected == null &&
            !hasSuccessfulEndpoint,
)
```

ViewModel 先读取一次 `settings.current()`，再启动 `session.state.collect` 与一次 `connectSaved()`；`retry()` 只显式再次连接。删除 `HomeViewModel.init` 中的自动 `connectSaved()`，保留现有 `retry()`。

- [ ] **Step 4: Run focused and adjacent tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.local.mediaviewer.app.AppSessionViewModelTest' --tests 'com.local.mediaviewer.home.HomeViewModelTest' --tests 'com.local.mediaviewer.session.*' '-Pkotlin.incremental=false'
```

Expected: all selected tests pass; no app session test observes more than one automatic connect.

- [ ] **Step 5: Commit only F1 files**

```powershell
git add app/src/main/java/com/local/mediaviewer/app/AppSessionViewModel.kt app/src/main/java/com/local/mediaviewer/home/HomeViewModel.kt app/src/test/java/com/local/mediaviewer/app/AppSessionViewModelTest.kt app/src/test/java/com/local/mediaviewer/home/HomeViewModelTest.kt
git commit -m "fix(android): own server session at app scope"
```

---

### Task 2: F2 — Model Player Bootstrap, Finite Waiting, and Explicit Reconnect

**Files:**

- Modify: `app/src/main/java/com/local/mediaviewer/navigation/CurrentPlayerNavigation.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/player/PlaybackController.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/player/Media3PlaybackController.kt`
- Verify/modify if the reconnect-generation characterization fails: `app/src/main/java/com/local/mediaviewer/player/ControllerConnectionMachine.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/player/PlayerBootstrapContent.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/navigation/CurrentPlayerNavigationTest.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/player/ControllerConnectionMachineTest.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/player/Media3StateMapperTest.kt`
- Create: `app/src/androidTest/java/com/local/mediaviewer/PlayerBootstrapContentTest.kt`
- Do not modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`

**Interfaces consumed:** `PlaybackSessionState.currentItem`, `PlaybackSessionState.playback.status`, `PlaybackSessionState.errorMessage`, `ControllerConnectionState`.

**Interfaces produced:**

```kotlin
const val PLAYER_ENTRY_WAIT_TIMEOUT_MS: Long = 5_000L

sealed interface PlayerEntryState {
    data object Connecting : PlayerEntryState
    data class Ready(val item: QueueMediaItem) : PlayerEntryState
    data object Empty : PlayerEntryState
    data class Failed(val message: String) : PlayerEntryState
}

fun resolvePlayerEntryState(
    session: PlaybackSessionState,
    hasPresentedItem: Boolean,
    waitExpired: Boolean,
): PlayerEntryState

interface QueuePlaybackController {
    fun reconnect() = onAppStarted()
}
```

优先级固定为 `currentItem -> Ready`、`errorMessage -> Failed`、`hasPresentedItem -> Empty`、`OPENING 或未超时 -> Connecting`、其余 `Empty`。重连只对 `ControllerConnectionMachine` 发起新的 connection generation，不清空服务队列、不调用 stop、不改 `playWhenReady`。

- [ ] **Step 1: Replace ambiguous route tests with failing explicit-state tests**

在 `CurrentPlayerNavigationTest.kt` 添加：

```kotlin
@Test
fun `connection failure wins over the initial waiting state`() {
    val state = resolvePlayerEntryState(
        session = session(currentItem = null, errorMessage = "播放器连接失败"),
        hasPresentedItem = false,
        waitExpired = false,
    )
    assertEquals(PlayerEntryState.Failed("播放器连接失败"), state)
}

@Test
fun `initial idle becomes empty only after the finite wait`() {
    val idle = session(currentItem = null, status = PlaybackStatus.IDLE)
    assertEquals(
        PlayerEntryState.Connecting,
        resolvePlayerEntryState(idle, hasPresentedItem = false, waitExpired = false),
    )
    assertEquals(
        PlayerEntryState.Empty,
        resolvePlayerEntryState(idle, hasPresentedItem = false, waitExpired = true),
    )
}

@Test
fun `a current item always produces ready`() {
    val item = item("video-a")
    assertEquals(
        PlayerEntryState.Ready(item),
        resolvePlayerEntryState(
            session(currentItem = item, errorMessage = "stale error"),
            hasPresentedItem = false,
            waitExpired = true,
        ),
    )
}
```

上述三个测试应**替换**现有“播放器路由在连接窗口等待而在已连接空队列时退出”测试，不保留它；否则 Task 7 删除 `PlayerRouteContent` / `resolvePlayerRouteContent` 兼容包装后会残留旧断言。沿用测试文件已有的 `item(key)`，并补齐：

```kotlin
private fun session(
    currentItem: QueueMediaItem? = null,
    status: PlaybackStatus = PlaybackStatus.IDLE,
    errorMessage: String? = null,
) = PlaybackSessionState(
    playback = PlaybackState(status = status),
    queue = PlaybackQueue(
        items = listOfNotNull(currentItem),
        currentMediaKey = currentItem?.mediaKey,
    ),
    currentItem = currentItem,
    errorMessage = errorMessage,
)
```

确认该文件保留/导入 `PlaybackState`、`PlaybackStatus`、`PlaybackQueue`、`PlaybackSessionState` 与 `QueueMediaItem`。

在 `ControllerConnectionMachineTest.kt` 添加：

```kotlin
@Test
fun `explicit reconnect after failure creates a new generation without dropping commands`() {
    val requestedGenerations = mutableListOf<Long>()
    val executedWith = mutableListOf<String>()
    val machine = ControllerConnectionMachine<String>(
        maxPendingCommands = 8,
        onStateChanged = {},
        requestConnection = requestedGenerations::add,
        release = {},
    )
    machine.start()
    val firstGeneration = requestedGenerations.single()
    machine.submit { executedWith += it }
    machine.onConnectionFailed(
        generation = firstGeneration,
        message = "offline",
        shouldReconnect = false,
    )

    machine.demandConnection()
    val secondGeneration = requestedGenerations.last()
    machine.onConnected(secondGeneration, "second connection")

    assertTrue(secondGeneration > firstGeneration)
    assertEquals(listOf("second connection"), executedWith)
}
```

- [ ] **Step 2: Run the JVM tests and verify red**

```powershell
$env:ANDROID_HOME='C:\Users\Administrator\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --tests 'com.local.mediaviewer.navigation.CurrentPlayerNavigationTest' --tests 'com.local.mediaviewer.player.ControllerConnectionMachineTest' --tests 'com.local.mediaviewer.player.Media3StateMapperTest' '-Pkotlin.incremental=false'
```

Expected: navigation tests fail to compile because `PlayerEntryState` / the new resolver signature are absent. The reconnect characterization may already pass; if it fails, it must fail because no new generation is produced or pending commands are lost.

- [ ] **Step 3: Implement the pure resolver and reconnect boundary**

新增 `PlayerEntryState` 和上述纯 resolver，但 **Task 2 暂时保留** `PlayerRouteContent` 与 `resolvePlayerRouteContent(...)` 作为旧根调用的兼容包装，确保尚未由 Task 7 修改的 `MediaViewerApp.kt` 继续编译：

```kotlin
@Deprecated("Task 7 root integration will use PlayerEntryState")
fun resolvePlayerRouteContent(
    session: PlaybackSessionState,
    hasPresentedItem: Boolean,
): PlayerRouteContent = when (
    val entry = resolvePlayerEntryState(
        session = session,
        hasPresentedItem = hasPresentedItem,
        waitExpired = false,
    )
) {
    is PlayerEntryState.Ready -> PlayerRouteContent.Ready(entry.item)
    PlayerEntryState.Empty -> PlayerRouteContent.Empty
    PlayerEntryState.Connecting,
    is PlayerEntryState.Failed,
    -> PlayerRouteContent.Waiting
}
```

Task 7 改完根调用后再删除这个 deprecated 包装和旧 sealed interface。给 `QueuePlaybackController` 增加默认 `reconnect()`；`Media3PlaybackController.reconnect()` 调用 connection machine 的 demand path，不创建第二个播放器，不释放当前 session，不发送队列清空命令。只有 characterization test 失败时才对 `ControllerConnectionMachine` 做保持 pending command 的最小修复。

- [ ] **Step 4: Add the bootstrap UI red test**

`PlayerBootstrapContent` 参数固定为状态、重连和返回：

```kotlin
@Composable
fun PlayerBootstrapContent(
    state: PlayerEntryState,
    onReconnect: () -> Unit,
    onBack: () -> Unit,
)
```

在 `PlayerBootstrapContentTest.kt` 添加：

```kotlin
@get:Rule
val rule = createComposeRule()

@Test
fun failed_state_has_reconnect_and_safe_back_actions() {
    var reconnects = 0
    var backs = 0
    rule.setContent {
        PlayerBootstrapContent(
            state = PlayerEntryState.Failed("服务未响应"),
            onReconnect = { reconnects++ },
            onBack = { backs++ },
        )
    }

    rule.onNodeWithText("服务未响应").assertIsDisplayed()
    rule.onNodeWithText("重连播放器").performClick()
    rule.onNodeWithContentDescription("返回").performClick()
    assertEquals(1, reconnects)
    assertEquals(1, backs)
}
```

- [ ] **Step 5: Confirm UI compilation is red, then implement the minimum UI**

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin '-Pkotlin.incremental=false'
```

Expected before implementation: compilation fails because `PlayerBootstrapContent` does not exist.

实现三个明确状态：

- `Connecting`：文本“正在连接播放器”，进度反馈和始终可用的返回。
- `Failed`：错误正文、主动作“重连播放器”、次动作返回。
- `Empty`：文本“播放队列为空”和返回。
- `Ready` 不由此组件渲染，若误传则不创建第二套播放器内容。

- [ ] **Step 6: Run Task 2 verification**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.local.mediaviewer.navigation.CurrentPlayerNavigationTest' --tests 'com.local.mediaviewer.player.ControllerConnectionMachineTest' --tests 'com.local.mediaviewer.player.Media3StateMapperTest' '-Pkotlin.incremental=false'
.\gradlew.bat :app:compileDebugAndroidTestKotlin '-Pkotlin.incremental=false'
```

Expected: selected JVM tests pass and AndroidTest Kotlin compiles.

- [ ] **Step 7: Commit F2 before releasing the controller files to F6**

```powershell
git add app/src/main/java/com/local/mediaviewer/navigation/CurrentPlayerNavigation.kt app/src/main/java/com/local/mediaviewer/player/PlaybackController.kt app/src/main/java/com/local/mediaviewer/player/Media3PlaybackController.kt app/src/main/java/com/local/mediaviewer/player/ControllerConnectionMachine.kt app/src/main/java/com/local/mediaviewer/ui/player/PlayerBootstrapContent.kt app/src/test/java/com/local/mediaviewer/navigation/CurrentPlayerNavigationTest.kt app/src/test/java/com/local/mediaviewer/player/ControllerConnectionMachineTest.kt app/src/test/java/com/local/mediaviewer/player/Media3StateMapperTest.kt app/src/androidTest/java/com/local/mediaviewer/PlayerBootstrapContentTest.kt
git commit -m "fix(android): make player bootstrap recoverable"
```

`ControllerConnectionMachine.kt` 若未变化，不会被暂存。记录本提交哈希；Task 6 开始前必须确认当前分支包含此提交。

---

### Task 3: F3 + I2 — Preserve Browser Content During Child Loads and Failed Attempts

**Files:**

- Modify: `app/src/main/java/com/local/mediaviewer/browser/BrowserViewModel.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/browser/BrowserScreen.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/browser/BrowserViewModelTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/BrowserScreenTest.kt`
- Do not modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`

**Interfaces consumed:** `BrowserPage`, `BrowserRepository`；Screen wiring 另消费 foundation Tasks 1–3 提供的 `MediaScreenScaffold` 和 `MediaStatePanel`。

**Interfaces produced:**

```kotlin
sealed interface BrowserUiState {
    data class Loading(val previous: BrowserPage? = null) : BrowserUiState
    data class Content(val page: BrowserPage) : BrowserUiState
    data class Empty(val page: BrowserPage) : BrowserUiState
    data class Error(
        val error: AppError,
        val previous: BrowserPage? = null,
        val failedLogicalUrl: String? = null,
    ) : BrowserUiState
}
```

ViewModel 的 `pages` 仍是成功目录历史的唯一真相。打开子目录时只设置 `Loading(previous = pages.lastOrNull())`，成功后才 append；失败时记录 `failedLogicalUrl`，不污染 `pages`。`goBack()` 在 `Error(previous != null)` 或 `Loading(previous != null)` 时先取消当前 job、恢复 previous 并返回 `true` 表示“已在 ViewModel 内消费”，仅根页面且无待处理尝试时返回 `false` 供 NavHost pop。

- [ ] **Step 1: Add failing stable-content and back-consumption tests**

在 `BrowserViewModelTest.kt` 使用现有 `ResultQueueBrowserRepository`、`page(...)`、`entry(...)` helper 添加：

```kotlin
@Test
fun `failed child keeps the parent and back consumes the failed attempt`() = runTest(dispatcher) {
    val rootUrl = "http://media.example:8080/middle/"
    val childUrl = "${rootUrl}child/"
    val rootPage = page(
        logicalUrl = rootUrl,
        entries = listOf(
            entry(
                name = "child",
                logicalUrl = childUrl,
                requestUrl = "",
                kind = MediaKind.DIRECTORY,
            ),
        ),
    )
    val repository = ResultQueueBrowserRepository(
        ArrayDeque(
            listOf(
                AppResult.Success(rootPage),
                AppResult.Failure(AppError.NetworkFailure("offline")),
            ),
        ),
    )
    val viewModel = BrowserViewModel(MIDDLE_SHARE, repository)
    advanceUntilIdle()

    viewModel.open(rootPage.entries.single())
    advanceUntilIdle()

    val error = viewModel.uiState.value as BrowserUiState.Error
    assertEquals(rootPage, error.previous)
    assertEquals(childUrl, error.failedLogicalUrl)
    assertTrue(viewModel.goBack())
    assertEquals(BrowserUiState.Content(rootPage), viewModel.uiState.value)
}

@Test
fun `child is appended once only after retry succeeds`() = runTest(dispatcher) {
    val rootUrl = "http://media.example:8080/middle/"
    val childUrl = "${rootUrl}child/"
    val rootPage = page(
        logicalUrl = rootUrl,
        entries = listOf(
            entry("child", childUrl, "", MediaKind.DIRECTORY),
        ),
    )
    val childPage = page(
        logicalUrl = childUrl,
        entries = listOf(
            entry(
                "movie.mp4",
                "${childUrl}movie.mp4",
                "http://192.0.2.1/movie.mp4",
                MediaKind.VIDEO,
            ),
        ),
        breadcrumbs = listOf(
            Breadcrumb("MiddleDir", rootUrl),
            Breadcrumb("child", childUrl),
        ),
    )
    val repository = ResultQueueBrowserRepository(
        ArrayDeque(
            listOf(
                AppResult.Success(rootPage),
                AppResult.Failure(AppError.NetworkFailure("offline")),
                AppResult.Success(childPage),
            ),
        ),
    )
    val viewModel = BrowserViewModel(MIDDLE_SHARE, repository)
    advanceUntilIdle()
    viewModel.open(rootPage.entries.single())
    advanceUntilIdle()

    viewModel.retry()
    advanceUntilIdle()

    assertEquals(childUrl, currentPage(viewModel).logicalDirectoryUrl)
    assertTrue(viewModel.goBack())
    assertEquals(rootUrl, currentPage(viewModel).logicalDirectoryUrl)
    assertFalse(viewModel.goBack())
}
```

- [ ] **Step 2: Run the Browser ViewModel test and confirm red**

```powershell
$env:ANDROID_HOME='C:\Users\Administrator\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --tests 'com.local.mediaviewer.browser.BrowserViewModelTest' '-Pkotlin.incremental=false'
```

Expected: tests fail because current `Loading` / `Error` do not carry `previous` and `failedLogicalUrl`, or because `goBack()` returns `false` and would pop Browser after a failed child load.

- [ ] **Step 3: Implement stable Browser state without a second page stack**

修改所有 `BrowserUiState.Loading` / `Error` 构造点，并将 `retry()` 的目标限定为 `failedLogicalUrl ?: currentPage.logicalUrl`。加载 job 新请求开始前取消旧 job；失败时不 append；成功时按 logical URL 去重后只 append 一次。

- [ ] **Step 4: After foundation Tasks 1–3, add the failing retained-content Compose test**

在 `BrowserScreenTest.kt` 添加：

```kotlin
@Test
fun existing_page_remains_visible_while_child_load_fails() {
    val previous = browserPage(
        entries = listOf(
            browserEntry("旧页面视频.mp4", MediaKind.VIDEO),
        ),
    )
    rule.setContent {
        MediaViewerTheme {
            BrowserScreen(
                state = BrowserUiState.Error(
                    error = AppError.NetworkFailure("offline"),
                    previous = previous,
                    failedLogicalUrl = "http://media/child/",
                ),
                onRetry = {},
                onBack = {},
                onEntryClick = {},
                onBreadcrumbClick = {},
            )
        }
    }

    rule.onNodeWithText("旧页面视频.mp4").assertIsDisplayed()
    rule.onNodeWithText("加载子目录失败").assertIsDisplayed()
    rule.onNodeWithText("重试").assertHasClickAction()
}
```

- [ ] **Step 5: Run AndroidTest compilation red, then wire I2**

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin '-Pkotlin.incremental=false'
```

Expected before screen implementation: the test fails to compile or cannot find the retained entry because `BrowserScreen` renders a full-page error.

在 `BrowserScreen.kt`：

- `Loading(previous = null)` 和 `Error(previous = null)` 使用 `MediaStatePanel` 全页状态。
- `Loading(previous != null)` 保留 title、breadcrumbs 和列表，并显示非阻断进度。
- `Error(previous != null)` 保留相同内容，在列表上方显示错误状态条和“重试”。
- Screen 只消费状态和回调，不读取 repository，不自行决定 NavHost pop。
- 只消费既定 `MediaScreenScaffold` / `MediaStatePanel`，不在本任务重复定义共享组件。

- [ ] **Step 6: Verify Browser unit and UI compilation**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.local.mediaviewer.browser.BrowserViewModelTest' '-Pkotlin.incremental=false'
.\gradlew.bat :app:compileDebugAndroidTestKotlin '-Pkotlin.incremental=false'
```

Expected: Browser ViewModel tests pass and all Compose tests compile.

- [ ] **Step 7: Commit F3/I2 files**

```powershell
git add app/src/main/java/com/local/mediaviewer/browser/BrowserViewModel.kt app/src/main/java/com/local/mediaviewer/ui/browser/BrowserScreen.kt app/src/test/java/com/local/mediaviewer/browser/BrowserViewModelTest.kt app/src/androidTest/java/com/local/mediaviewer/BrowserScreenTest.kt
git commit -m "fix(android): retain browser content across failed loads"
```

---

### Task 4: F4 + I3 — Close the Settings Save Loop and Protect Dirty Server Input

**Files:**

- Modify: `app/src/main/java/com/local/mediaviewer/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/settings/SettingsScreen.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/settings/SettingsViewModelTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/HomeSettingsScreenTest.kt`
- Do not modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`

**Interfaces consumed:** `ServerSessionManager.saveCandidate(result: ConnectionTestResult)`, `ReaderPreferencesRepository`；Screen wiring 在 foundation Tasks 1–3 后消费 `MediaUrlField`、loading button 和 `MediaConfirmDialog`。

**Interfaces produced:**

```kotlin
data class SettingsUiState(
    val input: String = "",
    val isTesting: Boolean = false,
    val resolvedIpv4s: List<String> = emptyList(),
    val selectedIpv4: String? = null,
    val errorMessage: String? = null,
    val canSave: Boolean = false,
    val defaultImageMode: ImageReaderMode = ImageReaderMode.COMIC,
    val isSavingImageMode: Boolean = false,
    val imageModeError: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val hasUnsavedServerChange: Boolean = false,
)

enum class SettingsBackDecision {
    LEAVE,
    CONFIRM_DISCARD,
}

fun requestBack(): SettingsBackDecision
```

`hasUnsavedServerChange` 只比较服务器输入与最后成功保存的服务器值；图片阅读模式即时保存不进入该标志。保存失败时，只有当前 input 仍对应原探测结果才保留 `successfulResult` 和 `selectedIpv4`，用户可直接重试相同结果。保存成功且当前 input 仍等于实际持久化值时，才发出已有 `saved` event。

ViewModel 增加 `private var savedServerInput = ""`。读取 `settings.current()` 时同时设置 `savedServerInput` 和初始 `input`；`onInputChanged(value)` 与成功探测归一化 input 后都设置 `hasUnsavedServerChange = input != savedServerInput`；保存成功把 `savedServerInput` 更新为保存前从 `result.server.logicalBaseUrl` 捕获的实际持久化值，再按当前 input 重算 dirty。`requestBack()` 只读取该布尔值，不读取图片模式保存状态。

- [ ] **Step 1: Add failing save-error, retry, and dirty-decision tests**

在 `SettingsViewModelTest.kt` 扩展现有 `SettingsFakeSession` 构造器：

```kotlin
private class SettingsFakeSession(
    private val saveResults: ArrayDeque<Result<Unit>> =
        ArrayDeque(),
    private val saveGate: CompletableDeferred<Unit>? = null,
    private val testBlock: CandidateTestBlock,
) : ServerSessionManager {
    // retain current state/test counters

    override suspend fun saveCandidate(result: ConnectionTestResult) {
        saveCalls += 1
        saveGate?.await()
        saveResults.removeFirstOrNull()?.getOrThrow()
        savedResult = result
    }
}
```

默认空队列保持既有测试的成功保存语义；失败/重试测试显式传队列。然后添加：

```kotlin
@Test
fun `save failure remains editable and the same tested result can be retried`() = runTest(dispatcher) {
    val session = SettingsFakeSession(
        testBlock = { AppResult.Success(successfulResult()) },
        saveResults = ArrayDeque(
            listOf(
                Result.failure<Unit>(IOException("disk full")),
                Result.success(Unit),
            ),
        ),
    )
    val viewModel = SettingsViewModel(
        SettingsFakeRepository(ServerConfig()),
        SettingsFakeReaderPreferences(),
        session,
    )
    advanceUntilIdle()
    viewModel.onInputChanged("http://media.example:8080")
    viewModel.testConnection()
    advanceUntilIdle()

    viewModel.save()
    advanceUntilIdle()
    assertFalse(viewModel.uiState.value.isSaving)
    assertEquals("保存失败，请重试", viewModel.uiState.value.saveError)
    assertTrue(viewModel.uiState.value.canSave)

    viewModel.save()
    advanceUntilIdle()
    assertEquals(2, session.saveCalls)
    assertNull(viewModel.uiState.value.saveError)
}

@Test
fun `editing B while A saves keeps B dirty and does not navigate away`() = runTest(dispatcher) {
    val saveGate = CompletableDeferred<Unit>()
    val session = SettingsFakeSession(
        saveGate = saveGate,
        testBlock = { AppResult.Success(successfulResult()) },
    )
    val viewModel = SettingsViewModel(
        SettingsFakeRepository(ServerConfig()),
        SettingsFakeReaderPreferences(),
        session,
    )
    val savedEvents = mutableListOf<Unit>()
    val savedJob = backgroundScope.launch(
        UnconfinedTestDispatcher(testScheduler),
    ) {
        viewModel.saved.collect { savedEvents += it }
    }
    advanceUntilIdle()
    viewModel.onInputChanged("http://media.example:8080")
    viewModel.testConnection()
    advanceUntilIdle()

    viewModel.save()
    runCurrent()
    viewModel.onInputChanged("http://new.example:8080")
    saveGate.complete(Unit)
    advanceUntilIdle()

    assertEquals(
        "http://new.example:8080",
        viewModel.uiState.value.input,
    )
    assertTrue(viewModel.uiState.value.hasUnsavedServerChange)
    assertFalse(viewModel.uiState.value.canSave)
    assertTrue(savedEvents.isEmpty())
    assertEquals(
        "http://media.example:8080",
        session.savedResult?.server?.logicalBaseUrl,
    )
    savedJob.cancel()
}

@Test
fun `only an unsaved server change asks for discard confirmation`() = runTest(dispatcher) {
    val viewModel = SettingsViewModel(
        SettingsFakeRepository(ServerConfig()),
        SettingsFakeReaderPreferences(),
        SettingsFakeSession {
            AppResult.Success(successfulResult())
        },
    )
    advanceUntilIdle()
    viewModel.onDefaultImageModeChanged(ImageReaderMode.SINGLE)
    advanceUntilIdle()
    assertEquals(SettingsBackDecision.LEAVE, viewModel.requestBack())

    viewModel.onInputChanged("http://new.example:8080")
    assertEquals(SettingsBackDecision.CONFIRM_DISCARD, viewModel.requestBack())
}
```

竞态测试还需导入 `kotlinx.coroutines.launch`、`kotlinx.coroutines.flow.collect` 与 `kotlinx.coroutines.test.UnconfinedTestDispatcher`；`CompletableDeferred` 使用现有协程 API。这里故意允许保存期间继续编辑，确保 ViewModel 自身守住竞态边界，而不依赖 UI 禁用输入。

- [ ] **Step 2: Run Settings JVM tests and confirm red**

```powershell
$env:ANDROID_HOME='C:\Users\Administrator\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --tests 'com.local.mediaviewer.settings.SettingsViewModelTest' '-Pkotlin.incremental=false'
```

Expected: compilation fails for the new state/decision, or the coroutine throws from `saveCandidate`; current implementation clears the successful result before save and cannot retry directly.

- [ ] **Step 3: Implement save state and dirty-state ownership**

保存逻辑使用显式 `try/catch/finally`，避免异常逃出 ViewModel：

```kotlin
fun save() {
    val result = successfulResult ?: return
    if (uiState.value.isSaving) return
    val persistedInput = result.server.logicalBaseUrl
    viewModelScope.launch {
        mutableUiState.value = mutableUiState.value.copy(
            isSaving = true,
            saveError = null,
            canSave = false,
        )
        try {
            session.saveCandidate(result)
            savedServerInput = persistedInput
            val inputStillMatches =
                mutableUiState.value.input == persistedInput
            successfulResult = null
            mutableUiState.value = mutableUiState.value.copy(
                isSaving = false,
                saveError = null,
                canSave = false,
                hasUnsavedServerChange = !inputStillMatches,
            )
            if (inputStillMatches) {
                mutableSaved.emit(Unit)
            }
        } catch (failure: Exception) {
            val resultStillCurrent =
                successfulResult === result &&
                    mutableUiState.value.input == persistedInput
            mutableUiState.value = mutableUiState.value.copy(
                isSaving = false,
                saveError = "保存失败，请重试",
                canSave = resultStillCurrent,
            )
        }
    }
}
```

不要在失败路径清除仍与当前 input 对应的 `successfulResult`。若保存期间 input 已变化，`onInputChanged` 会作废旧结果，失败路径也不得为新 input 恢复 `canSave`。`canSave` 必须同时要求成功探测结果存在、结果仍对应当前 input 且 `!isSaving`。保存成功只能将 `savedServerInput` 更新为实际持久化的 `persistedInput`；当前 input 不匹配时保持 dirty 且不发 `saved` 导航事件。

- [ ] **Step 4: After foundation Tasks 1–3, add the failing discard-dialog UI test**

为保持 Task 7 前的根编译兼容，`SettingsScreen` 在本任务采用以下精确过渡签名；现有 `MediaViewerApp.kt` 的 `onBack` / `onDefaultImageModeChanged` 调用继续有效，Task 7 再显式传入新的 back decision callbacks：

```kotlin
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onInputChanged: (String) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onDefaultImageModeChanged: (ImageReaderMode) -> Unit,
    onBack: () -> Unit,
    onBackRequest: () -> SettingsBackDecision = {
        SettingsBackDecision.LEAVE
    },
    onDiscardConfirmed: () -> Unit = onBack,
)
```

在 `HomeSettingsScreenTest.kt` 添加：

```kotlin
@Test
fun back_with_unsaved_server_change_requires_an_explicit_discard() {
    var discarded = 0
    rule.setContent {
        SettingsScreen(
            state = SettingsUiState(
                input = "http://new.example:8080",
                hasUnsavedServerChange = true,
            ),
            onBackRequest = { SettingsBackDecision.CONFIRM_DISCARD },
            onDiscardConfirmed = { discarded++ },
            onInputChanged = {},
            onTest = {},
            onSave = {},
            onDefaultImageModeChanged = {},
            onBack = {},
        )
    }

    rule.onNodeWithContentDescription("返回").performClick()
    rule.onNodeWithText("放弃未保存的服务器更改？").assertIsDisplayed()
    rule.onNodeWithText("继续编辑").assertHasClickAction()
    rule.onNodeWithText("放弃更改").performClick()
    assertEquals(1, discarded)
}
```

- [ ] **Step 5: Compile red, then wire I3**

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin '-Pkotlin.incremental=false'
```

Expected before implementation: test cannot compile against the new callbacks/state or no confirmation dialog appears.

在 `SettingsScreen.kt`：

- 保存按钮 `isSaving` 时保持宽度、显示进度且 disabled。
- `saveError` 在 URL/保存操作区显示，可被 TalkBack 读出。
- 返回按钮和系统 BackHandler 都调用同一个 `onBackRequest()`。
- `CONFIRM_DISCARD` 显示 `MediaConfirmDialog`；取消只关弹层，确认调用 `onDiscardConfirmed`。
- 图片阅读模式变化不打开该弹层。
- 只消费既定 `MediaUrlField` / `MediaConfirmDialog`，不复制其实现。

- [ ] **Step 6: Verify Settings tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.local.mediaviewer.settings.SettingsViewModelTest' '-Pkotlin.incremental=false'
.\gradlew.bat :app:compileDebugAndroidTestKotlin '-Pkotlin.incremental=false'
```

Expected: selected JVM tests pass and AndroidTest Kotlin compiles.

- [ ] **Step 7: Commit F4/I3 files**

```powershell
git add app/src/main/java/com/local/mediaviewer/settings/SettingsViewModel.kt app/src/main/java/com/local/mediaviewer/ui/settings/SettingsScreen.kt app/src/test/java/com/local/mediaviewer/settings/SettingsViewModelTest.kt app/src/androidTest/java/com/local/mediaviewer/HomeSettingsScreenTest.kt
git commit -m "fix(android): make settings save retryable"
```

---

### Task 5: F5 + I4 — Refresh the Endpoint on Explicit Image Retry Without Reloading Successes

**Files:**

- Modify: `app/src/main/java/com/local/mediaviewer/image/ImageReaderViewModel.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderScreen.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/ui/image/ImageItemErrorPanel.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/image/ImageReaderViewModelTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/ImageReaderScreenTest.kt`
- Do not modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`

**Interfaces consumed:** `ServerSessionManager.refreshAfterRequestFailure()`, `ImageReaderItem.logicalUrl`, per-item `itemRequestGenerations`, image failure kind.

**Interfaces produced:**

```kotlin
enum class EndpointRefreshTrigger {
    AUTOMATIC,
    USER,
}

fun retryImage(logicalUrl: String)

private fun refreshEndpoint(
    trigger: EndpointRefreshTrigger,
    retryLogicalUrl: String?,
)
```

自动恢复预算仍为一次。用户重试不重置也不消耗自动预算，但同一时间只允许一个 `refreshJob`。刷新成功后，仅把当前 `NETWORK` 失败项与显式目标项改写到新 endpoint 并递增这些项各自的 generation；已经成功显示且不在失败集合中的图片保留原 request URL 和 generation，因此不会因一次失败项重试被重新请求。

- [ ] **Step 1: Add failing explicit-refresh and selective-generation tests**

在 `ImageReaderViewModelTest.kt` 添加：

```kotlin
@Test
fun `manual retry refreshes after automatic budget is exhausted and only retries failed images`() =
    runTest(dispatcher) {
        val firstEndpoint = SessionEndpoint(
            logicalBaseUrl = "http://media.example:8080",
            requestBaseUrl = "http://192.0.2.20:8080",
            ipv4 = "192.0.2.20",
        )
        val secondEndpoint = firstEndpoint.copy(
            requestBaseUrl = "http://192.0.2.21:8080",
            ipv4 = "192.0.2.21",
        )
        val session = QueuedImageSession(
            ArrayDeque(
                listOf(
                    { AppResult.Success(firstEndpoint) },
                    { AppResult.Success(secondEndpoint) },
                ),
            ),
        )
        val viewModel = readerViewModel(
            repository = ReaderDirectoryRepository(
                successContent(
                    listOf(
                        readerEntry("a.jpg", MediaKind.IMAGE),
                        readerEntry("b.jpg", MediaKind.IMAGE),
                    ),
                ),
            ),
            session = session,
        )
        advanceUntilIdle()
        val initial = viewModel.uiState.value as ImageReaderUiState.Content
        val successUrl = initial.images[0].logicalUrl
        val failedUrl = initial.images[1].logicalUrl

        viewModel.onImageLoadError(failedUrl, ImageLoadFailureKind.NETWORK)
        advanceUntilIdle()
        viewModel.onImageLoadError(failedUrl, ImageLoadFailureKind.NETWORK)
        advanceUntilIdle()
        assertEquals(1, session.refreshCalls)
        val beforeManual =
            viewModel.uiState.value as ImageReaderUiState.Content

        viewModel.retryImage(failedUrl)
        advanceUntilIdle()
        val afterManual =
            viewModel.uiState.value as ImageReaderUiState.Content

        assertEquals(2, session.refreshCalls)
        assertEquals(
            beforeManual.itemRequestGenerations[successUrl] ?: 0,
            afterManual.itemRequestGenerations[successUrl] ?: 0,
        )
        assertEquals(
            (beforeManual.itemRequestGenerations[failedUrl] ?: 0) + 1,
            afterManual.itemRequestGenerations[failedUrl] ?: 0,
        )
        assertEquals(
            beforeManual.images.single { it.logicalUrl == successUrl }.requestUrl,
            afterManual.images.single { it.logicalUrl == successUrl }.requestUrl,
        )
        assertTrue(
            afterManual.images.single { it.logicalUrl == failedUrl }
                .requestUrl.contains("192.0.2.21"),
        )
    }

@Test
fun `repeated manual taps share one refresh job`() = runTest(dispatcher) {
    val refresh = CompletableDeferred<AppResult<SessionEndpoint>>()
    val session = QueuedImageSession(
        ArrayDeque(
            listOf(
                { AppResult.Success(REFRESHED_ENDPOINT) },
                { refresh.await() },
            ),
        ),
    )
    val viewModel = readerViewModel(
        repository = ReaderDirectoryRepository(
            successContent(
                listOf(readerEntry("a.jpg", MediaKind.IMAGE)),
            ),
        ),
        session = session,
    )
    advanceUntilIdle()
    viewModel.onImageLoadError(
        "${DIRECTORY_URL}a.jpg",
        ImageLoadFailureKind.NETWORK,
    )
    advanceUntilIdle()
    viewModel.onImageLoadError(
        "${DIRECTORY_URL}a.jpg",
        ImageLoadFailureKind.NETWORK,
    )

    viewModel.retryImage("${DIRECTORY_URL}a.jpg")
    viewModel.retryImage("${DIRECTORY_URL}a.jpg")
    runCurrent()

    assertEquals(2, session.refreshCalls)
    refresh.complete(AppResult.Failure(AppError.NetworkFailure("offline")))
    advanceUntilIdle()
    assertFalse(
        (viewModel.uiState.value as ImageReaderUiState.Content)
            .isRefreshingEndpoint,
    )
}
```

在同一测试文件新增：

```kotlin
private class QueuedImageSession(
    private val results:
        ArrayDeque<suspend () -> AppResult<SessionEndpoint>>,
) : ServerSessionManager {
    override val state: StateFlow<ServerSessionState> =
        MutableStateFlow(ServerSessionState.Connecting)
    var refreshCalls = 0
        private set

    override suspend fun connectSaved() = Unit

    override suspend fun testCandidate(
        input: String,
    ): AppResult<ConnectionTestResult> =
        error("unused testCandidate: $input")

    override suspend fun saveCandidate(result: ConnectionTestResult) {
        error("unused saveCandidate: ${result.server.logicalBaseUrl}")
    }

    override suspend fun refreshAfterRequestFailure():
        AppResult<SessionEndpoint> {
        refreshCalls += 1
        return results.removeFirst().invoke()
    }
}
```

这样 queued 与 deferred refresh 都使用真实返回类型 `AppResult<SessionEndpoint>`。

- [ ] **Step 2: Run ImageReader JVM tests and confirm red**

```powershell
$env:ANDROID_HOME='C:\Users\Administrator\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --tests 'com.local.mediaviewer.image.ImageReaderViewModelTest' '-Pkotlin.incremental=false'
```

Expected: current `retryImage()` only increments the old request generation and does not call endpoint refresh after the automatic budget is exhausted; the selected request URL stays stale.

- [ ] **Step 3: Implement trigger-aware refresh and selective remapping**

把当前自动刷新逻辑收敛到一个入口：

```kotlin
private fun remapFailedRequests(
    endpoint: SessionEndpoint,
    retryLogicalUrl: String?,
) {
    updateContent { content ->
        val retryKeys = content.itemFailures
            .filterValues { it.kind == ImageLoadFailureKind.NETWORK }
            .keys + listOfNotNull(retryLogicalUrl)
        content.copy(
            images = content.images.map { item ->
                if (item.logicalUrl in retryKeys) {
                    item.copy(requestUrl = endpoint.requestUrlFor(item.logicalUrl))
                } else {
                    item
                }
            },
            itemRequestGenerations = content.itemRequestGenerations.toMutableMap().apply {
                retryKeys.forEach { key -> this[key] = (this[key] ?: 0) + 1 }
            },
            itemFailures = content.itemFailures - retryKeys,
            isRefreshingEndpoint = false,
        )
    }
}
```

保持 `Content.requestGeneration` 不变，避免全局 invalidation。自动触发在 budget 用尽后直接保留错误；用户触发可再次调用 session refresh。refresh 失败只更新目标项可读错误并复位 `isRefreshingEndpoint`。

- [ ] **Step 4: After foundation Tasks 1–3, add the failing retry-label UI test**

在 `ImageReaderScreenTest.kt` 添加：

```kotlin
@Test
fun network_error_offers_reconnect_but_decode_error_only_retries_the_item() {
    val networkItem = ImageReaderItem(
        name = "network.jpg",
        size = 1L,
        modifiedAt = Instant.EPOCH,
        logicalUrl = "http://media.example/network.jpg",
        requestUrl = "http://192.0.2.1/network.jpg",
    )
    val decodeItem = networkItem.copy(
        name = "decode.jpg",
        logicalUrl = "http://media.example/decode.jpg",
        requestUrl = "http://192.0.2.1/decode.jpg",
    )
    rule.setContent {
        Column {
            ImageItemErrorPanel(
                item = networkItem,
                failure = ImageItemFailure(
                    message = "图片网络加载失败",
                    kind = ImageLoadFailureKind.NETWORK,
                ),
                onRetry = {},
            )
            ImageItemErrorPanel(
                item = decodeItem,
                failure = ImageItemFailure(
                    message = "图片解码失败",
                    kind = ImageLoadFailureKind.DECODE,
                ),
                onRetry = {},
            )
        }
    }

    rule.onNodeWithText("重新连接并重试").assertHasClickAction()
    rule.onNodeWithText("重试此图").assertHasClickAction()
}
```

- [ ] **Step 5: Compile red, then wire I4**

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin '-Pkotlin.incremental=false'
```

Expected before implementation: the network failure action still has the generic retry label or cannot distinguish failure kinds.

`ImageReaderScreen` 将每个 item 的 logical URL 传给 `retryImage`。`ImageItemErrorPanel` 根据失败类型显示上述两个动作；endpoint 刷新期间仅禁用目标失败项的重复点击并显示局部进度，不遮挡其他已加载图片。只消费 UI 计划的局部状态样式，不定义第二套全局状态组件。

- [ ] **Step 6: Verify ImageReader tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.local.mediaviewer.image.ImageReaderViewModelTest' '-Pkotlin.incremental=false'
.\gradlew.bat :app:compileDebugAndroidTestKotlin '-Pkotlin.incremental=false'
```

Expected: selected JVM tests pass and AndroidTest Kotlin compiles.

- [ ] **Step 7: Commit F5/I4 files**

```powershell
git add app/src/main/java/com/local/mediaviewer/image/ImageReaderViewModel.kt app/src/main/java/com/local/mediaviewer/ui/image/ImageReaderScreen.kt app/src/main/java/com/local/mediaviewer/ui/image/ImageItemErrorPanel.kt app/src/test/java/com/local/mediaviewer/image/ImageReaderViewModelTest.kt app/src/androidTest/java/com/local/mediaviewer/ImageReaderScreenTest.kt
git commit -m "fix(android): refresh failed image requests on demand"
```

---

### Task 6: F6 — Deliver Persistence Failures as Non-Blocking Playback Notices

**Precondition:** Confirm Task 2 is committed and its tests pass. This task is not allowed to start from a branch that lacks the F2 `QueuePlaybackController.reconnect()` and `PlayerEntryState` changes.

**Files:**

- Create: `app/src/main/java/com/local/mediaviewer/queue/PlaybackNotice.kt`
- Create: `app/src/main/java/com/local/mediaviewer/service/PlaybackNoticeCodec.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/queue/PlaybackCoordinator.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/service/PlaybackService.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/service/PlaybackSessionCallback.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/player/PlaybackController.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/player/Media3PlaybackController.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/queue/PlaybackCoordinatorTest.kt`
- Create: `app/src/test/java/com/local/mediaviewer/service/PlaybackNoticeCodecTest.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/service/PlaybackSessionCallbackTest.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/service/ServiceTestDoubles.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/MediaSessionControlsTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/testing/BackgroundPlaybackTestHarness.kt`
- Do not modify: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`

**Interfaces consumed:** `PlaybackCoordinator.saveCurrentSnapshot()`, `MediaSession.connectedControllers`, `MediaSession.sendCustomCommand(...)`, `MediaController.Listener.onCustomCommand(...)`.

**Interfaces produced:**

```kotlin
enum class PlaybackNoticeKind {
    QUEUE_SAVE_FAILED,
    POSITION_SAVE_FAILED,
}

enum class PlaybackNoticeAction {
    RETRY_PERSISTENCE,
}

data class PlaybackNotice(
    val id: Long,
    val kind: PlaybackNoticeKind,
    val message: String,
    val action: PlaybackNoticeAction? = null,
)

private val playbackNoticeIds =
    AtomicLong(System.currentTimeMillis())

internal fun nextPlaybackNoticeId(): Long =
    playbackNoticeIds.incrementAndGet()

interface QueuePlaybackController {
    val notices: SharedFlow<PlaybackNotice>
        get() = noPlaybackNotices

    fun retryPersistence() = Unit
}

private val noPlaybackNotices: SharedFlow<PlaybackNotice> =
    MutableSharedFlow<PlaybackNotice>(replay = 0).asSharedFlow()
```

通知使用 `MutableSharedFlow<PlaybackNotice>(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)`，不 replay 历史消息；没有活动 collector 时直接丢弃，额外容量只吸收活动 collector 短暂变慢时的最近一次消息，因此重建 Activity 不重复显示旧 Snackbar。`PlaybackNotice.kt` 定义 process-wide `AtomicLong(System.currentTimeMillis())`，每次事件 `incrementAndGet()`；它跨 Service/coordinator 重建仍不复用旧 id。持久化错误不再写入 `PlaybackSessionState.errorMessage`，播放器/端点真实错误仍继续使用该字段。

Media3 协议固定为：

```kotlin
const val ACTION_PLAYBACK_NOTICE =
    "com.local.mediaviewer.action.PLAYBACK_NOTICE"
const val ACTION_RETRY_PERSISTENCE =
    "com.local.mediaviewer.action.RETRY_PERSISTENCE"
```

`PlaybackNoticeCodec` 的 Bundle keys 固定为 `id`、`kind`、`message`、`action`；未知 kind/action 返回 `null`，不使控制器崩溃。

- [ ] **Step 1: Add the failing coordinator notice tests**

给现有 `FakeQueueRepository` 增加可变 `saveFailure: Throwable?` 与 `saveCalls`，给现有 `FakePositionStore` 增加可变 `recordFailure: Throwable?` 与 `recordCalls`；两者继续实现当前真实接口。添加：

```kotlin
@Test
fun `queue save failure emits a notice and keeps the in-memory queue`() = runTest {
    val repository = FakeQueueRepository(
        saveFailure = IOException("queue disk full"),
    )
    val coordinator = coordinator(FakeEngine(), repository = repository, scope = this)
    val notice = async(start = CoroutineStart.UNDISPATCHED) {
        coordinator.notices.first()
    }

    coordinator.append(item("a"))
    advanceUntilIdle()

    assertEquals(listOf("a"), coordinator.sessionState.value.queue.items.map { it.mediaKey })
    assertEquals(PlaybackNoticeKind.QUEUE_SAVE_FAILED, notice.await().kind)
    assertNull(coordinator.sessionState.value.errorMessage)
    coordinator.close()
}

@Test
fun `position save failure emits once and retry persists the current snapshot`() = runTest {
    val repository = FakeQueueRepository()
    val positions = FakePositionStore()
    val coordinator = coordinator(
        FakeEngine(),
        repository = repository,
        positions = positions,
        scope = this,
    )
    coordinator.replaceQueue(listOf(item("a")), "a")
    advanceUntilIdle()
    positions.recordFailure = IOException("position disk full")
    val notice = async(start = CoroutineStart.UNDISPATCHED) {
        coordinator.notices.first()
    }

    coordinator.saveCurrentSnapshot()
    advanceUntilIdle()
    assertEquals(PlaybackNoticeKind.POSITION_SAVE_FAILED, notice.await().kind)
    assertEquals(1, positions.recordCalls)
    assertEquals("a", coordinator.sessionState.value.currentItem?.mediaKey)
    assertNull(coordinator.sessionState.value.errorMessage)

    positions.recordFailure = null
    coordinator.saveCurrentSnapshot()
    advanceUntilIdle()
    assertEquals(2, positions.recordCalls)
    coordinator.close()
}
```

- [ ] **Step 2: Run coordinator tests and confirm red**

```powershell
$env:ANDROID_HOME='C:\Users\Administrator\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --tests 'com.local.mediaviewer.queue.PlaybackCoordinatorTest' '-Pkotlin.incremental=false'
```

Expected: tests fail to compile because `notices` / `PlaybackNoticeKind` do not exist; after types are introduced, the old implementation fails because it writes persistence failure to `sessionState.errorMessage`.

- [ ] **Step 3: Add the notice model and emit only at persistence catch sites**

在 `PlaybackCoordinator` 中添加：

```kotlin
private val mutableNotices = MutableSharedFlow<PlaybackNotice>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
)
val notices: SharedFlow<PlaybackNotice> = mutableNotices.asSharedFlow()
private fun notifyPersistenceFailure(
    kind: PlaybackNoticeKind,
    fallbackMessage: String,
    failure: Throwable,
) {
    mutableNotices.tryEmit(
        PlaybackNotice(
            id = nextPlaybackNoticeId(),
            kind = kind,
            message = failure.message?.takeIf(String::isNotBlank) ?: fallbackMessage,
            action = PlaybackNoticeAction.RETRY_PERSISTENCE,
        ),
    )
}
```

只替换 `queueRepository.save(...)` 与 position store save 的 persistence catch；播放错误、端点恢复错误仍调用 `setError`。`saveCurrentSnapshot()` 不能再用一个 `runCatching { persistSnapshot(...) }` 混淆失败种类，而是按真实边界保存：

```kotlin
suspend fun saveCurrentSnapshot() = mutate {
    val snapshot = captureCurrentSnapshot()
    try {
        queueRepository.save(snapshot.queue)
    } catch (failure: Exception) {
        notifyPersistenceFailure(
            PlaybackNoticeKind.QUEUE_SAVE_FAILED,
            "播放队列保存失败",
            failure,
        )
        return@mutate
    }
    val mediaKey = snapshot.currentMediaKey ?: return@mutate
    try {
        positionStore.record(
            mediaKey = mediaKey,
            positionMs = snapshot.positionMs,
            durationMs = snapshot.durationMs,
            updatedAtEpochMs = snapshot.updatedAtEpochMs,
        )
    } catch (failure: Exception) {
        notifyPersistenceFailure(
            PlaybackNoticeKind.POSITION_SAVE_FAILED,
            "播放状态保存失败",
            failure,
        )
    }
}
```

`persistSnapshot(snapshot)` 保持原来的抛错语义，供 service destroy 的外层持久化治理；实时 `saveCurrentSnapshot`、`setQueue` 和 `persistCurrentPositionLocked` 使用上述分类通知。通知失败本身不得抛异常或改变当前 queue/session。

- [ ] **Step 4: Add codec red tests**

在 `PlaybackNoticeCodecTest.kt` 使用 Android-capable JVM runner：

```kotlin
@RunWith(RobolectricTestRunner::class)
class PlaybackNoticeCodecTest {
    // tests below
}
```

并添加：

```kotlin
@Test
fun `notice bundle round trip preserves the event`() {
    val original = PlaybackNotice(
        id = 42L,
        kind = PlaybackNoticeKind.QUEUE_SAVE_FAILED,
        message = "播放队列保存失败",
        action = PlaybackNoticeAction.RETRY_PERSISTENCE,
    )
    assertEquals(original, PlaybackNoticeCodec.decode(PlaybackNoticeCodec.encode(original)))
}

@Test
fun `unknown kind is ignored`() {
    val bundle = Bundle().apply {
        putLong("id", 7L)
        putString("kind", "FUTURE_KIND")
        putString("message", "future")
    }
    assertNull(PlaybackNoticeCodec.decode(bundle))
}
```

- [ ] **Step 5: Run codec test red, then implement the exact Bundle codec**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.local.mediaviewer.service.PlaybackNoticeCodecTest' '-Pkotlin.incremental=false'
```

Expected before implementation: compilation fails because `PlaybackNoticeCodec` is absent.

实现 `encode` 和 `decode`，枚举解析使用 `entries.firstOrNull { it.name == raw }`；message 为空或 id 缺失时返回 `null`。不要使用 Java serialization 或 Parcelable。

- [ ] **Step 6: Add the failing retry custom-command callback test**

扩展 `ServiceTestQueueRepository` 的 `saveCalls` 和 `ServiceTestPositionStore` 的 `recordCalls`，然后在 `PlaybackSessionCallbackTest.kt` 使用真实 `PlaybackCoordinator` 添加：

```kotlin
@Test
fun `retry persistence command saves the current snapshot once`() = runTest {
    val repository = ServiceTestQueueRepository(
        PlaybackQueue(
            items = listOf(serviceTestItem("a")),
            currentMediaKey = "a",
        ),
    )
    val positions = ServiceTestPositionStore()
    val coordinator = serviceTestCoordinator(
        scope = this,
        repository = repository,
        positions = positions,
    )
    coordinator.restore()
    advanceUntilIdle()
    val callback = PlaybackSessionCallback(coordinator, scope = this)
    val fixture = mediaSession(coordinator, this)
    val command = SessionCommand(ACTION_RETRY_PERSISTENCE, Bundle.EMPTY)

    val result = callback.onCustomCommand(
        fixture.session,
        controllerInfo(),
        command,
        Bundle.EMPTY,
    )
    advanceUntilIdle()

    assertEquals(SessionResult.RESULT_SUCCESS, result.get().resultCode)
    assertEquals(1, repository.saveCalls)
    assertEquals(1, positions.recordCalls)
    fixture.session.release()
    fixture.player.release()
    coordinator.close()
}
```

- [ ] **Step 7: Implement the service-to-controller bridge**

在 `PlaybackSessionCallback.onConnect` 的 available session commands 中加入 `ACTION_RETRY_PERSISTENCE`；`onCustomCommand` 收到它后调用一次 `coordinator.saveCurrentSnapshot()`。

`PlaybackService` 在 MediaSession 创建后，用 service scope collect `coordinator.notices`：

```kotlin
noticeJob = serviceScope.launch {
    coordinator.notices.collect { notice ->
        val args = PlaybackNoticeCodec.encode(notice)
        mediaSession.connectedControllers.forEach { controller ->
            mediaSession.sendCustomCommand(
                controller,
                SessionCommand(ACTION_PLAYBACK_NOTICE, Bundle.EMPTY),
                args,
            )
        }
    }
}
```

`onDestroy()` 取消 `noticeJob`。没有 connected controller 时丢弃该 UI 通知；不得为通知创建永久磁盘 outbox。

`Media3PlaybackController` 的 `MediaController.Listener.onCustomCommand` 解码 `ACTION_PLAYBACK_NOTICE` 并 `tryEmit` 到 controller-local `MutableSharedFlow`，始终返回 immediate success；未知或 malformed Bundle 返回 not-supported/成功忽略均可，但必须与 codec test 和 controller test保持一致。`retryPersistence()` 发送 `ACTION_RETRY_PERSISTENCE`，不直接访问 Room。

- [ ] **Step 8: Add and run the Android bridge red/green test**

在 `BackgroundPlaybackTestHarness.kt` 增加一个只用于 androidTest 的 `FailOncePositionStore`，委托现有 `RoomPlaybackPositionStore`，`record()` 先递增 `recordCalls`，当 `failNextRecord` 为 true 时复位标志并抛 `IOException("position save fixture failure")`。`BackgroundPlaybackAppContainer.createPlaybackCoordinator()` 使用这个 wrapper，并由 harness 精确暴露：

```kotlin
fun failNextSnapshotSave() {
    container.testPositionStore.failNextRecord = true
}

val snapshotSaveCalls: Int
    get() = container.testPositionStore.recordCalls
```

然后在 `MediaSessionControlsTest.kt` 增加：

```kotlin
@Test
fun persistenceNoticeReachesControllerOnceAndRetryKeepsPlaybackState() =
    runBlocking {
        BackgroundPlaybackTestHarness().use { harness ->
            val controller =
                harness.container.playbackController as Media3PlaybackController
            harness.connectController().use { systemController ->
                systemController.run {
                    setMediaItems(harness.mediaQueue())
                    prepare()
                    play()
                }
                harness.waitUntil("current item is visible to app controller") {
                    controller.sessionState.value.currentItem != null
                }
                val before = controller.sessionState.value
                val notice = async(start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(5_000L) { controller.notices.first() }
                }

                harness.failNextSnapshotSave()
                controller.retryPersistence()
                val received = notice.await()

                assertEquals(
                    PlaybackNoticeKind.POSITION_SAVE_FAILED,
                    received.kind,
                )
                assertEquals(
                    before.currentItem?.mediaKey,
                    controller.sessionState.value.currentItem?.mediaKey,
                )
                assertEquals(
                    before.playWhenReady,
                    controller.sessionState.value.playWhenReady,
                )

                val callsBeforeRetry = harness.snapshotSaveCalls
                controller.retryPersistence()
                harness.waitUntil("retry snapshot succeeds") {
                    harness.snapshotSaveCalls == callsBeforeRetry + 1
                }
            }
        }
}
```

先运行：

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin '-Pkotlin.incremental=false'
```

Expected before bridge completion: compilation fails on controller notices/retry API. Bridge 完成后再次运行同一命令，Expected: AndroidTest Kotlin compiles.

- [ ] **Step 9: Run all F6 JVM tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.local.mediaviewer.queue.PlaybackCoordinatorTest' --tests 'com.local.mediaviewer.service.PlaybackNoticeCodecTest' --tests 'com.local.mediaviewer.service.PlaybackSessionCallbackTest' --tests 'com.local.mediaviewer.player.*' '-Pkotlin.incremental=false'
```

Expected: all selected tests pass; persistence failure produces notice rather than player error; reconnect behavior from F2 remains green.

- [ ] **Step 10: Commit F6 as a single controller-chain handoff**

```powershell
git add app/src/main/java/com/local/mediaviewer/queue/PlaybackNotice.kt app/src/main/java/com/local/mediaviewer/service/PlaybackNoticeCodec.kt app/src/main/java/com/local/mediaviewer/queue/PlaybackCoordinator.kt app/src/main/java/com/local/mediaviewer/service/PlaybackService.kt app/src/main/java/com/local/mediaviewer/service/PlaybackSessionCallback.kt app/src/main/java/com/local/mediaviewer/player/PlaybackController.kt app/src/main/java/com/local/mediaviewer/player/Media3PlaybackController.kt app/src/test/java/com/local/mediaviewer/queue/PlaybackCoordinatorTest.kt app/src/test/java/com/local/mediaviewer/service/PlaybackNoticeCodecTest.kt app/src/test/java/com/local/mediaviewer/service/PlaybackSessionCallbackTest.kt app/src/test/java/com/local/mediaviewer/service/ServiceTestDoubles.kt app/src/androidTest/java/com/local/mediaviewer/MediaSessionControlsTest.kt app/src/androidTest/java/com/local/mediaviewer/testing/BackgroundPlaybackTestHarness.kt
git commit -m "fix(android): surface playback persistence notices"
```

---

### Task 7: I1 + F7 — Integrate the Root Once and Make Player Exit Source-Safe

**Exclusive owner rule:** The same integration agent owns every edit in this task. No other task or UI plan may edit `MediaViewerApp.kt` concurrently. This task begins only after this plan's Tasks 1–6, the foundation plan's Tasks 1–7, and the player plan's Tasks 1–6 are committed.

**Files:**

- Modify exclusively: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/navigation/CurrentPlayerNavigation.kt`
- Modify: `app/src/test/java/com/local/mediaviewer/navigation/CurrentPlayerNavigationTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/MediaViewerNavigationTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/PlaybackQueueUiTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/testing/FakeAppContainer.kt`

**Interfaces consumed:**

- Task 1: `AppSessionViewModel.uiState`, `retry()`.
- Task 2: `PlayerEntryState`, `resolvePlayerEntryState`, `PLAYER_ENTRY_WAIT_TIMEOUT_MS`, `QueuePlaybackController.reconnect()`, `PlayerBootstrapContent`.
- Task 3: retained `BrowserUiState`.
- Task 4/5: already-wired page callbacks; root must preserve them.
- Task 6: `QueuePlaybackController.notices`, `retryPersistence()`.
- UI system plan: `MediaTheme.extendedColors/playerColors/spacing/sizing/motion/elevation`, `MediaAppScaffold`, `MediaScreenScaffold`, `MediaStatePanel`, `MediaSnackbarHost`, `MediaConfirmDialog`, `MediaUrlField`.
- Player UI plan: `NowPlayingBar`, the adaptive `PlaybackQueueSheet`, its `(QueueMediaItem, originalIndex)` removal callback, and the ordinary/fullscreen/mini queue entry callbacks.

**Interfaces produced:** one root-owned `SnackbarHostState`; one app-owned server session lifecycle; source-safe Player exit. This task consumes the listed UI system interfaces and does not redefine any of them.

返回策略不添加 URL/source 字符串到 route：

```kotlin
internal fun NavHostController.leavePlayerSafely() {
    if (!popBackStack()) {
        navigate(HomeRoute) {
            popUpTo(graph.startDestinationId) { inclusive = false }
            launchSingleTop = true
        }
    }
}
```

Browser → Player 保留现有 Browser back-stack entry；返回时 `popBackStack()` 回原目录。通知请求从 Home 的基础栈进入 Player；返回时回 Home。只有测试证明系统恢复后来源栈丢失，才允许增加纯枚举 `PlayerEntrySource`；不得保存 Browser 路径、request URL 或 Activity intent 的临时地址。

- [ ] **Step 1: Add exact fake hooks, then add failing root-flow navigation tests before editing the root**

在 `FakeAppContainer.kt`：

- 把 session fake 保存为 `private val fakeSessionManager`，`sessionManager` 返回它；
- 对 container 暴露只读 `sessionConnectCalls` 和 `emitServerSession(state)`；
- `FakeServerSessionManager.connectSaved()` 递增 `connectCalls`；
- 给 `FakeQueuePlaybackController` 增加 `MutableSharedFlow<PlaybackNotice>(extraBufferCapacity = 4)`、`override val notices`、`reconnectCalls`、`retryPersistenceCalls`、`emitNotice(notice)`；
- 继续使用现有 `emitSessionState(state)`，不再虚构 `emitSession(...)`。

在 `MediaViewerNavigationTest.kt` 增加类字段 `private lateinit var currentPlayerRequests: CurrentPlayerNavigationRequests`；`setUp()` 在唯一一次 `rule.setContent` 前创建它，并调用 `MediaViewerApp(container, currentPlayerRequests)`。所有测试复用这一个请求对象，禁止在同一测试第二次调用 `setContent`。添加：

```kotlin
@Test
fun app_scope_connects_once_and_navigation_does_not_connect_again() {
    rule.waitUntil(5_000) { container.sessionConnectCalls == 1 }
    rule.onNodeWithContentDescription("服务器设置").performClick()
    rule.onNodeWithText("服务器设置").assertIsDisplayed()
    assertEquals(1, container.sessionConnectCalls)
}

@Test
fun browser_remains_visible_during_global_reconnect() {
    openNestedDirectory()
    rule.onNodeWithText("样例.mp4").assertIsDisplayed()

    container.emitServerSession(ServerSessionState.Connecting)
    rule.waitForIdle()

    rule.onNodeWithText("样例.mp4").assertIsDisplayed()
    rule.onNodeWithText("正在重新连接").assertIsDisplayed()
}

@Test
fun failed_player_has_reconnect_and_back_without_an_infinite_spinner() {
    val item = QueueMediaItem(
        mediaKey = "video-a",
        name = "video-a",
        logicalUrl = "http://media.test/video-a",
        kind = MediaKind.VIDEO,
    )
    container.fakePlaybackController.emitSessionState(
        PlaybackSessionState(
            queue = PlaybackQueue(listOf(item), currentMediaKey = item.mediaKey),
            currentItem = item,
        ),
    )
    currentPlayerRequests.requestOpenCurrentPlayer()
    rule.onNodeWithText("video-a").assertIsDisplayed()
    container.fakePlaybackController.emitSessionState(
        PlaybackSessionState(errorMessage = "服务连接失败"),
    )

    rule.onNodeWithText("服务连接失败").assertIsDisplayed()
    rule.onNodeWithText("重连播放器").performClick()
    assertEquals(1, container.fakePlaybackController.reconnectCalls)
    rule.onNodeWithContentDescription("返回").performClick()
    rule.onNodeWithText("MediaViewer").assertIsDisplayed()
}
```

在同文件添加返回来源测试：

```kotlin
@Test
fun browser_player_back_returns_to_the_same_directory() {
    openNestedDirectory()
    rule.onNodeWithText("样例.mp4").performClick()
    rule.onNodeWithContentDescription("返回").performClick()
    rule.onNodeWithTag("breadcrumb_1").assertIsDisplayed()
    rule.onNodeWithText("样例.mp4").assertIsDisplayed()
}

@Test
fun notification_request_returns_home_and_empty_queue_exits_once() {
    val item = QueueMediaItem(
        mediaKey = "video-a",
        name = "video-a",
        logicalUrl = "http://media.test/video-a",
        kind = MediaKind.VIDEO,
    )
    container.fakePlaybackController.emitSessionState(
        PlaybackSessionState(
            queue = PlaybackQueue(listOf(item), currentMediaKey = item.mediaKey),
            currentItem = item,
        ),
    )
    currentPlayerRequests.requestOpenCurrentPlayer()
    rule.onNodeWithText("video-a").assertIsDisplayed()
    rule.onNodeWithContentDescription("返回").performClick()
    rule.onNodeWithText("MediaViewer").assertIsDisplayed()

    currentPlayerRequests.requestOpenCurrentPlayer()
    rule.onNodeWithText("video-a").assertIsDisplayed()
    container.fakePlaybackController.emitSessionState(PlaybackSessionState())
    rule.waitForIdle()
    rule.onNodeWithText("MediaViewer").assertIsDisplayed()
    rule.mainClock.advanceTimeBy(PLAYER_ENTRY_WAIT_TIMEOUT_MS + 1_000L)
    rule.onNodeWithText("MediaViewer").assertIsDisplayed()
}
```

- [ ] **Step 2: Compile Android tests and confirm red**

```powershell
$env:ANDROID_HOME='C:\Users\Administrator\AppData\Local\Android\Sdk'
.\gradlew.bat :app:compileDebugAndroidTestKotlin '-Pkotlin.incremental=false'
```

Expected: exact FakeAppContainer hooks compile；测试随后在旧根上失败，因为连接仍由 Home 启动、Browser 在全局 Connecting 时变成裸 spinner、Player failure 不可操作，或空队列使用不安全的直接 pop。

- [ ] **Step 3: Perform the only root integration edit**

在 `MediaViewerApp.kt` 顶层创建/获取 `AppSessionViewModel`，在 NavHost 之前 collect `AppSessionUiState`。删除 Home route 对服务器启动所有权的依赖；Home 可继续用无自动连接的 `HomeViewModel` 将同一个 session state 映射为 foundation 已稳定的 `HomeUiState`，但重试必须转给 `AppSessionViewModel.retry()`，不能产生第二个启动连接。

Settings route 保留现有 `onDefaultImageModeChanged = settings::onDefaultImageModeChanged`，并显式传入 `onBackRequest = settings::requestBack`、`onDiscardConfirmed = { navController.popBackStack() }`、`onBack = { navController.popBackStack() }`；Task 4 的默认兼容参数由此转为最终显式接线。

Browser route 的 endpoint/root 选择固定为：

```kotlin
val visibleConnection = when (val current = appSession.current) {
    is ServerSessionState.Connected -> current
    else -> appSession.lastConnected
}
```

`visibleConnection != null` 时保留同一个 Browser ViewModel key 和目录内容，并另显示重连状态；只有首次无成功连接时使用 `MediaStatePanel` 显示 Connecting/Failed/设置服务器。

Player route 用 `rememberSaveable(route.mediaKey) { mutableStateOf(false) }` 保存当前入口的 `waitExpired`，并在一个可取消 effect 内开启有限等待：

```kotlin
LaunchedEffect(route.mediaKey, session.currentItem?.mediaKey) {
    waitExpired = false
    if (session.currentItem == null) {
        delay(PLAYER_ENTRY_WAIT_TIMEOUT_MS)
        waitExpired = true
    }
}
```

用 `resolvePlayerEntryState(...)` 渲染：

- `Ready`：现有音频/视频 PlayerScreen。
- `Connecting` / `Failed` / `Empty`：`PlayerBootstrapContent`。
- `Failed` 的重连调用 `controller.reconnect()`。
- 返回、Empty 自动退出和当前项由非空变空统一调用 `leavePlayerSafely()`。
- effect 随 Player 离开而取消；用户在 Connecting 中返回后不得再由 timeout 导航。
- 根已切换到 `PlayerEntryState` 后，删除 Task 2 暂留的 `PlayerRouteContent`、`resolvePlayerRouteContent(...)` 兼容包装及旧 imports。

根部只消费 `MediaAppScaffold` 和它提供的 `MediaSnackbarHost`。`NowPlayingBar` 必须放入
`MediaAppScaffold.bottomBar` 并占用真实布局空间；删除旧的绝对定位覆盖层。普通播放器、
全屏播放器和迷你播放器的队列按钮都切换同一个根级 `PlaybackQueueSheet`。
用 `rememberSaveable` 保存最近 32 个已处理 id，精确去重重组、重复 bridge 投递和 Activity 重建；不能只保存“最高 id”，否则异步乱序事件可能被误丢弃：

```kotlin
var handledNoticeIds by rememberSaveable {
    mutableStateOf(arrayListOf<Long>())
}
LaunchedEffect(playbackController) {
    playbackController.notices.collect { notice ->
        if (notice.id in handledNoticeIds) return@collect
        handledNoticeIds = ArrayList(
            (handledNoticeIds + notice.id).takeLast(32),
        )
        val result = snackbarHostState.showSnackbar(
            MediaSnackbarVisuals(
                message = notice.message,
                kind = MediaSnackbarKind.ERROR,
                actionLabel = if (
                    notice.action == PlaybackNoticeAction.RETRY_PERSISTENCE
                ) "重试" else null,
            ),
        )
        if (result == SnackbarResult.ActionPerformed) {
            playbackController.retryPersistence()
        }
    }
}
```

Snackbar 位于根 scaffold，因此队列 Sheet 打开或关闭都可见；不得把 notice 写入 Player error overlay。
普通队列项删除调用 `remove` 后显示“撤销”，操作触发时按播放器计划规定依次调用 `append`
和 `move`；当前项删除与清空其他仍走确认弹层。根任务只接线已完成的视觉组件，不在此处重新
设计它们。

- [ ] **Step 4: Add the queue-sheet-independent Snackbar test**

在 `PlaybackQueueUiTest.kt` 添加：

```kotlin
@Test
fun persistence_notice_is_visible_with_queue_open_and_retry_keeps_the_sheet_open() {
    val container = FakeAppContainer(
        ApplicationProvider.getApplicationContext(),
    )
    rule.setContent { MediaViewerApp(container) }
    val fakePlaybackController = container.fakePlaybackController
    fakePlaybackController.replaceQueue(
        items = listOf(
            QueueMediaItem(
                mediaKey = "a",
                name = "第一首",
                logicalUrl = "http://media.test/a.mp3",
                kind = MediaKind.AUDIO,
            ),
        ),
        startMediaKey = "a",
    )
    rule.onNodeWithContentDescription("打开队列").performClick()
    val notice = PlaybackNotice(
        id = 9L,
        kind = PlaybackNoticeKind.QUEUE_SAVE_FAILED,
        message = "播放队列保存失败",
        action = PlaybackNoticeAction.RETRY_PERSISTENCE,
    )
    fakePlaybackController.emitNotice(notice)
    fakePlaybackController.emitNotice(notice)

    rule.onNodeWithText("播放队列保存失败").assertIsDisplayed()
    rule.onNodeWithText("重试").performClick()
    assertEquals(1, fakePlaybackController.retryPersistenceCalls)
    rule.onNodeWithText("播放队列").assertIsDisplayed()
    rule.waitUntil(5_000) {
        rule.onAllNodesWithText("播放队列保存失败")
            .fetchSemanticsNodes().isEmpty()
    }
    assertEquals(1, fakePlaybackController.retryPersistenceCalls)
    container.close()
}
```

- [ ] **Step 5: Run root integration tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.local.mediaviewer.navigation.CurrentPlayerNavigationTest' '-Pkotlin.incremental=false'
.\gradlew.bat :app:compileDebugAndroidTestKotlin '-Pkotlin.incremental=false'
```

Expected: pure navigation tests pass; all AndroidTest sources compile. If an API 36 emulator/device is connected, run the two focused suites:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaViewerNavigationTest' '-Pkotlin.incremental=false'
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PlaybackQueueUiTest' '-Pkotlin.incremental=false'
```

Expected on a connected target: both suites pass. If no target is attached, record `connectedDebugAndroidTest: NOT RUN (no device/emulator)`; compilation passing must not be reported as runtime passing.

- [ ] **Step 6: Commit the single-owner integration**

```powershell
git add app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt app/src/main/java/com/local/mediaviewer/navigation/CurrentPlayerNavigation.kt app/src/test/java/com/local/mediaviewer/navigation/CurrentPlayerNavigationTest.kt app/src/androidTest/java/com/local/mediaviewer/MediaViewerNavigationTest.kt app/src/androidTest/java/com/local/mediaviewer/PlaybackQueueUiTest.kt app/src/androidTest/java/com/local/mediaviewer/testing/FakeAppContainer.kt
git commit -m "fix(android): integrate recoverable app flows"
```

`CurrentPlayerNavigation.kt` 若测试证明无需变化，不会被暂存。提交后由同一负责人检查 `git show --name-only --format= HEAD`，确认没有 Tasks 1–6 之外的共享文件被意外带入。

---

### Task 8: I5 — Lock Activity Recreation, Back Stack, Queue, and Background Behavior With Regression Gates

**Owner rule:** 本任务以测试和证据为主，不主动修改产品代码。若回归暴露 `MediaViewerApp.kt` 或根导航缺陷，把可复现测试和失败日志交回 Task 7 的同一集成负责人；若暴露 F2/F6 控制器链缺陷，按原文件所有权退回对应负责人。

**Files:**

- Create: `app/src/androidTest/java/com/local/mediaviewer/AppActivityRecreationTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/MediaViewerNavigationTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/MediaSessionControlsTest.kt`
- Modify: `app/src/androidTest/java/com/local/mediaviewer/BackgroundPlaybackTest.kt`
- Create: `docs/verification/2026-07-31-app-flow-hardening.md`

**Interfaces consumed:** final F1–F7 / I1–I4 behavior, existing service-owned queue snapshot and stable `mediaKey` restoration.

**Interfaces produced:** executable Activity recreation regression suite and an evidence matrix separating automated and manual results. 本计划不把 `ActivityScenario.recreate()` 描述为进程死亡；真正的系统杀进程/通知冷启动仍为人工设备项。

- [ ] **Step 1: Add a concrete Activity recreation suite**

`AppActivityRecreationTest.kt` 使用一个写在同文件内的 `ExternalResource` 作为 outer rule，在 `createAndroidComposeRule<MainActivity>()` 启动 Activity **之前**替换 `MediaViewerApplication.container`，after 中恢复旧 container 并关闭 fake。规则顺序固定：

```kotlin
private class FakeContainerRule : ExternalResource() {
    private val application =
        ApplicationProvider.getApplicationContext<MediaViewerApplication>()
    private lateinit var original: AppContainer
    lateinit var container: FakeAppContainer
        private set

    override fun before() {
        original = application.container
        container = FakeAppContainer(application)
        application.container = container
    }

    override fun after() {
        container.close()
        application.container = original
    }
}

private val containerRule = FakeContainerRule()
private val compose = createAndroidComposeRule<MainActivity>()

@get:Rule
val rules: TestRule = RuleChain
    .outerRule(containerRule)
    .around(compose)
```

`FakeContainerRule` 暴露 `lateinit var container: FakeAppContainer`；测试只向 fake controller 写 stable `mediaKey`/logical URL，不注入临时 IPv4 request URL。添加：

```kotlin
@Test
fun activity_recreation_restores_player_route_and_service_owned_item() {
    val item = QueueMediaItem(
        mediaKey = "http://media.test/video-b",
        name = "video-b",
        logicalUrl = "http://media.test/video-b",
        kind = MediaKind.VIDEO,
    )
    containerRule.container.fakePlaybackController.emitSessionState(
        PlaybackSessionState(
            playback = PlaybackState(
                status = PlaybackStatus.PAUSED,
                positionMs = 12_345L,
                durationMs = 60_000L,
            ),
            queue = PlaybackQueue(
                items = listOf(item),
                currentMediaKey = item.mediaKey,
            ),
            currentItem = item,
        ),
    )

    compose.onNodeWithContentDescription("打开播放器：video-b").performClick()
    compose.onNodeWithText("video-b").assertIsDisplayed()
    compose.activityRule.scenario.recreate()
    compose.onNodeWithText("video-b").assertIsDisplayed()
    assertEquals(
        12_345L,
        containerRule.container.fakePlaybackController
            .sessionState.value.playback.positionMs,
    )

    compose.onNodeWithContentDescription("返回").performClick()
    compose.onNodeWithText("MediaViewer").assertIsDisplayed()
}

@Test
fun recreation_does_not_replay_an_old_persistence_notice() {
    val controller = containerRule.container.fakePlaybackController
    compose.onNodeWithText("MediaViewer").assertIsDisplayed()
    controller.emitNotice(
        PlaybackNotice(
            id = 100L,
            kind = PlaybackNoticeKind.QUEUE_SAVE_FAILED,
            message = "播放队列保存失败",
            action = PlaybackNoticeAction.RETRY_PERSISTENCE,
        ),
    )
    compose.onNodeWithText("播放队列保存失败").assertIsDisplayed()
    compose.activityRule.scenario.recreate()
    compose.waitForIdle()
    compose.onNodeWithText("播放队列保存失败").assertDoesNotExist()
}
```

- [ ] **Step 2: Add background and restoration assertions to existing suites**

`BackgroundPlaybackTest.kt` 保留并强化仓库当前可观测的测试 `videoKeepsPlayingWithoutSurfaceAndReattachesContinuously()`：

- 前台 `VideoOutputConnectionState.Attached`；
- Activity 到 `CREATED` 后变为 `Detached`；
- 无 Surface 的 2 秒内 Media3 position 至少推进 500ms 且 `isPlaying == true`；
- Activity 回 `RESUMED` 后重新 `Attached`，position 不倒退。

当前 harness 无可靠 presented-frame timestamp/pixel callback，因此 **不新增** `lastPresentedFrameTimestampMs()` 等虚构 API；“后台画面停在离开帧”只在 Step 7 ARM64 人工观察，不以 position/Surface 断言冒充。

在 `MediaSessionControlsTest.kt` 覆盖：

- 当前 Activity 收到 notification request 只导航一次；
- queue snapshot 恢复 current `mediaKey`、顺序、mode、speed、position；
- persistence notice 的重试不改变 `playWhenReady`；
- controller 断开期间的旧 notice 不在重连后 replay。

在 `MediaViewerNavigationTest.kt` 覆盖：

- BOOT-02/03/04/05/06；
- NAV-01/04/05；
- Player Connecting 中返回后等待 5 秒不会再次导航；
- Browser 深目录重连恢复仍停留原 breadcrumbs。

- [ ] **Step 3: Compile the new regression suite and capture the initial failure**

```powershell
$env:ANDROID_HOME='C:\Users\Administrator\AppData\Local\Android\Sdk'
.\gradlew.bat :app:compileDebugAndroidTestKotlin '-Pkotlin.incremental=false'
```

Expected red before harness/test integration is complete: Activity recreation 后 route 丢失或旧 notice 再次出现。只实现上文已定义的 `FakeContainerRule` 与 deterministic fake hooks；任何生产失败都退回原所有者，并保持新测试为红直到修复落地。

- [ ] **Step 4: Run all JVM gates serially**

```powershell
.\gradlew.bat :app:testDebugUnitTest '-Pkotlin.incremental=false'
```

Expected: all JVM tests pass. Record total tests, duration and any skipped tests in `docs/verification/2026-07-31-app-flow-hardening.md`.

- [ ] **Step 5: Run static and AndroidTest compilation gates serially**

```powershell
.\gradlew.bat :app:lintDebug '-Pkotlin.incremental=false'
.\gradlew.bat :app:compileDebugAndroidTestKotlin '-Pkotlin.incremental=false'
```

Expected: both commands exit 0 with no new fatal lint issue and all AndroidTest sources compile.

- [ ] **Step 6: Run the runtime suites on one API 36 target**

Run one command at a time:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.AppActivityRecreationTest' '-Pkotlin.incremental=false'
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaViewerNavigationTest' '-Pkotlin.incremental=false'
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.BrowserScreenTest' '-Pkotlin.incremental=false'
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.HomeSettingsScreenTest' '-Pkotlin.incremental=false'
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.ImageReaderScreenTest' '-Pkotlin.incremental=false'
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.PlaybackQueueUiTest' '-Pkotlin.incremental=false'
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.MediaSessionControlsTest' '-Pkotlin.incremental=false'
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.BackgroundPlaybackTest' '-Pkotlin.incremental=false'
```

Expected: every invoked suite passes. When a target is unavailable, do not fabricate runtime evidence; mark every suite `NOT RUN` and retain the successful compile gate separately.

- [ ] **Step 7: Execute the manual/device matrix without expanding scope**

On an ARM64 device and the user's real server, record:

| Scenario | Required observation |
|---|---|
| Initial server offline | finite error with Retry and Settings; no endless spinner |
| Browser deep child failure | parent list/breadcrumbs remain; Back consumes failure |
| Player controller failure | explicit reconnect; queue and play intent remain |
| Pause → seek → play | remains paused after seek; target frame shown; resumes at target |
| Background video | audio continues; frame stops; foreground video catches service position |
| Queue persistence failure | one non-blocking Snackbar; playback/queue remain; retry works |
| Image automatic budget exhausted | explicit reconnect retries failed image only |
| Settings save failure | stays on page; direct retry; dirty Back confirmation |
| Notification cold start | opens current item; Back goes Home |
| Real problematic video | audio and picture both render for the supplied server sample |

Do not mark a row passed from JVM/Compose simulation alone. Record device model, Android API, app commit, server endpoint category, timestamp and outcome.

- [ ] **Step 8: Write the evidence report**

`docs/verification/2026-07-31-app-flow-hardening.md` must contain:

文档固定包含标题 `# App Flow Hardening Verification`、`git rev-parse HEAD` 输出的完整 40 位 Commit、JVM 命令与测试数、Lint、AndroidTest 编译、API 36 runtime、ARM64 manual、Real server，以及按 BOOT/NAV/SET/NET/IMG/PLAY/QUEUE/BG/FOCUS 编号的结果表。每一项写入执行时取得的 `PASS`、`FAIL` 或 `NOT RUN：具体原因`，不能保留示例值或角括号标记。

- [ ] **Step 9: Commit regression tests and verified evidence**

```powershell
git add app/src/androidTest/java/com/local/mediaviewer/AppActivityRecreationTest.kt app/src/androidTest/java/com/local/mediaviewer/MediaViewerNavigationTest.kt app/src/androidTest/java/com/local/mediaviewer/MediaSessionControlsTest.kt app/src/androidTest/java/com/local/mediaviewer/BackgroundPlaybackTest.kt docs/verification/2026-07-31-app-flow-hardening.md
git commit -m "test(android): lock app flow recovery regressions"
```

Do not include an APK in this commit; Release packaging remains a separate, later gate after flow and UI plans both pass.

---

## Requirement Traceability

| Flow | Implemented by | Primary proof |
|---|---|---|
| F1 app-owned session | Task 1 + Task 7 | `AppSessionViewModelTest`, non-Home startup navigation test |
| F2 player bootstrap | Task 2 + Task 7 | resolver tests, bootstrap UI test, Player failure navigation test |
| F3 Browser stable content | Task 3 + Task 7 | Browser ViewModel/Screen tests, global reconnect navigation test |
| F4 Settings recovery | Task 4 | Settings ViewModel and discard-dialog tests |
| F5 Image manual recovery | Task 5 | selective generation and retry-label tests |
| F6 playback notices | Task 6 + Task 7 | coordinator/codec/callback tests, global Snackbar test |
| F7 Player source/exit | Task 7 | Browser return, notification return, empty queue exit tests |
| I1 root integration | Task 7 | single-owner root tests |
| I2 Browser screen | Task 3 | retained-content Compose test |
| I3 Settings screen | Task 4 | save/dirty Compose test |
| I4 Image screen | Task 5 | failure-action Compose test |
| I5 recreation/background regression | Task 8 | Activity recreation, MediaSession and background suites plus manual cold-start matrix |

## Completion Review

Before claiming this plan implemented:

- [ ] Confirm changed production, test and verification files contain no unfinished implementation markers or example evidence values.
- [ ] Confirm `git log --oneline` contains the eight task commits or documented squash equivalents in dependency order, with F2 before F6.
- [ ] Confirm `git diff 2949a99...HEAD -- app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt` was authored/reviewed by the Task 7 integration owner only.
- [ ] Confirm `PlaybackController.kt` and `Media3PlaybackController.kt` were not modified concurrently and the F2 reconnect tests remain green after F6.
- [ ] Confirm every F1–F7 and I1–I5 row above has a test/evidence owner.
- [ ] Confirm no test maps a request URL to persistent identity and no persistence notice maps to `PlaybackSessionState.errorMessage`.
- [ ] Confirm automated passing gates and unrun device/real-server gates are reported separately.
- [ ] Only after this flow plan and the independent UI redesign plan both pass, run the repository's normal arm64-v8a personal Release build and publish its APK path, SHA-256, signing limitation and device-install result.
