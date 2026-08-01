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
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
        val saveGate = CompletableDeferred<Unit>()
        val session = SettingsFakeSession(
            saveGate = saveGate,
        ) { AppResult.Success(successful) }
        val viewModel = SettingsViewModel(
            SettingsFakeRepository(ServerConfig()),
            SettingsFakeReaderPreferences(),
            session,
        )
        var savedEvents = 0
        backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler),
        ) {
            viewModel.saved.collect {
                savedEvents += 1
            }
        }
        advanceUntilIdle()
        viewModel.onInputChanged("http://media.example:8080")
        viewModel.testConnection()
        advanceUntilIdle()

        assertTrue(
            viewModel.uiState.value.hasUnsavedServerChange,
        )
        viewModel.save()
        runCurrent()

        assertTrue(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.canSave)
        viewModel.save()
        runCurrent()
        assertEquals(1, session.saveCalls)

        saveGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, session.saveCalls)
        assertEquals(successful, session.savedResult)
        assertEquals(listOf(successful), session.saveAttempts)
        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.canSave)
        assertFalse(
            viewModel.uiState.value.hasUnsavedServerChange,
        )
        assertNull(viewModel.uiState.value.saveError)
        assertEquals(1, savedEvents)
    }

    @Test
    fun `save failure remains editable and the same tested result can be retried`() =
        runTest(dispatcher) {
            val successful = successfulResult()
            val saveGate = CompletableDeferred<Unit>()
            val session = SettingsFakeSession(
                saveResults = ArrayDeque(
                    listOf(
                        Result.failure<Unit>(
                            IOException("disk full"),
                        ),
                        Result.success(Unit),
                    ),
                ),
                saveGate = saveGate,
            ) {
                AppResult.Success(successful)
            }
            val viewModel = SettingsViewModel(
                SettingsFakeRepository(ServerConfig()),
                SettingsFakeReaderPreferences(),
                session,
            )
            var savedEvents = 0
            backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler),
            ) {
                viewModel.saved.collect {
                    savedEvents += 1
                }
            }
            advanceUntilIdle()
            viewModel.onInputChanged(
                successful.server.logicalBaseUrl,
            )
            viewModel.testConnection()
            advanceUntilIdle()

            viewModel.save()
            runCurrent()

            assertTrue(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.canSave)
            saveGate.complete(Unit)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isSaving)
            assertEquals(
                "保存失败，请重试",
                viewModel.uiState.value.saveError,
            )
            assertTrue(viewModel.uiState.value.canSave)
            assertEquals(
                successful.endpoint.ipv4,
                viewModel.uiState.value.selectedIpv4,
            )
            assertEquals(
                successful.server.logicalBaseUrl,
                viewModel.uiState.value.input,
            )
            assertEquals(0, savedEvents)

            viewModel.save()
            advanceUntilIdle()

            assertEquals(2, session.saveCalls)
            assertEquals(
                listOf(successful, successful),
                session.saveAttempts,
            )
            assertEquals(successful, session.savedResult)
            assertNull(viewModel.uiState.value.saveError)
            assertFalse(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.canSave)
            assertFalse(
                viewModel.uiState.value.hasUnsavedServerChange,
            )
            assertEquals(1, savedEvents)
        }

    @Test
    fun `fatal save errors remain observable instead of becoming retryable failures`() {
        val fatal = AssertionError("fatal save failure")

        val thrown = assertThrows(AssertionError::class.java) {
            runTest(dispatcher) {
                val successful = successfulResult()
                val session = SettingsFakeSession(
                    saveResults = ArrayDeque(
                        listOf(Result.failure<Unit>(fatal)),
                    ),
                ) {
                    AppResult.Success(successful)
                }
                val viewModel = SettingsViewModel(
                    SettingsFakeRepository(ServerConfig()),
                    SettingsFakeReaderPreferences(),
                    session,
                )
                advanceUntilIdle()
                viewModel.onInputChanged(
                    successful.server.logicalBaseUrl,
                )
                viewModel.testConnection()
                advanceUntilIdle()

                viewModel.save()
                advanceUntilIdle()
            }
        }

        assertEquals("fatal save failure", thrown.message)
    }

    @Test
    fun `editing B while A saves leaves B unverified and free to leave`() =
        runTest(dispatcher) {
            val resultA = successfulResult()
            val inputB = "http://other.example:8080"
            val saveGate = CompletableDeferred<Unit>()
            val session = SettingsFakeSession(
                saveGate = saveGate,
            ) {
                AppResult.Success(resultA)
            }
            val viewModel = SettingsViewModel(
                SettingsFakeRepository(ServerConfig()),
                SettingsFakeReaderPreferences(),
                session,
            )
            var savedEvents = 0
            backgroundScope.launch(
                UnconfinedTestDispatcher(testScheduler),
            ) {
                viewModel.saved.collect {
                    savedEvents += 1
                }
            }
            advanceUntilIdle()
            viewModel.onInputChanged(
                resultA.server.logicalBaseUrl,
            )
            viewModel.testConnection()
            advanceUntilIdle()

            viewModel.save()
            runCurrent()
            viewModel.onInputChanged(inputB)

            assertTrue(viewModel.uiState.value.isSaving)
            assertEquals(inputB, viewModel.uiState.value.input)
            assertFalse(viewModel.uiState.value.canSave)
            assertFalse(
                viewModel.uiState.value.hasUnsavedServerChange,
            )

            saveGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(resultA, session.savedResult)
            assertEquals(inputB, viewModel.uiState.value.input)
            assertFalse(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.canSave)
            assertFalse(
                viewModel.uiState.value.hasUnsavedServerChange,
            )
            assertEquals(
                SettingsBackDecision.LEAVE,
                viewModel.requestBack(),
            )
            assertEquals(0, savedEvents)

            viewModel.save()
            advanceUntilIdle()
            assertEquals(1, session.saveCalls)
        }

    @Test
    fun `a successful B probe becomes saveable only after the pending A save completes`() =
        runTest(dispatcher) {
            val resultA = successfulResult()
            val resultB = successfulResult(
                logicalBaseUrl =
                    "http://other.example:8080",
                host = "other.example",
                selectedIpv4 = "203.0.113.9",
            )
            val saveGate = CompletableDeferred<Unit>()
            val session = SettingsFakeSession(
                saveGate = saveGate,
            ) { input ->
                when (input) {
                    resultA.server.logicalBaseUrl ->
                        AppResult.Success(resultA)

                    resultB.server.logicalBaseUrl ->
                        AppResult.Success(resultB)

                    else -> error("unexpected input: $input")
                }
            }
            val viewModel = SettingsViewModel(
                SettingsFakeRepository(ServerConfig()),
                SettingsFakeReaderPreferences(),
                session,
            )
            advanceUntilIdle()
            viewModel.onInputChanged(
                resultA.server.logicalBaseUrl,
            )
            viewModel.testConnection()
            advanceUntilIdle()
            viewModel.save()
            runCurrent()

            viewModel.onInputChanged(
                resultB.server.logicalBaseUrl,
            )
            viewModel.testConnection()
            runCurrent()
            assertEquals(
                resultB.endpoint.ipv4,
                viewModel.uiState.value.selectedIpv4,
            )
            assertTrue(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.canSave)

            saveGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(resultA, session.savedResult)
            assertEquals(
                resultB.server.logicalBaseUrl,
                viewModel.uiState.value.input,
            )
            assertEquals(
                resultB.endpoint.ipv4,
                viewModel.uiState.value.selectedIpv4,
            )
            assertTrue(viewModel.uiState.value.canSave)
            assertTrue(
                viewModel.uiState.value.hasUnsavedServerChange,
            )
        }

    @Test
    fun `failed A save preserves a successful B probe and enables B afterward`() =
        runTest(dispatcher) {
            val resultA = successfulResult()
            val resultB = successfulResult(
                logicalBaseUrl =
                    "http://other.example:8080",
                host = "other.example",
                selectedIpv4 = "203.0.113.9",
            )
            val saveGate = CompletableDeferred<Unit>()
            val session = SettingsFakeSession(
                saveResults = ArrayDeque(
                    listOf(
                        Result.failure<Unit>(
                            IOException("disk full"),
                        ),
                        Result.success(Unit),
                    ),
                ),
                saveGate = saveGate,
            ) { input ->
                when (input) {
                    resultA.server.logicalBaseUrl ->
                        AppResult.Success(resultA)

                    resultB.server.logicalBaseUrl ->
                        AppResult.Success(resultB)

                    else -> error("unexpected input: $input")
                }
            }
            val viewModel = SettingsViewModel(
                SettingsFakeRepository(ServerConfig()),
                SettingsFakeReaderPreferences(),
                session,
            )
            advanceUntilIdle()
            viewModel.onInputChanged(
                resultA.server.logicalBaseUrl,
            )
            viewModel.testConnection()
            advanceUntilIdle()
            viewModel.save()
            runCurrent()

            viewModel.onInputChanged(
                resultB.server.logicalBaseUrl,
            )
            viewModel.testConnection()
            runCurrent()
            assertTrue(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.canSave)
            assertEquals(
                resultB.endpoint.ipv4,
                viewModel.uiState.value.selectedIpv4,
            )

            saveGate.complete(Unit)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isSaving)
            assertEquals(
                resultB.server.logicalBaseUrl,
                viewModel.uiState.value.input,
            )
            assertEquals(
                resultB.endpoint.ipv4,
                viewModel.uiState.value.selectedIpv4,
            )
            assertTrue(viewModel.uiState.value.canSave)
            assertEquals(
                "保存失败，请重试",
                viewModel.uiState.value.saveError,
            )

            viewModel.save()
            advanceUntilIdle()
            assertEquals(
                listOf(resultA, resultB),
                session.saveAttempts,
            )
            assertEquals(resultB, session.savedResult)
        }

    @Test
    fun `failed A save does not restore its tested result after input changes to B`() =
        runTest(dispatcher) {
            val resultA = successfulResult()
            val inputB = "http://other.example:8080"
            val saveGate = CompletableDeferred<Unit>()
            val session = SettingsFakeSession(
                saveResults = ArrayDeque(
                    listOf(
                        Result.failure<Unit>(
                            IOException("disk full"),
                        ),
                    ),
                ),
                saveGate = saveGate,
            ) {
                AppResult.Success(resultA)
            }
            val viewModel = SettingsViewModel(
                SettingsFakeRepository(ServerConfig()),
                SettingsFakeReaderPreferences(),
                session,
            )
            advanceUntilIdle()
            viewModel.onInputChanged(
                resultA.server.logicalBaseUrl,
            )
            viewModel.testConnection()
            advanceUntilIdle()
            viewModel.save()
            runCurrent()

            viewModel.onInputChanged(inputB)
            saveGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(inputB, viewModel.uiState.value.input)
            assertFalse(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.uiState.value.canSave)
            assertNull(viewModel.uiState.value.selectedIpv4)
            assertEquals(
                "保存失败，请重试",
                viewModel.uiState.value.saveError,
            )
            assertFalse(
                viewModel.uiState.value.hasUnsavedServerChange,
            )
        }

    @Test
    fun `only a validated unsaved server change asks for discard confirmation`() =
        runTest(dispatcher) {
            val savedInput = "http://saved.example:8080"
            val viewModel = SettingsViewModel(
                SettingsFakeRepository(
                    ServerConfig(
                        logicalBaseUrl = savedInput,
                    ),
                ),
                SettingsFakeReaderPreferences(),
                SettingsFakeSession {
                    AppResult.Success(
                        successfulResult(
                            logicalBaseUrl = "http://other.example:8080",
                            host = "other.example",
                        ),
                    )
                },
            )
            advanceUntilIdle()

            assertFalse(
                viewModel.uiState.value.hasUnsavedServerChange,
            )
            assertEquals(
                SettingsBackDecision.LEAVE,
                viewModel.requestBack(),
            )

            viewModel.onDefaultImageModeChanged(
                ImageReaderMode.SINGLE,
            )
            advanceUntilIdle()
            assertEquals(
                SettingsBackDecision.LEAVE,
                viewModel.requestBack(),
            )

            // 普通未验证输入不拦截返回（规格 §8.3/§10）。
            viewModel.onInputChanged(
                "http://other.example:8080",
            )
            assertFalse(
                viewModel.uiState.value.hasUnsavedServerChange,
            )
            assertEquals(
                SettingsBackDecision.LEAVE,
                viewModel.requestBack(),
            )

            // 已验证但未保存才需要放弃确认。
            viewModel.testConnection()
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.hasUnsavedServerChange,
            )
            assertEquals(
                SettingsBackDecision.CONFIRM_DISCARD,
                viewModel.requestBack(),
            )

            viewModel.onInputChanged(savedInput)
            assertFalse(
                viewModel.uiState.value.hasUnsavedServerChange,
            )
            assertEquals(
                SettingsBackDecision.LEAVE,
                viewModel.requestBack(),
            )
        }

    @Test
    fun `successful normalization recomputes dirty against the saved server`() =
        runTest(dispatcher) {
            val successful = successfulResult()
            val viewModel = SettingsViewModel(
                SettingsFakeRepository(
                    ServerConfig(
                        logicalBaseUrl =
                            successful.server.logicalBaseUrl,
                    ),
                ),
                SettingsFakeReaderPreferences(),
                SettingsFakeSession {
                    AppResult.Success(successful)
                },
            )
            advanceUntilIdle()

            viewModel.onInputChanged(
                "HTTP://MEDIA.EXAMPLE:8080/",
            )
            assertFalse(
                viewModel.uiState.value.hasUnsavedServerChange,
            )
            viewModel.testConnection()
            advanceUntilIdle()

            assertEquals(
                successful.server.logicalBaseUrl,
                viewModel.uiState.value.input,
            )
            assertFalse(
                viewModel.uiState.value.hasUnsavedServerChange,
            )
            assertTrue(viewModel.uiState.value.canSave)
        }

    @Test
    fun `late initial load establishes the baseline without overwriting a newer input`() =
        runTest(dispatcher) {
            val savedInput = "http://saved.example:8080"
            val editedInput = "http://edited.example:8080"
            val currentGate = CompletableDeferred<Unit>()
            val viewModel = SettingsViewModel(
                SettingsFakeRepository(
                    initial = ServerConfig(
                        logicalBaseUrl = savedInput,
                    ),
                    currentGate = currentGate,
                ),
                SettingsFakeReaderPreferences(),
                SettingsFakeSession {
                    error("initialization must not probe")
                },
            )
            runCurrent()

            viewModel.onInputChanged(editedInput)
            currentGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                editedInput,
                viewModel.uiState.value.input,
            )
            assertFalse(
                viewModel.uiState.value.hasUnsavedServerChange,
            )
            assertEquals(
                SettingsBackDecision.LEAVE,
                viewModel.requestBack(),
            )
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

    @Test
    fun `自动隐藏偏好加载后立即独立保存`() =
        runTest(dispatcher) {
            val playerPreferences = SettingsFakePlayerPreferences(
                initial = VideoControlsAutoHide.FIVE_SECONDS,
            )
            val viewModel = SettingsViewModel(
                settings = SettingsFakeRepository(ServerConfig()),
                readerPreferences = SettingsFakeReaderPreferences(),
                session = SettingsFakeSession {
                    error("自动隐藏偏好不应探测服务器")
                },
                playerPreferences = playerPreferences,
            )
            advanceUntilIdle()

            assertEquals(
                VideoControlsAutoHide.FIVE_SECONDS,
                viewModel.uiState.value.videoControlsAutoHide,
            )

            viewModel.onVideoControlsAutoHideChanged(
                VideoControlsAutoHide.NEVER,
            )
            advanceUntilIdle()

            assertEquals(
                VideoControlsAutoHide.NEVER,
                playerPreferences.videoControlsAutoHide.value,
            )
            assertEquals(
                VideoControlsAutoHide.NEVER,
                viewModel.uiState.value.videoControlsAutoHide,
            )
            assertEquals(1, playerPreferences.saveCalls)
            assertFalse(
                viewModel.uiState.value.isSavingVideoControlsAutoHide,
            )
            assertNull(
                viewModel.uiState.value.videoControlsAutoHideError,
            )
        }

    @Test
    fun `自动隐藏偏好保存失败回滚并显示错误`() =
        runTest(dispatcher) {
            val playerPreferences = SettingsFakePlayerPreferences(
                initial = VideoControlsAutoHide.FIVE_SECONDS,
                failOnSave = true,
            )
            val viewModel = SettingsViewModel(
                settings = SettingsFakeRepository(ServerConfig()),
                readerPreferences = SettingsFakeReaderPreferences(),
                session = SettingsFakeSession {
                    error("自动隐藏偏好不应探测服务器")
                },
                playerPreferences = playerPreferences,
            )
            advanceUntilIdle()

            viewModel.onVideoControlsAutoHideChanged(
                VideoControlsAutoHide.TEN_SECONDS,
            )
            advanceUntilIdle()

            assertEquals(
                VideoControlsAutoHide.FIVE_SECONDS,
                viewModel.uiState.value.videoControlsAutoHide,
            )
            assertFalse(
                viewModel.uiState.value.isSavingVideoControlsAutoHide,
            )
            assertEquals(
                "自动隐藏时长保存失败",
                viewModel.uiState.value.videoControlsAutoHideError,
            )
        }
}

private fun successfulResult(
    logicalBaseUrl: String = "http://media.example:8080",
    host: String = "media.example",
    selectedIpv4: String = "203.0.113.7",
) = ConnectionTestResult(
    server = ValidatedServerUrl(
        logicalBaseUrl,
        host,
        8080,
        false,
    ),
    resolvedIpv4s = listOf("10.0.0.8", selectedIpv4),
    endpoint = SessionEndpoint(
        logicalBaseUrl,
        "http://$selectedIpv4:8080",
        selectedIpv4,
    ),
)

private class SettingsFakeRepository(
    initial: ServerConfig,
    private val currentGate: CompletableDeferred<Unit>? = null,
) :
    ServerSettingsRepository {
    private val mutable = MutableStateFlow(initial)
    override val config: Flow<ServerConfig> = mutable

    override suspend fun current(): ServerConfig {
        currentGate?.await()
        return mutable.value
    }

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

private class SettingsFakePlayerPreferences(
    initial: VideoControlsAutoHide =
        VideoControlsAutoHide.THREE_SECONDS,
    private val failOnSave: Boolean = false,
) : PlayerPreferencesRepository {
    private val gesturesShown = MutableStateFlow(false)
    override val hasShownVideoGestures: Flow<Boolean> = gesturesShown
    override val videoControlsAutoHide = MutableStateFlow(initial)
    var saveCalls = 0
        private set

    override suspend fun markVideoGesturesShown() {
        gesturesShown.value = true
    }

    override suspend fun setVideoControlsAutoHide(
        value: VideoControlsAutoHide,
    ) {
        saveCalls += 1
        if (failOnSave) {
            error("save failed")
        }
        videoControlsAutoHide.value = value
    }
}

private fun interface CandidateTestBlock {
    suspend fun invoke(input: String): AppResult<ConnectionTestResult>
}

private class SettingsFakeSession(
    private val saveResults: ArrayDeque<Result<Unit>> =
        ArrayDeque(),
    private val saveGate: CompletableDeferred<Unit>? = null,
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
    val saveAttempts = mutableListOf<ConnectionTestResult>()

    override suspend fun connectSaved() = Unit

    override suspend fun testCandidate(
        input: String,
    ): AppResult<ConnectionTestResult> {
        testCalls += 1
        return testBlock.invoke(input)
    }

    override suspend fun saveCandidate(result: ConnectionTestResult) {
        saveCalls += 1
        saveAttempts += result
        saveGate?.await()
        if (saveResults.isNotEmpty()) {
            saveResults.removeFirst().getOrThrow()
        }
        savedResult = result
    }

    override suspend fun refreshAfterRequestFailure(): AppResult<SessionEndpoint> =
        AppResult.Failure(AppError.NetworkFailure("not used"))
}
