# 应用外壳、首页与设置页 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成手工依赖装配、进程启动连接、首页状态展示和“测试成功后才保存”的设置流程。

**Architecture:** `MediaViewerApplication` 持有 `AppContainer`；Activity 只启动 `MediaViewerApp`。Home 和 Settings 各自通过 ViewModel 适配 `ServerSessionManager`，Composable 只接收不可变状态和回调，便于不用真实网络做 UI 测试。

**Tech Stack:** Jetpack Compose Material 3、Navigation Compose 2.9.8、Lifecycle ViewModel 2.11.0、手工依赖装配。

## Global Constraints

- 首页显示应用名、当前服务器状态、设置按钮和两个固定入口。
- 未连接时显示简体中文错误和重试，不显示失效目录内容。
- 设置页显示 URL、解析 IPv4 列表、当前选择、测试和保存。
- 只有两个根目录探测成功后才允许保存。
- 默认 URL 为 `http://192.168.1.17:8080`。
- AppContainer 不引入 Hilt、Koin 或其他 DI 框架。

---

### Task 7: AppContainer、Home 与 Settings

**Files:**

- Modify: `app/src/main/java/com/local/mediaviewer/MediaViewerApplication.kt`
- Modify: `app/src/main/java/com/local/mediaviewer/MainActivity.kt`
- Create: `app/src/main/java/com/local/mediaviewer/app/AppContainer.kt`
- Create: `app/src/main/java/com/local/mediaviewer/app/MediaViewerApp.kt`
- Create: `app/src/main/java/com/local/mediaviewer/home/HomeViewModel.kt`
- Create: `app/src/main/java/com/local/mediaviewer/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/local/mediaviewer/navigation/Destinations.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/components/AppErrorPanel.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/home/HomeScreen.kt`
- Create: `app/src/main/java/com/local/mediaviewer/ui/settings/SettingsScreen.kt`
- Test: `app/src/test/java/com/local/mediaviewer/home/HomeViewModelTest.kt`
- Test: `app/src/test/java/com/local/mediaviewer/settings/SettingsViewModelTest.kt`
- Test: `app/src/androidTest/java/com/local/mediaviewer/HomeSettingsScreenTest.kt`

**Interfaces:**

- Consumes:

```kotlin
interface ServerSessionManager
interface ServerSettingsRepository
interface BrowserRepository
```

- Produces:

```kotlin
interface AppContainer {
    val settingsRepository: ServerSettingsRepository
    val sessionManager: ServerSessionManager
    val browserRepository: BrowserRepository
}

sealed interface HomeUiState {
    data object Connecting : HomeUiState
    data class Connected(val ipv4: String) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

data class SettingsUiState(
    val input: String,
    val isTesting: Boolean,
    val resolvedIpv4s: List<String>,
    val selectedIpv4: String?,
    val errorMessage: String?,
    val canSave: Boolean,
)
```

- [ ] **Step 1: 写 Home ViewModel 失败测试**

`HomeViewModelTest.kt`：

```kotlin
package com.local.mediaviewer.home

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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun before() = Dispatchers.setMain(dispatcher)
    @After fun after() = Dispatchers.resetMain()

    @Test
    fun `创建时连接并映射选中 IPv4`() = runTest(dispatcher) {
        val session = HomeFakeSession()
        val viewModel = HomeViewModel(session)
        advanceUntilIdle()

        assertEquals(1, session.connectCalls)
        assertEquals(
            HomeUiState.Connected("192.168.1.17"),
            viewModel.uiState.value,
        )
    }
}

private class HomeFakeSession : ServerSessionManager {
    private val mutable = MutableStateFlow<ServerSessionState>(
        ServerSessionState.Connecting,
    )
    override val state: StateFlow<ServerSessionState> = mutable
    var connectCalls = 0
    override suspend fun connectSaved() {
        connectCalls += 1
        mutable.value = ServerSessionState.Connected(
            SessionEndpoint(
                "http://media.example:8080",
                "http://192.168.1.17:8080",
                "192.168.1.17",
            ),
            listOf("192.168.1.17"),
        )
    }
    override suspend fun testCandidate(input: String) =
        error("not used")
    override suspend fun saveCandidate(result: ConnectionTestResult) = Unit
    override suspend fun refreshAfterRequestFailure() =
        AppResult.Failure(
            com.local.mediaviewer.core.AppError.NetworkFailure("not used"),
        )
}
```

- [ ] **Step 2: 运行 Home 测试并观察预期失败**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.home.HomeViewModelTest'
```

Expected:

```text
Kotlin compilation fails because HomeViewModel and HomeUiState are unresolved
```

- [ ] **Step 3: 实现 Home 状态适配**

`HomeViewModel.kt`：

```kotlin
package com.local.mediaviewer.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Connecting : HomeUiState
    data class Connected(val ipv4: String) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(
    private val session: ServerSessionManager,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = session.state
        .map { state ->
            when (state) {
                ServerSessionState.Connecting -> HomeUiState.Connecting
                is ServerSessionState.Connected ->
                    HomeUiState.Connected(state.endpoint.ipv4)
                is ServerSessionState.Failed ->
                    HomeUiState.Error(state.error.userMessage)
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            HomeUiState.Connecting,
        )

    init {
        viewModelScope.launch { session.connectSaved() }
    }

    fun retry() {
        viewModelScope.launch { session.connectSaved() }
    }
}
```

- [ ] **Step 4: 写 Settings 事务失败测试**

`SettingsViewModelTest.kt`：

```kotlin
package com.local.mediaviewer.settings

import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.ServerConfig
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.model.ValidatedServerUrl
import com.local.mediaviewer.network.ConnectionTestResult
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun before() = Dispatchers.setMain(dispatcher)
    @After fun after() = Dispatchers.resetMain()

    @Test
    fun `测试成功前不能保存且修改输入会作废旧结果`() = runTest(dispatcher) {
        val settings = SettingsFakeRepository(ServerConfig())
        val result = ConnectionTestResult(
            server = ValidatedServerUrl(
                "http://media.example:8080",
                "media.example",
                8080,
                false,
            ),
            resolvedIpv4s = listOf("10.0.0.8", "203.0.113.7"),
            endpoint = SessionEndpoint(
                "http://media.example:8080",
                "http://203.0.113.7:8080",
                "203.0.113.7",
            ),
        )
        val session = SettingsFakeSession(AppResult.Success(result))
        val viewModel = SettingsViewModel(settings, session)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canSave)
        viewModel.onInputChanged("http://media.example:8080")
        viewModel.testConnection()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canSave)
        assertEquals("203.0.113.7", viewModel.uiState.value.selectedIpv4)

        viewModel.onInputChanged("http://other.example:8080")
        assertFalse(viewModel.uiState.value.canSave)
        viewModel.save()
        advanceUntilIdle()
        assertEquals(0, session.saveCalls)
    }
}

private class SettingsFakeRepository(initial: ServerConfig) :
    ServerSettingsRepository {
    private val mutable = MutableStateFlow(initial)
    override val config: Flow<ServerConfig> = mutable
    override suspend fun current() = mutable.value
    override suspend fun save(config: ServerConfig) { mutable.value = config }
}

private class SettingsFakeSession(
    private val testResult: AppResult<ConnectionTestResult>,
) : ServerSessionManager {
    private val mutable = MutableStateFlow<ServerSessionState>(
        ServerSessionState.Connecting,
    )
    override val state: StateFlow<ServerSessionState> = mutable
    var saveCalls = 0
    override suspend fun connectSaved() = Unit
    override suspend fun testCandidate(input: String) = testResult
    override suspend fun saveCandidate(result: ConnectionTestResult) {
        saveCalls += 1
    }
    override suspend fun refreshAfterRequestFailure() =
        AppResult.Failure(
            com.local.mediaviewer.core.AppError.NetworkFailure("not used"),
        )
}
```

- [ ] **Step 5: 实现 Settings ViewModel**

`SettingsViewModel.kt`：

```kotlin
package com.local.mediaviewer.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.network.ConnectionTestResult
import com.local.mediaviewer.session.ServerSessionManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val input: String = "",
    val isTesting: Boolean = false,
    val resolvedIpv4s: List<String> = emptyList(),
    val selectedIpv4: String? = null,
    val errorMessage: String? = null,
    val canSave: Boolean = false,
)

class SettingsViewModel(
    private val settings: ServerSettingsRepository,
    private val session: ServerSessionManager,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()
    private val mutableSaved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = mutableSaved.asSharedFlow()
    private var successfulResult: ConnectionTestResult? = null

    init {
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(
                input = settings.current().logicalBaseUrl,
            )
        }
    }

    fun onInputChanged(value: String) {
        successfulResult = null
        mutableUiState.value = mutableUiState.value.copy(
            input = value,
            resolvedIpv4s = emptyList(),
            selectedIpv4 = null,
            errorMessage = null,
            canSave = false,
        )
    }

    fun testConnection() {
        val candidate = mutableUiState.value.input
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(
                isTesting = true,
                errorMessage = null,
                canSave = false,
            )
            when (val result = session.testCandidate(candidate)) {
                is AppResult.Success -> {
                    successfulResult = result.value
                    mutableUiState.value = mutableUiState.value.copy(
                        input = result.value.server.logicalBaseUrl,
                        isTesting = false,
                        resolvedIpv4s = result.value.resolvedIpv4s,
                        selectedIpv4 = result.value.endpoint.ipv4,
                        canSave = true,
                    )
                }
                is AppResult.Failure -> {
                    successfulResult = null
                    mutableUiState.value = mutableUiState.value.copy(
                        isTesting = false,
                        errorMessage = result.error.userMessage,
                        canSave = false,
                    )
                }
            }
        }
    }

    fun save() {
        val result = successfulResult ?: return
        viewModelScope.launch {
            session.saveCandidate(result)
            mutableSaved.emit(Unit)
        }
    }
}
```

- [ ] **Step 6: 运行 ViewModel 测试并确认通过**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.home.HomeViewModelTest' `
  --tests 'com.local.mediaviewer.settings.SettingsViewModelTest'
```

Expected:

```text
Home and Settings ViewModel tests pass
```

- [ ] **Step 7: 实现手工 AppContainer**

`AppContainer.kt`：

```kotlin
package com.local.mediaviewer.app

import android.content.Context
import com.local.mediaviewer.browser.BrowserRepository
import com.local.mediaviewer.browser.DefaultBrowserRepository
import com.local.mediaviewer.network.DefaultCaddyDirectoryClient
import com.local.mediaviewer.network.DefaultConnectionProbe
import com.local.mediaviewer.network.DefaultDirectoryJsonParser
import com.local.mediaviewer.network.OkHttpDirectoryProbeTransport
import com.local.mediaviewer.network.SystemIpv4Resolver
import com.local.mediaviewer.session.DefaultServerSessionManager
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.settings.DataStoreServerSettingsRepository
import com.local.mediaviewer.settings.ServerSettingsRepository
import com.local.mediaviewer.settings.serverSettingsDataStore

interface AppContainer {
    val settingsRepository: ServerSettingsRepository
    val sessionManager: ServerSessionManager
    val browserRepository: BrowserRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
    override val settingsRepository: ServerSettingsRepository =
        DataStoreServerSettingsRepository(context.serverSettingsDataStore)

    private val directoryParser = DefaultDirectoryJsonParser()
    private val directoryClient = DefaultCaddyDirectoryClient(
        parser = directoryParser,
    )
    private val resolver = SystemIpv4Resolver()
    private val probe = DefaultConnectionProbe(
        OkHttpDirectoryProbeTransport(),
        directoryParser,
    )

    override val sessionManager: ServerSessionManager =
        DefaultServerSessionManager(settingsRepository, resolver, probe)

    override val browserRepository: BrowserRepository =
        DefaultBrowserRepository(directoryClient, sessionManager)
}
```

修改 `MediaViewerApplication.kt`：

```kotlin
package com.local.mediaviewer

import android.app.Application
import com.local.mediaviewer.app.AppContainer
import com.local.mediaviewer.app.DefaultAppContainer

class MediaViewerApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
```

- [ ] **Step 8: 实现路由和可复用错误控件**

`Destinations.kt`：

```kotlin
package com.local.mediaviewer.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object SettingsRoute
```

`AppErrorPanel.kt`：

```kotlin
package com.local.mediaviewer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

@Composable
fun AppErrorPanel(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message)
        Button(onClick = onRetry) { Text("重试") }
    }
}
```

- [ ] **Step 9: 实现首页和设置页**

`HomeScreen.kt` 的公开签名与核心内容：

```kotlin
package com.local.mediaviewer.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.home.HomeUiState
import com.local.mediaviewer.model.RootShare
import com.local.mediaviewer.ui.components.AppErrorPanel

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRoot: (RootShare) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("mediaviewer") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                HomeUiState.Connecting -> CircularProgressIndicator()
                is HomeUiState.Error -> AppErrorPanel(state.message, onRetry)
                is HomeUiState.Connected -> {
                    Text("当前 IPv4：${state.ipv4}")
                    RootShare.entries.forEach { root ->
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenRoot(root) },
                        ) {
                            Text(root.displayName, Modifier.padding(20.dp))
                        }
                    }
                }
            }
        }
    }
}
```

`SettingsScreen.kt` 的公开签名与必要控件：

```kotlin
package com.local.mediaviewer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.settings.SettingsUiState

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onInputChanged: (String) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("服务器设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = onInputChanged,
                label = { Text("服务器 URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("server_url"),
            )
            Button(
                onClick = onTest,
                enabled = !state.isTesting,
                modifier = Modifier.testTag("test_connection"),
            ) {
                Text(if (state.isTesting) "正在测试…" else "测试连接")
            }
            state.resolvedIpv4s.forEach { ip ->
                Text(if (ip == state.selectedIpv4) "已选择：$ip" else ip)
            }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier.testTag("save_server"),
            ) {
                Text("保存")
            }
        }
    }
}
```

- [ ] **Step 10: 实现 Navigation 外壳和 Activity 接线**

`MediaViewerApp.kt`：

```kotlin
package com.local.mediaviewer.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.local.mediaviewer.home.HomeViewModel
import com.local.mediaviewer.navigation.HomeRoute
import com.local.mediaviewer.navigation.SettingsRoute
import com.local.mediaviewer.settings.SettingsViewModel
import com.local.mediaviewer.ui.home.HomeScreen
import com.local.mediaviewer.ui.settings.SettingsScreen

@Composable
fun MediaViewerApp(container: AppContainer) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            val home: HomeViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { HomeViewModel(container.sessionManager) }
                },
            )
            val state by home.uiState.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onRetry = home::retry,
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenRoot = {},
            )
        }
        composable<SettingsRoute> {
            val settings: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        SettingsViewModel(
                            container.settingsRepository,
                            container.sessionManager,
                        )
                    }
                },
            )
            val state by settings.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(settings) {
                settings.saved.collect { navController.popBackStack() }
            }
            SettingsScreen(
                state,
                settings::onInputChanged,
                settings::testConnection,
                settings::save,
                navController::popBackStack,
            )
        }
    }
}
```

修改 `MainActivity.kt`：

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MediaViewerApplication).container
        setContent {
            MediaViewerTheme {
                MediaViewerApp(container)
            }
        }
    }
}
```

- [ ] **Step 11: 写 Compose 页面测试**

`HomeSettingsScreenTest.kt`：

```kotlin
package com.local.mediaviewer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.local.mediaviewer.home.HomeUiState
import com.local.mediaviewer.settings.SettingsUiState
import com.local.mediaviewer.ui.home.HomeScreen
import com.local.mediaviewer.ui.settings.SettingsScreen
import org.junit.Rule
import org.junit.Test

class HomeSettingsScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun connectedHomeShowsBothRoots() {
        rule.setContent {
            HomeScreen(
                HomeUiState.Connected("192.168.1.17"),
                {},
                {},
                {},
            )
        }
        rule.onNodeWithText("MiddleDir").assertIsDisplayed()
        rule.onNodeWithText("pik").assertIsDisplayed()
    }

    @Test
    fun settingsSaveFollowsProbeState() {
        rule.setContent {
            SettingsScreen(
                SettingsUiState(
                    input = "http://media.example:8080",
                    resolvedIpv4s = listOf("203.0.113.7"),
                    selectedIpv4 = "203.0.113.7",
                    canSave = false,
                ),
                {},
                {},
                {},
                {},
            )
        }
        rule.onNodeWithTag("test_connection").assertIsEnabled()
        rule.onNodeWithTag("save_server").assertIsNotEnabled()
        rule.onNodeWithText("已选择：203.0.113.7").assertIsDisplayed()
    }
}
```

- [ ] **Step 12: 运行本任务门禁**

Run:

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests 'com.local.mediaviewer.home.*' `
  --tests 'com.local.mediaviewer.settings.SettingsViewModelTest'
.\gradlew.bat lintDebug assembleDebug
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.local.mediaviewer.HomeSettingsScreenTest
```

Expected:

```text
Home and Settings unit tests pass
Compose screen tests pass
Lint reports 0 errors
Debug APK builds
```

- [ ] **Step 13: 提交**

```powershell
git add app/src/main/java/com/local/mediaviewer `
  app/src/test/java/com/local/mediaviewer/home `
  app/src/test/java/com/local/mediaviewer/settings/SettingsViewModelTest.kt `
  app/src/androidTest/java/com/local/mediaviewer/HomeSettingsScreenTest.kt
git commit -m "feat: add home and validated server settings UI"
```
