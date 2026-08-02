package com.local.mediaviewer.browser

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.ServerShare
import com.local.mediaviewer.model.ShareAuthenticationMode
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setMain() = Dispatchers.setMain(dispatcher)

    @After
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun `点击可播放文件按目录顺序发出目录播放请求`() = runTest(dispatcher) {
        val rootUrl = "http://media.example:8080/middle/"
        val subUrl = "${rootUrl}sub/"
        val directory = entry(
            name = "nested",
            logicalUrl = "${subUrl}nested/",
            requestUrl = "",
            kind = MediaKind.DIRECTORY,
        )
        val image = entry(
            name = "page.png",
            logicalUrl = "${subUrl}page.png",
            requestUrl = "http://192.0.2.1:8080/middle/sub/page.png",
            kind = MediaKind.IMAGE,
        )
        val pdf = entry(
            name = "manual.pdf",
            logicalUrl = "${subUrl}manual.pdf",
            requestUrl = "http://192.0.2.1:8080/middle/sub/manual.pdf",
            kind = MediaKind.PDF,
        )
        val video = entry(
            name = "movie.mp4",
            logicalUrl = "${subUrl}movie.mp4",
            requestUrl = "http://192.0.2.1:8080/middle/sub/movie.mp4",
            kind = MediaKind.VIDEO,
        )
        val audio = entry(
            name = "song.mp3",
            logicalUrl = "${subUrl}song.mp3",
            requestUrl = "http://192.0.2.1:8080/middle/sub/song.mp3",
            kind = MediaKind.AUDIO,
        )
        val unknown = entry(
            name = "stream.bin",
            logicalUrl = "${subUrl}stream.bin",
            requestUrl = "http://192.0.2.1:8080/middle/sub/stream.bin",
            kind = MediaKind.UNKNOWN,
        )
        val rootPage = page(
            rootUrl,
            listOf(entry("sub", subUrl, "", MediaKind.DIRECTORY)),
        )
        val subPage = page(
            subUrl,
            listOf(directory, image, pdf, video, audio, unknown),
            listOf(
                Breadcrumb("MiddleDir", rootUrl),
                Breadcrumb("sub", subUrl),
            ),
        )
        val pages = ArrayDeque(listOf(rootPage, subPage, subPage))
        val viewModel = BrowserViewModel(
            root = MIDDLE_SHARE,
            repository = QueueBrowserRepository(pages),
        )

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is BrowserUiState.Content)

        viewModel.open(currentPage(viewModel).entries.single())
        advanceUntilIdle()
        val requestDeferred = async { viewModel.playbackRequests.first() }
        runCurrent()
        viewModel.open(video)
        val request = requestDeferred.await()
        assertEquals(BrowserPlaybackAction.PLAY_DIRECTORY, request.action)
        assertEquals(video.logicalUrl, request.selected.mediaKey)
        assertEquals(
            listOf(video.logicalUrl, audio.logicalUrl, unknown.logicalUrl),
            request.directoryItems.map { it.mediaKey },
        )

        val imageDeferred =
            async { viewModel.mediaLaunches.first() }
        runCurrent()
        viewModel.open(image)
        val imageLaunch = imageDeferred.await()
        assertEquals(image.logicalUrl, imageLaunch.mediaKey)
        assertEquals(
            subUrl,
            imageLaunch.directoryLogicalUrl,
        )

        var playbackRequestCount = 0
        val playbackJob = backgroundScope.launch {
            viewModel.playbackRequests.collect {
                playbackRequestCount += 1
            }
        }
        val mediaLaunchDeferred = async { viewModel.mediaLaunches.first() }
        runCurrent()
        viewModel.open(pdf)
        val launch = mediaLaunchDeferred.await()
        assertEquals(MediaKind.PDF, launch.kind)
        assertEquals(pdf.logicalUrl, launch.logicalUrl)
        assertEquals(subUrl, launch.directoryLogicalUrl)
        assertEquals(0, playbackRequestCount)
        playbackJob.cancel()

        viewModel.openBreadcrumb(0)
        assertEquals(rootUrl, currentPage(viewModel).logicalDirectoryUrl)
        assertFalse(viewModel.goBack())

        viewModel.open(currentPage(viewModel).entries.single())
        advanceUntilIdle()
        assertEquals(subUrl, currentPage(viewModel).logicalDirectoryUrl)
        assertTrue(viewModel.goBack())
        assertEquals(rootUrl, currentPage(viewModel).logicalDirectoryUrl)
        assertFalse(viewModel.goBack())
    }

    @Test
    fun `加载失败可重试为空目录`() = runTest(dispatcher) {
        val emptyPage = page(
            "http://media.example:8080/pik/",
            emptyList(),
            listOf(
                Breadcrumb(
                    "pik",
                    "http://media.example:8080/pik/",
                ),
            ),
            root = PIK_SHARE,
        )
        val repository = ResultQueueBrowserRepository(
            ArrayDeque(
                listOf(
                    AppResult.Failure(AppError.HttpFailure(503)),
                    AppResult.Success(emptyPage),
                ),
            ),
        )
        val viewModel = BrowserViewModel(PIK_SHARE, repository)

        advanceUntilIdle()
        val error = viewModel.uiState.value as BrowserUiState.Error
        assertEquals(AppError.HttpFailure(503), error.error)

        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is BrowserUiState.Empty)
        assertEquals(emptyPage, currentPage(viewModel))
    }

    @Test
    fun `只有子目录是内容而完全空目录才为空`() = runTest(dispatcher) {
        val rootUrl = "http://media.example:8080/middle/"
        val foldersPage = page(
            rootUrl,
            listOf(
                entry(
                    name = "child",
                    logicalUrl = "${rootUrl}child/",
                    requestUrl = "http://192.0.2.1:8080/middle/child/",
                    kind = MediaKind.DIRECTORY,
                ),
            ),
        )
        val folderBrowser = BrowserViewModel(
            MIDDLE_SHARE,
            QueueBrowserRepository(ArrayDeque(listOf(foldersPage))),
        )
        advanceUntilIdle()
        assertTrue(folderBrowser.uiState.value is BrowserUiState.Content)

        val emptyPage = page(rootUrl, emptyList())
        val emptyBrowser = BrowserViewModel(
            MIDDLE_SHARE,
            QueueBrowserRepository(ArrayDeque(listOf(emptyPage))),
        )
        advanceUntilIdle()
        assertTrue(emptyBrowser.uiState.value is BrowserUiState.Empty)
    }

    @Test
    fun `进入空子目录后保留目标路径面包屑且返回父目录`() =
        runTest(dispatcher) {
            val rootUrl = "http://media.example:8080/middle/"
            val childUrl = "${rootUrl}empty/"
            val childEntry = entry(
                name = "empty",
                logicalUrl = childUrl,
                requestUrl =
                    "http://192.0.2.1:8080/middle/empty/",
                kind = MediaKind.DIRECTORY,
            )
            val rootPage = page(rootUrl, listOf(childEntry))
            val childPage = page(
                logicalUrl = childUrl,
                entries = emptyList(),
                breadcrumbs = listOf(
                    Breadcrumb("MiddleDir", rootUrl),
                    Breadcrumb("empty", childUrl),
                ),
            )
            val viewModel = BrowserViewModel(
                MIDDLE_SHARE,
                QueueBrowserRepository(
                    ArrayDeque(listOf(rootPage, childPage)),
                ),
            )

            advanceUntilIdle()
            viewModel.open(childEntry)
            advanceUntilIdle()

            val empty =
                viewModel.uiState.value as BrowserUiState.Empty
            assertEquals(
                childUrl,
                empty.page.logicalDirectoryUrl,
            )
            assertEquals(
                listOf("MiddleDir", "empty"),
                empty.page.breadcrumbs.map(Breadcrumb::label),
            )
            assertTrue(viewModel.goBack())
            assertEquals(
                rootUrl,
                currentPage(viewModel).logicalDirectoryUrl,
            )
        }

    @Test
    fun `failed child keeps the parent and back consumes the failed attempt`() =
        runTest(dispatcher) {
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

            val error =
                viewModel.uiState.value as BrowserUiState.Error
            assertEquals(
                rootPage,
                retainedPageOrNull(error),
            )
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

        assertEquals(listOf(childUrl, childUrl), repository.openedLogicalUrls)
        assertEquals(childUrl, currentPage(viewModel).logicalDirectoryUrl)
        assertTrue(viewModel.goBack())
        assertEquals(rootUrl, currentPage(viewModel).logicalDirectoryUrl)
        assertFalse(viewModel.goBack())
    }

    @Test
    fun `加载更深目录时保留当前页且返回取消待处理尝试`() = runTest(dispatcher) {
        val rootUrl = "http://media.example:8080/middle/"
        val subUrl = "${rootUrl}sub/"
        val deepUrl = "${subUrl}deep/"
        val rootPage = page(
            rootUrl,
            listOf(entry("sub", subUrl, "", MediaKind.DIRECTORY)),
        )
        val subPage = page(
            subUrl,
            listOf(entry("deep", deepUrl, "", MediaKind.DIRECTORY)),
            listOf(
                Breadcrumb("MiddleDir", rootUrl),
                Breadcrumb("sub", subUrl),
            ),
        )
        val deepPage = page(
            deepUrl,
            emptyList(),
            listOf(
                Breadcrumb("MiddleDir", rootUrl),
                Breadcrumb("sub", subUrl),
                Breadcrumb("deep", deepUrl),
            ),
        )
        val deepResult = CompletableDeferred<BrowserPage>()
        val viewModel = BrowserViewModel(
            MIDDLE_SHARE,
            ControlledBrowserRepository(rootPage, subPage, deepUrl, deepResult),
        )
        advanceUntilIdle()
        viewModel.open(currentPage(viewModel).entries.single())
        advanceUntilIdle()
        assertEquals(subUrl, currentPage(viewModel).logicalDirectoryUrl)

        viewModel.open(currentPage(viewModel).entries.single())
        runCurrent()
        val loading =
            viewModel.uiState.value as BrowserUiState.Loading
        assertEquals(
            subPage,
            retainedPageOrNull(loading),
        )
        assertTrue(viewModel.goBack())
        assertEquals(subUrl, currentPage(viewModel).logicalDirectoryUrl)

        deepResult.complete(deepPage)
        advanceUntilIdle()

        assertEquals(subUrl, currentPage(viewModel).logicalDirectoryUrl)
        assertTrue(viewModel.goBack())
        assertEquals(rootUrl, currentPage(viewModel).logicalDirectoryUrl)
        assertFalse(viewModel.goBack())
    }
}

private class QueueBrowserRepository(
    private val pages: ArrayDeque<BrowserPage>,
) : BrowserRepository {
    override suspend fun openRoot(root: ServerShare): AppResult<BrowserPage> =
        AppResult.Success(pages.removeFirst())

    override suspend fun openDirectory(
        root: ServerShare,
        logicalUrl: String,
        breadcrumbs: List<Breadcrumb>,
    ): AppResult<BrowserPage> =
        AppResult.Success(pages.removeFirst())
}

private class ResultQueueBrowserRepository(
    private val results: ArrayDeque<AppResult<BrowserPage>>,
) : BrowserRepository {
    val openedLogicalUrls = mutableListOf<String>()

    override suspend fun openRoot(root: ServerShare): AppResult<BrowserPage> =
        results.removeFirst()

    override suspend fun openDirectory(
        root: ServerShare,
        logicalUrl: String,
        breadcrumbs: List<Breadcrumb>,
    ): AppResult<BrowserPage> {
        openedLogicalUrls += logicalUrl
        return results.removeFirst()
    }
}

private class ControlledBrowserRepository(
    private val rootPage: BrowserPage,
    private val subPage: BrowserPage,
    private val deepUrl: String,
    private val deepResult: CompletableDeferred<BrowserPage>,
) : BrowserRepository {
    override suspend fun openRoot(root: ServerShare): AppResult<BrowserPage> =
        AppResult.Success(rootPage)

    override suspend fun openDirectory(
        root: ServerShare,
        logicalUrl: String,
        breadcrumbs: List<Breadcrumb>,
    ): AppResult<BrowserPage> =
        if (logicalUrl == deepUrl) {
            AppResult.Success(deepResult.await())
        } else {
            AppResult.Success(subPage)
        }
}

private fun page(
    logicalUrl: String,
    entries: List<DirectoryEntry>,
    breadcrumbs: List<Breadcrumb> =
        listOf(Breadcrumb("MiddleDir", logicalUrl)),
    root: ServerShare = MIDDLE_SHARE,
) = BrowserPage(
    root = root,
    logicalDirectoryUrl = logicalUrl,
    requestDirectoryUrl = logicalUrl.replace("media.example", "192.0.2.1"),
    breadcrumbs = breadcrumbs,
    entries = entries,
)

private fun entry(
    name: String,
    logicalUrl: String,
    requestUrl: String,
    kind: MediaKind,
) = DirectoryEntry(
    name = name,
    size = 1,
    modifiedAt = Instant.EPOCH,
    mode = 420,
    isDirectory = kind == MediaKind.DIRECTORY,
    isSymlink = false,
    logicalUrl = logicalUrl,
    requestUrl = requestUrl,
    kind = kind,
)

private fun currentPage(viewModel: BrowserViewModel): BrowserPage =
    when (val state = viewModel.uiState.value) {
        is BrowserUiState.Content -> state.page
        is BrowserUiState.Empty -> state.page
        else -> error("No page: $state")
    }

private fun retainedPageOrNull(state: BrowserUiState): BrowserPage? =
    when (state) {
        is BrowserUiState.Content -> state.page
        is BrowserUiState.Empty -> state.page
        is BrowserUiState.Loading -> state.previous
        is BrowserUiState.Error -> state.previous
    }

private val MIDDLE_SHARE = ServerShare(
    id = "4f01061d-9b75-4f7d-96db-49c801e96188",
    displayName = "MiddleDir",
    urlPrefix = "middle",
    directoryBrowsing = true,
    authenticationMode = ShareAuthenticationMode.ANONYMOUS,
)

private val PIK_SHARE = ServerShare(
    id = "0447a975-eccb-4802-a8f5-5f574971876c",
    displayName = "pik",
    urlPrefix = "pik",
    directoryBrowsing = true,
    authenticationMode = ShareAuthenticationMode.ANONYMOUS,
)
