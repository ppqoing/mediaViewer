package com.local.mediaviewer.pdf

import android.graphics.Bitmap
import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.core.DispatcherProvider
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PdfReaderViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val io = dispatcher
        override val default = dispatcher
        override val main = dispatcher
    }

    @Before
    fun before() = Dispatchers.setMain(dispatcher)

    @After
    fun after() = Dispatchers.resetMain()

    @Test
    fun `downloads before opening and publishes page sizes`() =
        runTest(dispatcher) {
            val events = mutableListOf<String>()
            val openGate = CompletableDeferred<Unit>()
            val handle = FakePdfDocumentHandle(pageCount = 5)
            val files = FakePdfTemporaryFileRepository(events = events)
            val documents = FakePdfDocumentFactory(
                results = ArrayDeque(listOf(AppResult.Success(handle))),
                events = events,
                openGate = openGate,
            )
            val viewModel = viewModel(files, documents)

            assertEquals(
                PdfReaderUiState.Loading(FILE_NAME, PdfLoadPhase.DOWNLOADING),
                viewModel.uiState.value,
            )
            runCurrent()

            assertEquals(listOf("acquire", "open"), events)
            assertEquals(
                PdfReaderUiState.Loading(FILE_NAME, PdfLoadPhase.OPENING),
                viewModel.uiState.value,
            )

            openGate.complete(Unit)
            advanceUntilIdle()

            val content = viewModel.uiState.value as PdfReaderUiState.Content
            assertEquals(FILE_NAME, content.fileName)
            assertEquals(handle.pageSizes, content.pageSizes)
            assertEquals(5, content.pageSizes.size)
            assertTrue(content.pages.isEmpty())
            assertEquals(0, content.currentPageIndex)
            assertEquals(listOf(LOGICAL_URL), files.acquiredLogicalUrls)
        }

    @Test
    fun `visible pages prefetch one neighbor and keep zero based current page`() =
        runTest(dispatcher) {
            val handle = FakePdfDocumentHandle(pageCount = 8)
            val viewModel = loadedViewModel(handle)

            viewModel.updateViewport(
                currentPageIndex = 4,
                visiblePageIndices = setOf(4, 5),
                viewportWidthPx = 1080,
                renderScale = 1f,
            )
            advanceUntilIdle()

            assertEquals(setOf(3, 4, 5, 6), handle.renderedPages.toSet())
            assertEquals(
                4,
                (viewModel.uiState.value as PdfReaderUiState.Content)
                    .currentPageIndex,
            )
        }

    @Test
    fun `repeated viewport skips pages already rendered at requested width`() =
        runTest(dispatcher) {
            val handle = FakePdfDocumentHandle(pageCount = 5)
            val viewModel = loadedViewModel(handle)

            repeat(2) {
                viewModel.updateViewport(
                    currentPageIndex = 2,
                    visiblePageIndices = setOf(2),
                    viewportWidthPx = 900,
                    renderScale = 1f,
                )
                advanceUntilIdle()
            }

            assertEquals(3, handle.renderRequests.size)
            assertEquals(
                setOf(1 to 900, 2 to 900, 3 to 900),
                handle.renderRequests.toSet(),
            )
        }

    @Test
    fun `page failure is local and retry renders only that page`() =
        runTest(dispatcher) {
            val handle = FakePdfDocumentHandle(
                pageCount = 5,
                failures = mutableMapOf((2 to 1080) to 1),
            )
            val viewModel = loadedViewModel(handle)

            viewModel.updateViewport(
                currentPageIndex = 2,
                visiblePageIndices = setOf(2),
                viewportWidthPx = 1080,
                renderScale = 1f,
            )
            advanceUntilIdle()

            val failed = viewModel.uiState.value as PdfReaderUiState.Content
            assertEquals("第 3 页渲染失败", failed.pages.getValue(2).errorMessage)
            assertNull(failed.pages.getValue(2).bitmap)
            assertTrue(failed.pages.getValue(1).bitmap != null)
            assertTrue(failed.pages.getValue(3).bitmap != null)

            viewModel.retryPage(2)
            advanceUntilIdle()

            val recovered = viewModel.uiState.value as PdfReaderUiState.Content
            assertNull(recovered.pages.getValue(2).errorMessage)
            assertTrue(recovered.pages.getValue(2).bitmap != null)
            assertEquals(4, handle.renderedPages.size)
            assertEquals(setOf(1, 2, 3), handle.renderedPages.take(3).toSet())
            assertEquals(2, handle.renderedPages.last())
        }

    @Test
    fun `high resolution failure clears cache and falls back once to viewport width`() =
        runTest(dispatcher) {
            val handle = FakePdfDocumentHandle(
                pageCount = 7,
                failures = mutableMapOf((4 to 2160) to 1),
            )
            val viewModel = loadedViewModel(handle)
            viewModel.updateViewport(
                currentPageIndex = 0,
                visiblePageIndices = setOf(0),
                viewportWidthPx = 1080,
                renderScale = 1f,
            )
            advanceUntilIdle()
            val oldBitmap = handle.bitmaps.getValue(0 to 1080)

            viewModel.updateViewport(
                currentPageIndex = 4,
                visiblePageIndices = linkedSetOf(3, 5, 4),
                viewportWidthPx = 1080,
                renderScale = 3f,
            )
            advanceUntilIdle()

            assertEquals(
                listOf(2160, 1080),
                handle.renderRequests
                    .filter { it.first == 4 }
                    .map { it.second },
            )
            val content = viewModel.uiState.value as PdfReaderUiState.Content
            assertEquals(1080, content.pages.getValue(4).renderedWidthPx)
            assertNull(content.pages.getValue(4).errorMessage)
            assertTrue(oldBitmap.isRecycled)
            val nearby = handle.bitmaps.getValue(3 to 2160)
            assertSame(nearby, content.pages.getValue(3).bitmap)
            assertFalse(nearby.isRecycled)
        }

    @Test
    fun `cache eviction removes bitmap from state before recycling it`() =
        runTest(dispatcher) {
            val handle = FakePdfDocumentHandle(pageCount = 3)
            val viewModel = loadedViewModel(
                handle = handle,
                bitmapCacheBytes = 1080 * Int.SIZE_BYTES,
            )

            viewModel.updateViewport(
                currentPageIndex = 0,
                visiblePageIndices = setOf(0),
                viewportWidthPx = 1080,
                renderScale = 1f,
            )
            advanceUntilIdle()

            val first = handle.bitmaps.getValue(0 to 1080)
            val content = viewModel.uiState.value as PdfReaderUiState.Content
            assertNull(content.pages.getValue(0).bitmap)
            assertSame(
                handle.bitmaps.getValue(1 to 1080),
                content.pages.getValue(1).bitmap,
            )
            assertTrue(first.isRecycled)
        }

    @Test
    fun `document failure publishes Chinese error and retry replaces resources first`() =
        runTest(dispatcher) {
            val events = mutableListOf<String>()
            val first = FakePdfDocumentHandle(pageCount = 5, events = events)
            val second = FakePdfDocumentHandle(pageCount = 5, events = events)
            val files = FakePdfTemporaryFileRepository(events = events)
            val documents = FakePdfDocumentFactory(
                results = ArrayDeque(
                    listOf(
                        AppResult.Success(first),
                        AppResult.Success(second),
                    ),
                ),
                events = events,
            )
            val viewModel = viewModel(files, documents)
            advanceUntilIdle()
            events.clear()

            viewModel.retryDocument()
            advanceUntilIdle()

            assertEquals(listOf("close", "release", "acquire", "open"), events)
            assertEquals(1, first.closeCalls)
            assertEquals(2, files.acquiredLogicalUrls.size)

            val failing = viewModel(
                files = FakePdfTemporaryFileRepository(
                    acquireResults = ArrayDeque(
                        listOf(AppResult.Failure(AppError.PdfCacheSpaceInsufficient)),
                    ),
                ),
                documents = FakePdfDocumentFactory(),
            )
            advanceUntilIdle()

            assertEquals(
                PdfReaderUiState.Error(
                    FILE_NAME,
                    "缓存空间不足，无法打开 PDF",
                ),
                failing.uiState.value,
            )
        }

    @Test
    fun `close is idempotent and cancelled rendering cannot publish a bitmap`() =
        runTest(dispatcher) {
            val renderGate = CompletableDeferred<Unit>()
            val handle = FakePdfDocumentHandle(
                pageCount = 3,
                renderGate = renderGate,
            )
            val files = FakePdfTemporaryFileRepository()
            val viewModel = loadedViewModel(handle, files = files)

            viewModel.updateViewport(
                currentPageIndex = 0,
                visiblePageIndices = setOf(0),
                viewportWidthPx = 1080,
                renderScale = 1f,
            )
            runCurrent()

            viewModel.closeForTest()
            viewModel.closeForTest()
            renderGate.complete(Unit)
            advanceUntilIdle()

            val content = viewModel.uiState.value as PdfReaderUiState.Content
            assertTrue(content.pages.values.none { it.bitmap != null })
            assertEquals(1, handle.closeCalls)
            assertEquals(1, files.releasedFiles.size)
        }

    @Test
    fun `close recycles bitmap completed on IO after render job cancellation`() =
        runTest(dispatcher) {
            val ioScheduler = TestCoroutineScheduler()
            val renderGate = CompletableDeferred<Unit>()
            val handle = FakePdfDocumentHandle(
                pageCount = 3,
                renderGate = renderGate,
                renderDispatcher = StandardTestDispatcher(ioScheduler),
            )
            val viewModel = loadedViewModel(handle)
            viewModel.updateViewport(
                currentPageIndex = 0,
                visiblePageIndices = setOf(0),
                viewportWidthPx = 1080,
                renderScale = 1f,
            )
            runCurrent()
            ioScheduler.runCurrent()

            viewModel.closeForTest()
            renderGate.complete(Unit)
            ioScheduler.advanceUntilIdle()
            advanceUntilIdle()

            assertTrue(handle.bitmaps.values.isNotEmpty())
            assertTrue(handle.bitmaps.values.all(Bitmap::isRecycled))
        }

    private suspend fun loadedViewModel(
        handle: FakePdfDocumentHandle,
        files: FakePdfTemporaryFileRepository = FakePdfTemporaryFileRepository(),
        bitmapCacheBytes: Int = 32 * 1024 * 1024,
    ): PdfReaderViewModel {
        val viewModel = viewModel(
            files = files,
            documents = FakePdfDocumentFactory(
                results = ArrayDeque(listOf(AppResult.Success(handle))),
            ),
            bitmapCacheBytes = bitmapCacheBytes,
        )
        kotlinx.coroutines.test.TestScope(dispatcher).runCurrent()
        return viewModel
    }

    private fun viewModel(
        files: FakePdfTemporaryFileRepository,
        documents: FakePdfDocumentFactory,
        bitmapCacheBytes: Int = 32 * 1024 * 1024,
    ) = PdfReaderViewModel(
        fileName = FILE_NAME,
        logicalUrl = LOGICAL_URL,
        files = files,
        documents = documents,
        dispatchers = dispatchers,
        bitmapCacheBytes = bitmapCacheBytes,
    )
}

private const val FILE_NAME = "示例.pdf"
private const val LOGICAL_URL = "http://media.example/books/example.pdf"

private class FakePdfTemporaryFileRepository(
    private val acquireResults: ArrayDeque<AppResult<PdfTemporaryFile>> =
        ArrayDeque(
            listOf(
                AppResult.Success(
                    PdfTemporaryFile(
                        logicalUrl = LOGICAL_URL,
                        file = File("build/tmp/pdf-reader-test.pdf"),
                        byteCount = 1024L,
                    ),
                ),
            ),
        ),
    private val events: MutableList<String>? = null,
) : PdfTemporaryFileRepository {
    val acquiredLogicalUrls = mutableListOf<String>()
    val releasedFiles = mutableListOf<PdfTemporaryFile>()

    override suspend fun acquire(logicalUrl: String): AppResult<PdfTemporaryFile> {
        events?.add("acquire")
        acquiredLogicalUrls += logicalUrl
        return if (acquireResults.size > 1) {
            acquireResults.removeFirst()
        } else {
            acquireResults.first()
        }
    }

    override fun release(file: PdfTemporaryFile) {
        events?.add("release")
        releasedFiles += file
    }

    override suspend fun cleanupExpired(nowMs: Long) = Unit
}

private class FakePdfDocumentFactory(
    private val results: ArrayDeque<AppResult<PdfDocumentHandle>> = ArrayDeque(),
    private val events: MutableList<String>? = null,
    private val openGate: CompletableDeferred<Unit>? = null,
) : PdfDocumentFactory {
    override suspend fun open(file: File): AppResult<PdfDocumentHandle> {
        events?.add("open")
        openGate?.await()
        return results.removeFirst()
    }
}

private class FakePdfDocumentHandle(
    override val pageCount: Int,
    private val failures: MutableMap<Pair<Int, Int>, Int> = mutableMapOf(),
    private val renderGate: CompletableDeferred<Unit>? = null,
    private val renderDispatcher: kotlinx.coroutines.CoroutineDispatcher? = null,
    private val events: MutableList<String>? = null,
) : PdfDocumentHandle {
    override val pageSizes = (0 until pageCount).map { pageIndex ->
        PdfPageSize(pageIndex, 600 + pageIndex, 800 + pageIndex)
    }
    val renderRequests = mutableListOf<Pair<Int, Int>>()
    val renderedPages: List<Int>
        get() = renderRequests.map { it.first }
    val bitmaps = mutableMapOf<Pair<Int, Int>, Bitmap>()
    var closeCalls = 0
        private set

    override suspend fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int,
    ): AppResult<Bitmap> {
        renderRequests += pageIndex to targetWidthPx
        return if (renderDispatcher == null) {
            renderResult(pageIndex, targetWidthPx)
        } else {
            withContext(NonCancellable + renderDispatcher) {
                renderResult(pageIndex, targetWidthPx)
            }
        }
    }

    private suspend fun renderResult(
        pageIndex: Int,
        targetWidthPx: Int,
    ): AppResult<Bitmap> {
        renderGate?.await()
        val key = pageIndex to targetWidthPx
        val remainingFailures = failures[key] ?: 0
        if (remainingFailures > 0) {
            failures[key] = remainingFailures - 1
            return AppResult.Failure(AppError.PdfPageRenderFailure(pageIndex + 1))
        }
        val bitmap = Bitmap.createBitmap(
            targetWidthPx,
            1,
            Bitmap.Config.ARGB_8888,
        )
        bitmaps[key] = bitmap
        return AppResult.Success(bitmap)
    }

    override fun close() {
        events?.add("close")
        closeCalls += 1
    }
}
