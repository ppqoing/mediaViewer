package com.local.mediaviewer.pdf

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.core.DefaultDispatcherProvider
import com.local.mediaviewer.core.DispatcherProvider
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfReaderViewModel(
    private val fileName: String,
    private val logicalUrl: String,
    private val files: PdfTemporaryFileRepository,
    private val documents: PdfDocumentFactory,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
    private val bitmapCacheBytes: Int = defaultPdfBitmapCacheBytes(),
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<PdfReaderUiState>(
        PdfReaderUiState.Loading(fileName, PdfLoadPhase.DOWNLOADING),
    )
    val uiState: StateFlow<PdfReaderUiState> = mutableUiState.asStateFlow()

    private val renderJobs = mutableMapOf<Int, Job>()
    private val requestedWidths = mutableMapOf<Int, Int>()
    private val pageRequestIds = mutableMapOf<Int, Long>()
    private val pageRequests = mutableMapOf<Int, PageRequest>()
    private val cleanupScope = CoroutineScope(SupervisorJob() + dispatchers.main)
    private val bitmapCache = PdfPageBitmapCache(
        maxBytes = bitmapCacheBytes,
        onEvicted = ::onBitmapEvicted,
    )

    private var documentLoadJob: Job? = null
    private var temporaryFile: PdfTemporaryFile? = null
    private var document: PdfDocumentHandle? = null
    private var generation = 0L
    private var nextPageRequestId = 0L
    private var closed = false
    private var viewportPageIndices = emptySet<Int>()
    private var preservedBitmaps = emptyMap<Int, Bitmap>()

    init {
        loadDocument()
    }

    fun updateViewport(
        currentPageIndex: Int,
        visiblePageIndices: Set<Int>,
        viewportWidthPx: Int,
        renderScale: Float,
    ) {
        if (closed || viewportWidthPx <= 0) return
        val content = mutableUiState.value as? PdfReaderUiState.Content ?: return
        if (content.pageSizes.isEmpty()) return
        val lastPageIndex = content.pageSizes.lastIndex
        val safeCurrentPageIndex = currentPageIndex.coerceIn(0, lastPageIndex)
        if (content.currentPageIndex != safeCurrentPageIndex) {
            mutableUiState.value = content.copy(
                currentPageIndex = safeCurrentPageIndex,
            )
        }

        val targetWidthPx = (
            viewportWidthPx * renderScale.coerceIn(1f, 2f)
            ).roundToInt().coerceAtLeast(1)
        val pagesToRender = buildSet {
            (visiblePageIndices + safeCurrentPageIndex)
                .filter { it in 0..lastPageIndex }
                .forEach { pageIndex ->
                    add(pageIndex)
                    if (pageIndex > 0) add(pageIndex - 1)
                    if (pageIndex < lastPageIndex) add(pageIndex + 1)
                }
        }
        viewportPageIndices = pagesToRender
        pagesToRender.forEach { pageIndex ->
            val request = PageRequest(
                viewportWidthPx = viewportWidthPx,
                targetWidthPx = targetWidthPx,
            )
            pageRequests[pageIndex] = request
            requestPage(pageIndex, request)
        }
    }

    fun retryDocument() {
        if (closed) return
        loadDocument()
    }

    fun retryPage(pageIndex: Int) {
        if (closed) return
        val content = mutableUiState.value as? PdfReaderUiState.Content ?: return
        val page = content.pages[pageIndex] ?: return
        if (page.errorMessage == null) return
        val request = pageRequests[pageIndex] ?: return
        requestPage(pageIndex, request, force = true)
    }

    internal fun closeForTest() {
        closeResources()
    }

    override fun onCleared() {
        closeResources()
        super.onCleared()
    }

    private fun loadDocument() {
        generation += 1
        val loadGeneration = generation
        val previousLoadJob = documentLoadJob
        previousLoadJob?.cancel()
        val previousRenderJobs = cancelRenderJobs()
        mutableUiState.value = PdfReaderUiState.Loading(
            fileName = fileName,
            phase = PdfLoadPhase.DOWNLOADING,
        )
        documentLoadJob = viewModelScope.launch(dispatchers.main) {
            previousLoadJob?.join()
            previousRenderJobs.joinAll()
            releaseDocumentResources()
            if (!isCurrent(loadGeneration)) return@launch
            val acquired = withContext(NonCancellable) {
                files.acquire(logicalUrl)
            }
            when (acquired) {
                is AppResult.Failure -> {
                    if (isCurrent(loadGeneration)) {
                        mutableUiState.value = PdfReaderUiState.Error(
                            fileName = fileName,
                            message = acquired.error.userMessage,
                        )
                    }
                }

                is AppResult.Success -> {
                    if (!isCurrent(loadGeneration)) {
                        files.release(acquired.value)
                        return@launch
                    }
                    temporaryFile = acquired.value
                    mutableUiState.value = PdfReaderUiState.Loading(
                        fileName = fileName,
                        phase = PdfLoadPhase.OPENING,
                    )
                    withContext(NonCancellable) {
                        when (val opened = documents.open(acquired.value.file)) {
                            is AppResult.Failure -> {
                                if (isCurrent(loadGeneration)) {
                                    releaseTemporaryFile()
                                    mutableUiState.value = PdfReaderUiState.Error(
                                        fileName = fileName,
                                        message = opened.error.userMessage,
                                    )
                                }
                            }

                            is AppResult.Success -> {
                                if (!isCurrent(loadGeneration)) {
                                    withContext(dispatchers.io) {
                                        opened.value.close()
                                    }
                                } else {
                                    document = opened.value
                                    mutableUiState.value = PdfReaderUiState.Content(
                                        fileName = fileName,
                                        pageSizes = opened.value.pageSizes,
                                        pages = emptyMap(),
                                        currentPageIndex = 0,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestPage(
        pageIndex: Int,
        request: PageRequest,
        force: Boolean = false,
    ) {
        val content = mutableUiState.value as? PdfReaderUiState.Content ?: return
        if (pageIndex !in content.pageSizes.indices) return
        val page = content.pages[pageIndex] ?: PdfPageUiState()
        if (
            !force &&
            page.bitmap?.isRecycled == false &&
            page.renderedWidthPx >= request.targetWidthPx
        ) {
            bitmapCache.get(pageIndex)
            return
        }
        val pendingWidth = requestedWidths[pageIndex]
        if (!force && pendingWidth != null && pendingWidth >= request.targetWidthPx) return

        renderJobs.remove(pageIndex)?.cancel()
        requestedWidths[pageIndex] = request.targetWidthPx
        val pageRequestId = ++nextPageRequestId
        pageRequestIds[pageIndex] = pageRequestId
        setPageState(
            pageIndex,
            page.copy(isLoading = true, errorMessage = null),
        )
        val renderGeneration = generation
        val renderDocument = document ?: return
        val job = viewModelScope.launch(
            context = dispatchers.main,
            start = CoroutineStart.LAZY,
        ) {
            try {
                val initialResult = withContext(NonCancellable) {
                    renderDocument.renderPage(
                        pageIndex = pageIndex,
                        targetWidthPx = request.targetWidthPx,
                    )
                }
                if (
                    !isCurrent(renderGeneration) ||
                    document !== renderDocument ||
                    pageRequestIds[pageIndex] != pageRequestId
                ) {
                    recycleSuccess(initialResult)
                    return@launch
                }
                val result = if (
                    initialResult is AppResult.Failure &&
                    request.targetWidthPx > request.viewportWidthPx
                ) {
                    clearDistantBitmaps()
                    withContext(NonCancellable) {
                        renderDocument.renderPage(
                            pageIndex = pageIndex,
                            targetWidthPx = request.viewportWidthPx,
                        )
                    }
                } else {
                    initialResult
                }
                val renderedWidthPx = if (
                    initialResult is AppResult.Failure &&
                    request.targetWidthPx > request.viewportWidthPx
                ) {
                    request.viewportWidthPx
                } else {
                    request.targetWidthPx
                }
                publishRenderResult(
                    pageIndex = pageIndex,
                    renderedWidthPx = renderedWidthPx,
                    renderGeneration = renderGeneration,
                    renderDocument = renderDocument,
                    pageRequestId = pageRequestId,
                    result = result,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                if (pageRequestIds[pageIndex] == pageRequestId) {
                    requestedWidths.remove(pageIndex)
                    pageRequestIds.remove(pageIndex)
                    renderJobs.remove(pageIndex)
                }
            }
        }
        renderJobs[pageIndex] = job
        job.start()
    }

    private fun publishRenderResult(
        pageIndex: Int,
        renderedWidthPx: Int,
        renderGeneration: Long,
        renderDocument: PdfDocumentHandle,
        pageRequestId: Long,
        result: AppResult<Bitmap>,
    ) {
        if (
            !isCurrent(renderGeneration) ||
            document !== renderDocument ||
            pageRequestIds[pageIndex] != pageRequestId
        ) {
            recycleSuccess(result)
            return
        }
        when (result) {
            is AppResult.Failure -> {
                val current = currentPageState(pageIndex)
                setPageState(
                    pageIndex,
                    current.copy(
                        isLoading = false,
                        errorMessage = result.error.userMessage,
                    ),
                )
            }

            is AppResult.Success -> {
                val current = currentPageState(pageIndex)
                setPageState(
                    pageIndex,
                    current.copy(
                        bitmap = result.value,
                        isLoading = false,
                        errorMessage = null,
                        renderedWidthPx = renderedWidthPx,
                    ),
                )
                bitmapCache.put(pageIndex, result.value)
            }
        }
    }

    private fun currentPageState(pageIndex: Int): PdfPageUiState =
        (mutableUiState.value as? PdfReaderUiState.Content)
            ?.pages
            ?.get(pageIndex)
            ?: PdfPageUiState()

    private fun setPageState(pageIndex: Int, page: PdfPageUiState) {
        val content = mutableUiState.value as? PdfReaderUiState.Content ?: return
        mutableUiState.value = content.copy(
            pages = content.pages + (pageIndex to page),
        )
    }

    private fun onBitmapEvicted(pageIndex: Int, bitmap: Bitmap) {
        if (preservedBitmaps[pageIndex] === bitmap) return
        val content = mutableUiState.value as? PdfReaderUiState.Content
        val page = content?.pages?.get(pageIndex)
        if (content != null && page?.bitmap === bitmap) {
            mutableUiState.value = content.copy(
                pages = content.pages + (
                    pageIndex to page.copy(
                        bitmap = null,
                        renderedWidthPx = 0,
                    )
                    ),
            )
        }
        if (!bitmap.isRecycled) bitmap.recycle()
    }

    private fun closeResources() {
        if (closed) return
        closed = true
        generation += 1
        val loadJob = documentLoadJob
        loadJob?.cancel()
        documentLoadJob = null
        val jobs = cancelRenderJobs()
        cleanupScope.launch {
            loadJob?.join()
            jobs.joinAll()
            releaseDocumentResources()
            cleanupScope.cancel()
        }
    }

    private fun cancelRenderJobs(): List<Job> {
        val jobs = renderJobs.values.toList()
        jobs.forEach(Job::cancel)
        renderJobs.clear()
        requestedWidths.clear()
        pageRequestIds.clear()
        pageRequests.clear()
        viewportPageIndices = emptySet()
        return jobs
    }

    private suspend fun releaseDocumentResources() {
        bitmapCache.clear()
        val oldDocument = document
        val oldTemporaryFile = temporaryFile
        document = null
        temporaryFile = null
        withContext(dispatchers.io) {
            oldDocument?.close()
            oldTemporaryFile?.let(files::release)
        }
    }

    private fun releaseTemporaryFile() {
        temporaryFile?.let(files::release)
        temporaryFile = null
    }

    private fun isCurrent(expectedGeneration: Long): Boolean =
        !closed && generation == expectedGeneration

    private fun clearDistantBitmaps() {
        val nearby = viewportPageIndices.mapNotNull { pageIndex ->
            bitmapCache.get(pageIndex)?.let { pageIndex to it }
        }.toMap()
        preservedBitmaps = nearby
        bitmapCache.clear()
        preservedBitmaps = emptyMap()
        nearby.forEach(bitmapCache::put)
    }

    private fun recycleSuccess(result: AppResult<Bitmap>) {
        if (result is AppResult.Success && !result.value.isRecycled) {
            result.value.recycle()
        }
    }

    private data class PageRequest(
        val viewportWidthPx: Int,
        val targetWidthPx: Int,
    )
}

fun defaultPdfBitmapCacheBytes(): Int =
    minOf(
        Runtime.getRuntime().maxMemory() / 8L,
        48L * 1024L * 1024L,
    ).toInt()
