package com.local.mediaviewer.image

import com.local.mediaviewer.browser.DirectoryContent
import com.local.mediaviewer.browser.DirectoryContentRepository
import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.network.ConnectionTestResult
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import java.time.Instant
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
class ImageReaderViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun before() = Dispatchers.setMain(dispatcher)

    @After
    fun after() = Dispatchers.resetMain()

    @Test
    fun `加载当前目录图片并定位点击项`() =
        runTest(dispatcher) {
            val repository = ReaderDirectoryRepository(
                successContent(
                    listOf(
                        readerEntry(
                            "a.jpg",
                            MediaKind.IMAGE,
                        ),
                        readerEntry(
                            "movie.mp4",
                            MediaKind.VIDEO,
                        ),
                        readerEntry(
                            "b.png",
                            MediaKind.IMAGE,
                        ),
                    ),
                ),
            )
            val viewModel = readerViewModel(
                repository = repository,
                selectedLogicalUrl =
                    "${DIRECTORY_URL}b.png",
            )

            assertTrue(
                viewModel.uiState.value ===
                    ImageReaderUiState.Loading,
            )
            advanceUntilIdle()

            val content =
                viewModel.uiState.value
                    as ImageReaderUiState.Content
            assertEquals(
                listOf("a.jpg", "b.png"),
                content.images.map(ImageReaderItem::name),
            )
            assertEquals(
                "${DIRECTORY_URL}b.png",
                content.anchorLogicalUrl,
            )
            assertEquals(
                ImageReaderMode.COMIC,
                content.mode,
            )
            assertEquals(
                ImageSortOrder.NAME_ASC,
                content.sortOrder,
            )
            assertEquals(
                listOf(DIRECTORY_URL),
                repository.loadedUrls,
            )
        }

    @Test
    fun `默认设置为单图时初始单图`() =
        runTest(dispatcher) {
            val viewModel = readerViewModel(
                repository = ReaderDirectoryRepository(
                    successContent(
                        listOf(
                            readerEntry(
                                "a.jpg",
                                MediaKind.IMAGE,
                            ),
                        ),
                    ),
                ),
                preferences = ReaderPreferencesFake(
                    ImageReaderMode.SINGLE,
                ),
            )

            advanceUntilIdle()

            assertEquals(
                ImageReaderMode.SINGLE,
                (
                    viewModel.uiState.value
                        as ImageReaderUiState.Content
                ).mode,
            )
        }

    @Test
    fun `点击项消失时回退第一张`() =
        runTest(dispatcher) {
            val viewModel = readerViewModel(
                repository = ReaderDirectoryRepository(
                    successContent(
                        listOf(
                            readerEntry(
                                "b.jpg",
                                MediaKind.IMAGE,
                            ),
                            readerEntry(
                                "a.jpg",
                                MediaKind.IMAGE,
                            ),
                        ),
                    ),
                ),
                selectedLogicalUrl =
                    "${DIRECTORY_URL}missing.jpg",
            )

            advanceUntilIdle()

            assertEquals(
                "${DIRECTORY_URL}a.jpg",
                (
                    viewModel.uiState.value
                        as ImageReaderUiState.Content
                ).anchorLogicalUrl,
            )
        }

    @Test
    fun `没有图片时进入空状态`() =
        runTest(dispatcher) {
            val viewModel = readerViewModel(
                repository = ReaderDirectoryRepository(
                    successContent(
                        listOf(
                            readerEntry(
                                "movie.mp4",
                                MediaKind.VIDEO,
                            ),
                        ),
                    ),
                ),
            )

            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value ===
                    ImageReaderUiState.Empty,
            )
        }

    @Test
    fun `目录错误显示中文错误且重试可替换状态`() =
        runTest(dispatcher) {
            val repository = ReaderDirectoryRepository(
                AppResult.Failure(
                    AppError.HttpFailure(503),
                ),
                successContent(
                    listOf(
                        readerEntry(
                            "a.jpg",
                            MediaKind.IMAGE,
                        ),
                    ),
                ),
            )
            val viewModel = readerViewModel(
                repository = repository,
            )
            advanceUntilIdle()

            assertEquals(
                ImageReaderUiState.Error(
                    "服务器返回 HTTP 503",
                ),
                viewModel.uiState.value,
            )

            viewModel.retryDirectory()
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value is
                    ImageReaderUiState.Content,
            )
            assertEquals(
                listOf(DIRECTORY_URL, DIRECTORY_URL),
                repository.loadedUrls,
            )
        }

    @Test
    fun `切换排序和模式保持当前锚点`() =
        runTest(dispatcher) {
            val logicalA = "${DIRECTORY_URL}a.jpg"
            val logicalB = "${DIRECTORY_URL}b.jpg"
            val viewModel = readerViewModel(
                repository = ReaderDirectoryRepository(
                    successContent(
                        listOf(
                            readerEntry(
                                name = "a.jpg",
                                kind = MediaKind.IMAGE,
                                size = 10L,
                            ),
                            readerEntry(
                                name = "b.jpg",
                                kind = MediaKind.IMAGE,
                                size = 20L,
                            ),
                        ),
                    ),
                ),
                selectedLogicalUrl = logicalA,
            )
            advanceUntilIdle()

            viewModel.updateAnchor(logicalB)
            viewModel.setSortOrder(
                ImageSortOrder.SIZE_DESC,
            )
            viewModel.setMode(ImageReaderMode.SINGLE)

            val content =
                viewModel.uiState.value
                    as ImageReaderUiState.Content
            assertEquals(logicalB, content.anchorLogicalUrl)
            assertEquals(
                ImageReaderMode.SINGLE,
                content.mode,
            )
            assertEquals(
                ImageSortOrder.SIZE_DESC,
                content.sortOrder,
            )
            assertEquals(
                listOf("b.jpg", "a.jpg"),
                content.images.map(ImageReaderItem::name),
            )
        }

    @Test
    fun `无效锚点更新被忽略`() = runTest(dispatcher) {
        val logicalA = "${DIRECTORY_URL}a.jpg"
        val viewModel = readerViewModel(
            repository = ReaderDirectoryRepository(
                successContent(
                    listOf(
                        readerEntry(
                            "a.jpg",
                            MediaKind.IMAGE,
                        ),
                    ),
                ),
            ),
            selectedLogicalUrl = logicalA,
        )
        advanceUntilIdle()

        viewModel.updateAnchor(
            "${DIRECTORY_URL}outside.jpg",
        )

        assertEquals(
            logicalA,
            (
                viewModel.uiState.value
                    as ImageReaderUiState.Content
            ).anchorLogicalUrl,
        )
    }

    @Test
    fun `并发网络失败只刷新一次并只重映射失败项`() =
        runTest(dispatcher) {
            val logicalA = "${DIRECTORY_URL}a.jpg"
            val logicalB = "${DIRECTORY_URL}b.jpg"
            val logicalC = "${DIRECTORY_URL}c.jpg"
            val refreshedEndpoint = SessionEndpoint(
                logicalBaseUrl =
                    "http://media.example:8080",
                requestBaseUrl =
                    "http://203.0.113.9:8080",
                ipv4 = "203.0.113.9",
            )
            val session = ControlledImageSession(
                AppResult.Success(refreshedEndpoint),
            )
            val viewModel = readerViewModel(
                repository = ReaderDirectoryRepository(
                    successContent(
                        listOf(
                            readerEntry("a.jpg", MediaKind.IMAGE),
                            readerEntry("b.jpg", MediaKind.IMAGE),
                            readerEntry("c.jpg", MediaKind.IMAGE),
                        ),
                    ),
                ),
                session = session,
            )
            advanceUntilIdle()
            val initial =
                viewModel.uiState.value
                    as ImageReaderUiState.Content

            viewModel.onImageLoadError(
                logicalA,
                ImageLoadFailureKind.NETWORK,
            )
            viewModel.onImageLoadError(
                logicalB,
                ImageLoadFailureKind.NETWORK,
            )
            advanceUntilIdle()

            val content =
                viewModel.uiState.value
                    as ImageReaderUiState.Content
            assertEquals(1, session.refreshCalls)
            assertTrue(
                content.images
                    .single { it.logicalUrl == logicalA }
                    .requestUrl
                    .startsWith(refreshedEndpoint.requestBaseUrl),
            )
            assertTrue(
                content.images
                    .single { it.logicalUrl == logicalB }
                    .requestUrl
                    .startsWith(refreshedEndpoint.requestBaseUrl),
            )
            assertEquals(
                initial.images
                    .single { it.logicalUrl == logicalC }
                    .requestUrl,
                content.images
                    .single { it.logicalUrl == logicalC }
                    .requestUrl,
            )
            assertEquals(
                initial.requestGeneration,
                content.requestGeneration,
            )
            assertEquals(
                initial.anchorLogicalUrl,
                content.anchorLogicalUrl,
            )
            assertEquals(
                mapOf(logicalA to 1, logicalB to 1),
                content.itemRequestGenerations,
            )
            assertTrue(content.itemFailures.isEmpty())
            assertEquals(
                false,
                content.isRefreshingEndpoint,
            )
        }

    @Test
    fun `解码失败只记录单项且不刷新`() =
        runTest(dispatcher) {
            val logicalA = "${DIRECTORY_URL}a.jpg"
            val session = ControlledImageSession(
                AppResult.Failure(
                    AppError.DnsFailure("unused"),
                ),
            )
            val viewModel = populatedViewModel(
                session = session,
            )
            advanceUntilIdle()

            viewModel.onImageLoadError(
                logicalA,
                ImageLoadFailureKind.DECODE,
            )
            advanceUntilIdle()

            val content =
                viewModel.uiState.value
                    as ImageReaderUiState.Content
            assertEquals(0, session.refreshCalls)
            assertEquals(
                ImageItemFailure(
                    message = "图片解码失败",
                    kind = ImageLoadFailureKind.DECODE,
                ),
                content.itemFailures[logicalA],
            )
            assertEquals(0, content.requestGeneration)
        }

    @Test
    fun `第二次网络失败不自动刷新`() =
        runTest(dispatcher) {
            val logicalA = "${DIRECTORY_URL}a.jpg"
            val session = ControlledImageSession(
                AppResult.Success(REFRESHED_ENDPOINT),
            )
            val viewModel = populatedViewModel(
                session = session,
            )
            advanceUntilIdle()

            viewModel.onImageLoadError(
                logicalA,
                ImageLoadFailureKind.NETWORK,
            )
            advanceUntilIdle()
            viewModel.onImageLoadError(
                logicalA,
                ImageLoadFailureKind.NETWORK,
            )
            advanceUntilIdle()

            val content =
                viewModel.uiState.value
                    as ImageReaderUiState.Content
            assertEquals(1, session.refreshCalls)
            assertEquals(
                ImageLoadFailureKind.NETWORK,
                content.itemFailures[logicalA]?.kind,
            )
            assertEquals(0, content.requestGeneration)
        }

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
            val beforeManual = viewModel.uiState.value as ImageReaderUiState.Content

            viewModel.retryImage(failedUrl)
            advanceUntilIdle()
            val afterManual = viewModel.uiState.value as ImageReaderUiState.Content

            assertEquals(2, session.refreshCalls)
            assertEquals(
                beforeManual.requestGeneration,
                afterManual.requestGeneration,
            )
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

            viewModel.onImageLoadError(
                failedUrl,
                ImageLoadFailureKind.NETWORK,
            )
            advanceUntilIdle()

            assertEquals(2, session.refreshCalls)
            assertEquals(
                ImageLoadFailureKind.NETWORK,
                (
                    viewModel.uiState.value
                        as ImageReaderUiState.Content
                ).itemFailures[failedUrl]?.kind,
            )
        }

    @Test
    fun `repeated manual taps share one refresh job`() = runTest(dispatcher) {
        val refresh = CompletableDeferred<AppResult<SessionEndpoint>>()
        val manualEndpoint = REFRESHED_ENDPOINT.copy(
            requestBaseUrl = "http://192.0.2.77:8080",
            ipv4 = "192.0.2.77",
        )
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
        val beforeManual =
            viewModel.uiState.value
                as ImageReaderUiState.Content

        viewModel.retryImage("${DIRECTORY_URL}a.jpg")
        viewModel.retryImage("${DIRECTORY_URL}a.jpg")
        runCurrent()

        assertEquals(2, session.refreshCalls)
        val refreshing =
            viewModel.uiState.value
                as ImageReaderUiState.Content
        assertTrue(refreshing.isRefreshingEndpoint)
        assertEquals(
            "${DIRECTORY_URL}a.jpg",
            refreshing.refreshingImageLogicalUrl,
        )
        refresh.complete(AppResult.Success(manualEndpoint))
        advanceUntilIdle()
        val afterManual =
            viewModel.uiState.value
                as ImageReaderUiState.Content
        assertFalse(afterManual.isRefreshingEndpoint)
        assertNull(afterManual.refreshingImageLogicalUrl)
        assertEquals(
            beforeManual.requestGeneration,
            afterManual.requestGeneration,
        )
        assertEquals(
            (
                beforeManual.itemRequestGenerations[
                    "${DIRECTORY_URL}a.jpg"
                ] ?: 0
            ) + 1,
            afterManual.itemRequestGenerations[
                "${DIRECTORY_URL}a.jpg"
            ],
        )
        assertTrue(
            afterManual.images.single().requestUrl
                .startsWith(manualEndpoint.requestBaseUrl),
        )
    }

    @Test
    fun `刷新抛出异常转为局部错误并允许再次重试`() =
        runTest(dispatcher) {
            val logicalA = "${DIRECTORY_URL}a.jpg"
            val session = QueuedImageSession(
                ArrayDeque(
                    listOf(
                        {
                            throw IllegalStateException(
                                "refresh crashed",
                            )
                        },
                        {
                            AppResult.Success(
                                REFRESHED_ENDPOINT,
                            )
                        },
                    ),
                ),
            )
            val viewModel = populatedViewModel(session)
            advanceUntilIdle()

            viewModel.onImageLoadError(
                logicalA,
                ImageLoadFailureKind.NETWORK,
            )
            advanceUntilIdle()

            val failed =
                viewModel.uiState.value
                    as ImageReaderUiState.Content
            assertFalse(failed.isRefreshingEndpoint)
            assertNull(failed.refreshingImageLogicalUrl)
            assertEquals(
                ImageItemFailure(
                    message =
                        "重新连接失败：refresh crashed",
                    kind =
                        ImageLoadFailureKind.NETWORK,
                ),
                failed.itemFailures[logicalA],
            )

            viewModel.retryImage(logicalA)
            advanceUntilIdle()

            val recovered =
                viewModel.uiState.value
                    as ImageReaderUiState.Content
            assertEquals(2, session.refreshCalls)
            assertTrue(
                logicalA !in recovered.itemFailures,
            )
            assertEquals(
                1,
                recovered.itemRequestGenerations[
                    logicalA
                ],
            )
        }

    @Test
    fun `人工重试只递增目标项代数`() =
        runTest(dispatcher) {
            val logicalA = "${DIRECTORY_URL}a.jpg"
            val logicalB = "${DIRECTORY_URL}b.jpg"
            val viewModel = populatedViewModel()
            advanceUntilIdle()
            viewModel.onImageLoadError(
                logicalA,
                ImageLoadFailureKind.DECODE,
            )
            viewModel.onImageLoadError(
                logicalB,
                ImageLoadFailureKind.DECODE,
            )

            viewModel.retryImage(logicalA)

            val content =
                viewModel.uiState.value
                    as ImageReaderUiState.Content
            assertEquals(
                mapOf(logicalA to 1),
                content.itemRequestGenerations,
            )
            assertTrue(
                logicalA !in content.itemFailures,
            )
            assertTrue(
                logicalB in content.itemFailures,
            )
            assertEquals(0, content.requestGeneration)
        }

    @Test
    fun `加载成功清除目标项失败`() =
        runTest(dispatcher) {
            val logicalA = "${DIRECTORY_URL}a.jpg"
            val logicalB = "${DIRECTORY_URL}b.jpg"
            val viewModel = populatedViewModel()
            advanceUntilIdle()
            viewModel.onImageLoadError(
                logicalA,
                ImageLoadFailureKind.DECODE,
            )
            viewModel.onImageLoadError(
                logicalB,
                ImageLoadFailureKind.DECODE,
            )

            viewModel.onImageLoadSuccess(logicalA)

            val content =
                viewModel.uiState.value
                    as ImageReaderUiState.Content
            assertTrue(
                logicalA !in content.itemFailures,
            )
            assertTrue(
                logicalB in content.itemFailures,
            )
        }

    @Test
    fun `刷新失败保留单项错误并结束刷新`() =
        runTest(dispatcher) {
            val logicalA = "${DIRECTORY_URL}a.jpg"
            val session = ControlledImageSession(
                AppResult.Failure(
                    AppError.DnsFailure("no host"),
                ),
            )
            val viewModel = populatedViewModel(
                session = session,
            )
            advanceUntilIdle()

            viewModel.onImageLoadError(
                logicalA,
                ImageLoadFailureKind.NETWORK,
            )
            advanceUntilIdle()

            val content =
                viewModel.uiState.value
                    as ImageReaderUiState.Content
            assertEquals(1, session.refreshCalls)
            assertEquals(
                ImageItemFailure(
                    message =
                        "DNS 解析失败：no host",
                    kind =
                        ImageLoadFailureKind.NETWORK,
                ),
                content.itemFailures[logicalA],
            )
            assertEquals(
                false,
                content.isRefreshingEndpoint,
            )
            assertEquals(0, content.requestGeneration)
        }

    private fun populatedViewModel(
        session: ServerSessionManager =
            ControlledImageSession(
                AppResult.Success(
                    REFRESHED_ENDPOINT,
                ),
            ),
    ): ImageReaderViewModel =
        readerViewModel(
            repository = ReaderDirectoryRepository(
                successContent(
                    listOf(
                        readerEntry(
                            "a.jpg",
                            MediaKind.IMAGE,
                        ),
                        readerEntry(
                            "b.jpg",
                            MediaKind.IMAGE,
                        ),
                    ),
                ),
            ),
            session = session,
        )

    private fun readerViewModel(
        repository: DirectoryContentRepository,
        selectedLogicalUrl: String =
            "${DIRECTORY_URL}a.jpg",
        preferences: ReaderPreferencesRepository =
            ReaderPreferencesFake(
                ImageReaderMode.COMIC,
            ),
        session: ServerSessionManager =
            ControlledImageSession(
                AppResult.Success(
                    REFRESHED_ENDPOINT,
                ),
            ),
    ) = ImageReaderViewModel(
        directoryLogicalUrl = DIRECTORY_URL,
        selectedLogicalUrl = selectedLogicalUrl,
        contentRepository = repository,
        preferences = preferences,
        session = session,
    )
}

private const val DIRECTORY_URL =
    "http://media.example:8080/pik/chapter/"
private const val REQUEST_DIRECTORY_URL =
    "http://192.0.2.1:8080/pik/chapter/"
private val REFRESHED_ENDPOINT = SessionEndpoint(
    logicalBaseUrl = "http://media.example:8080",
    requestBaseUrl = "http://203.0.113.9:8080",
    ipv4 = "203.0.113.9",
)

private fun successContent(
    entries: List<DirectoryEntry>,
): AppResult<DirectoryContent> =
    AppResult.Success(
        DirectoryContent(
            logicalDirectoryUrl = DIRECTORY_URL,
            requestDirectoryUrl = REQUEST_DIRECTORY_URL,
            entries = entries,
        ),
    )

private fun readerEntry(
    name: String,
    kind: MediaKind,
    size: Long = 1L,
) = DirectoryEntry(
    name = name,
    size = size,
    modifiedAt =
        Instant.parse("2026-07-28T00:00:00Z"),
    mode = 420L,
    isDirectory = kind == MediaKind.DIRECTORY,
    isSymlink = false,
    logicalUrl = "$DIRECTORY_URL$name",
    requestUrl = "$REQUEST_DIRECTORY_URL$name",
    kind = kind,
)

private class ReaderDirectoryRepository(
    vararg results: AppResult<DirectoryContent>,
) : DirectoryContentRepository {
    private val queued = ArrayDeque(results.toList())
    val loadedUrls = mutableListOf<String>()

    override suspend fun load(
        logicalDirectoryUrl: String,
    ): AppResult<DirectoryContent> {
        loadedUrls += logicalDirectoryUrl
        return queued.removeFirst()
    }
}

private class ReaderPreferencesFake(
    initial: ImageReaderMode,
) : ReaderPreferencesRepository {
    private val mutable = MutableStateFlow(initial)
    override val defaultMode: Flow<ImageReaderMode> = mutable

    override suspend fun currentDefaultMode(): ImageReaderMode =
        mutable.value

    override suspend fun setDefaultMode(mode: ImageReaderMode) {
        mutable.value = mode
    }
}

private class ControlledImageSession(
    private val refreshResult:
        AppResult<SessionEndpoint>,
) : ServerSessionManager {
    private val mutableState =
        MutableStateFlow<ServerSessionState>(
            ServerSessionState.Connected(
                endpoint = REFRESHED_ENDPOINT,
                resolvedIpv4s =
                    listOf(REFRESHED_ENDPOINT.ipv4),
            ),
        )
    override val state = mutableState
    var refreshCalls = 0
        private set

    override suspend fun connectSaved() = Unit

    override suspend fun testCandidate(
        input: String,
    ): AppResult<ConnectionTestResult> =
        error("测试不会探测服务器：$input")

    override suspend fun saveCandidate(
        result: ConnectionTestResult,
    ) {
        error(
            "测试不会保存服务器：" +
                result.server.logicalBaseUrl,
        )
    }

    override suspend fun refreshAfterRequestFailure():
        AppResult<SessionEndpoint> {
        refreshCalls += 1
        return refreshResult
    }
}

private class QueuedImageSession(
    private val results: ArrayDeque<suspend () -> AppResult<SessionEndpoint>>,
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
