package com.local.mediaviewer.settings

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.image.ImageReaderMode
import com.local.mediaviewer.image.ReaderPreferencesRepository
import com.local.mediaviewer.model.ServerConfig
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.model.ValidatedServerUrl
import com.local.mediaviewer.network.ConnectionTestResult
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun before() = Dispatchers.setMain(dispatcher)

    @After
    fun after() = Dispatchers.resetMain()

    @Test
    fun `测试成功前不能保存且修改输入会作废旧结果`() = runTest(dispatcher) {
        val settings = SettingsFakeRepository(ServerConfig())
        val successful = successfulResult()
        val session = SettingsFakeSession { AppResult.Success(successful) }
        val viewModel = SettingsViewModel(
            settings,
            SettingsFakeReaderPreferences(),
            session,
        )
        advanceUntilIdle()

        assertEquals(ServerConfig.DEFAULT_SERVER_URL, viewModel.uiState.value.input)
        assertFalse(viewModel.uiState.value.canSave)
        viewModel.onInputChanged("http://media.example:8080")
        viewModel.testConnection()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canSave)
        assertEquals(
            listOf("10.0.0.8", "203.0.113.7"),
            viewModel.uiState.value.resolvedIpv4s,
        )
        assertEquals("203.0.113.7", viewModel.uiState.value.selectedIpv4)

        viewModel.onInputChanged("http://other.example:8080")
        assertFalse(viewModel.uiState.value.canSave)
        viewModel.save()
        advanceUntilIdle()

        assertEquals(0, session.saveCalls)
    }

    @Test
    fun `测试失败显示中文错误且不能保存`() = runTest(dispatcher) {
        val session = SettingsFakeSession {
            AppResult.Failure(AppError.NoIpv4Address)
        }
        val viewModel = SettingsViewModel(
            SettingsFakeRepository(ServerConfig()),
            SettingsFakeReaderPreferences(),
            session,
        )
        advanceUntilIdle()

        viewModel.onInputChanged("http://v6.example:8080")
        viewModel.testConnection()
        advanceUntilIdle()

        assertEquals("未解析到 IPv4", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isTesting)
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `测试进行中修改输入会取消旧结果`() = runTest(dispatcher) {
        val deferred = CompletableDeferred<AppResult<ConnectionTestResult>>()
        val session = SettingsFakeSession { deferred.await() }
        val viewModel = SettingsViewModel(
            SettingsFakeRepository(ServerConfig()),
            SettingsFakeReaderPreferences(),
            session,
        )
        advanceUntilIdle()
        viewModel.onInputChanged("http://media.example:8080")
        viewModel.testConnection()
        runCurrent()
        assertTrue(viewModel.uiState.value.isTesting)

        viewModel.onInputChanged("http://new.example:8080")
        assertFalse(viewModel.uiState.value.isTesting)
        deferred.complete(AppResult.Success(successfulResult()))
        advanceUntilIdle()

        assertEquals("http://new.example:8080", viewModel.uiState.value.input)
        assertFalse(viewModel.uiState.value.canSave)
        assertNull(viewModel.uiState.value.selectedIpv4)
    }

    @Test
    fun `成功结果显式保存后提交同一探测结果`() = runTest(dispatcher) {
        val successful = successfulResult()
        val session = SettingsFakeSession { AppResult.Success(successful) }
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

        assertEquals(1, session.saveCalls)
        assertEquals(successful, session.savedResult)
    }

    @Test
    fun `阅读方式立即独立保存且不调用服务器`() =
        runTest(dispatcher) {
            val reader = SettingsFakeReaderPreferences(
                ImageReaderMode.COMIC,
            )
            val session = SettingsFakeSession {
                error("阅读偏好不应探测服务器")
            }
            val viewModel = SettingsViewModel(
                SettingsFakeRepository(ServerConfig()),
                reader,
                session,
            )
            advanceUntilIdle()

            viewModel.onDefaultImageModeChanged(
                ImageReaderMode.SINGLE,
            )
            advanceUntilIdle()

            assertEquals(
                ImageReaderMode.SINGLE,
                reader.currentDefaultMode(),
            )
            assertEquals(
                ImageReaderMode.SINGLE,
                viewModel.uiState.value.defaultImageMode,
            )
            assertEquals(1, reader.saveCalls)
            assertEquals(0, session.testCalls)
            assertEquals(0, session.saveCalls)
            assertFalse(viewModel.uiState.value.canSave)
        }

    @Test
    fun `已保存单图模式加载时不改变服务器输入`() =
        runTest(dispatcher) {
            val server = ServerConfig(
                logicalBaseUrl =
                    "http://configured.example:8080",
            )
            val viewModel = SettingsViewModel(
                SettingsFakeRepository(server),
                SettingsFakeReaderPreferences(
                    ImageReaderMode.SINGLE,
                ),
                SettingsFakeSession {
                    error("初始化不应探测服务器")
                },
            )

            advanceUntilIdle()

            assertEquals(
                "http://configured.example:8080",
                viewModel.uiState.value.input,
            )
            assertEquals(
                ImageReaderMode.SINGLE,
                viewModel.uiState.value.defaultImageMode,
            )
            assertFalse(viewModel.uiState.value.canSave)
        }

    @Test
    fun `阅读偏好保存失败回滚且不污染服务器表单`() =
        runTest(dispatcher) {
            val reader = SettingsFakeReaderPreferences(
                initial = ImageReaderMode.COMIC,
                failOnSave = true,
            )
            val viewModel = SettingsViewModel(
                SettingsFakeRepository(
                    ServerConfig(
                        logicalBaseUrl =
                            "http://stable.example:8080",
                    ),
                ),
                reader,
                SettingsFakeSession {
                    error("阅读偏好不应探测服务器")
                },
            )
            advanceUntilIdle()

            viewModel.onDefaultImageModeChanged(
                ImageReaderMode.SINGLE,
            )
            advanceUntilIdle()

            assertEquals(
                ImageReaderMode.COMIC,
                viewModel.uiState.value.defaultImageMode,
            )
            assertEquals(
                "默认看图方式保存失败",
                viewModel.uiState.value.imageModeError,
            )
            assertEquals(
                "http://stable.example:8080",
                viewModel.uiState.value.input,
            )
            assertFalse(viewModel.uiState.value.canSave)
        }
}

private fun successfulResult() = ConnectionTestResult(
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

private class SettingsFakeRepository(initial: ServerConfig) :
    ServerSettingsRepository {
    private val mutable = MutableStateFlow(initial)
    override val config: Flow<ServerConfig> = mutable

    override suspend fun current(): ServerConfig = mutable.value

    override suspend fun save(config: ServerConfig) {
        mutable.value = config
    }
}

private class SettingsFakeReaderPreferences(
    initial: ImageReaderMode = ImageReaderMode.COMIC,
    private val failOnSave: Boolean = false,
) : ReaderPreferencesRepository {
    private val mutable = MutableStateFlow(initial)
    override val defaultMode: Flow<ImageReaderMode> = mutable
    var saveCalls = 0
        private set

    override suspend fun currentDefaultMode(): ImageReaderMode =
        mutable.value

    override suspend fun setDefaultMode(mode: ImageReaderMode) {
        saveCalls += 1
        if (failOnSave) {
            error("save failed")
        }
        mutable.value = mode
    }
}

private fun interface CandidateTestBlock {
    suspend fun invoke(input: String): AppResult<ConnectionTestResult>
}

private class SettingsFakeSession(
    private val testBlock: CandidateTestBlock,
) : ServerSessionManager {
    private val mutable = MutableStateFlow<ServerSessionState>(
        ServerSessionState.Connecting,
    )
    override val state: StateFlow<ServerSessionState> = mutable
    var saveCalls = 0
        private set
    var testCalls = 0
        private set
    var savedResult: ConnectionTestResult? = null
        private set

    override suspend fun connectSaved() = Unit

    override suspend fun testCandidate(
        input: String,
    ): AppResult<ConnectionTestResult> {
        testCalls += 1
        return testBlock.invoke(input)
    }

    override suspend fun saveCandidate(result: ConnectionTestResult) {
        saveCalls += 1
        savedResult = result
    }

    override suspend fun refreshAfterRequestFailure(): AppResult<SessionEndpoint> =
        AppResult.Failure(AppError.NetworkFailure("not used"))
}
