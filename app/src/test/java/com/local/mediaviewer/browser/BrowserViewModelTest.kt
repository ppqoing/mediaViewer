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
            listOf(directory, image, video, audio, unknown),
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
    fun `返回上级会取消尚未完成的更深目录加载`() = runTest(dispatcher) {
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
        assertTrue(viewModel.uiState.value is BrowserUiState.Loading)
        assertTrue(viewModel.goBack())
        assertEquals(rootUrl, currentPage(viewModel).logicalDirectoryUrl)

        deepResult.complete(deepPage)
        advanceUntilIdle()

        assertEquals(rootUrl, currentPage(viewModel).logicalDirectoryUrl)
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
    override suspend fun openRoot(root: ServerShare): AppResult<BrowserPage> =
        results.removeFirst()

    override suspend fun openDirectory(
        root: ServerShare,
        logicalUrl: String,
        breadcrumbs: List<Breadcrumb>,
    ): AppResult<BrowserPage> =
        results.removeFirst()
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
