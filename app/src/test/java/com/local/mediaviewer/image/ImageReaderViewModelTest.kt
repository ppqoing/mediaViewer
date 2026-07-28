package com.local.mediaviewer.image

import com.local.mediaviewer.browser.DirectoryContent
import com.local.mediaviewer.browser.DirectoryContentRepository
import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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

    private fun readerViewModel(
        repository: DirectoryContentRepository,
        selectedLogicalUrl: String =
            "${DIRECTORY_URL}a.jpg",
        preferences: ReaderPreferencesRepository =
            ReaderPreferencesFake(
                ImageReaderMode.COMIC,
            ),
    ) = ImageReaderViewModel(
        directoryLogicalUrl = DIRECTORY_URL,
        selectedLogicalUrl = selectedLogicalUrl,
        contentRepository = repository,
        preferences = preferences,
    )
}

private const val DIRECTORY_URL =
    "http://media.example:8080/pik/chapter/"
private const val REQUEST_DIRECTORY_URL =
    "http://192.0.2.1:8080/pik/chapter/"

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
