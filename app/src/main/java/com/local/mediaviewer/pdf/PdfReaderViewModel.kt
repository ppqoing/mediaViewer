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
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

    private val pendingRenders = mutableMapOf<Int, ScheduledRender>()
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
    private var nextRenderSequence = 0L
    private var closed = false
    private var activeRender: ScheduledRender? = null
    private var renderPumpJob: Job? = null
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
        val visiblePages = visiblePageIndices
            .filter { it in 0..lastPageIndex }
        val primaryPages = buildList {
            add(safeCurrentPageIndex)
            visiblePages.forEach { pageIndex ->
                if (pageIndex != safeCurrentPageIndex) add(pageIndex)
            }
        }
        val neighborPages = buildList {
            primaryPages.forEach { pageIndex ->
                if (pageIndex > 0) add(pageIndex - 1)
                if (pageIndex < lastPageIndex) add(pageIndex + 1)
            }
        }.filter { it !in primaryPages }.distinct()
        val orderedPages = primaryPages + neighborPages
        viewportPageIndices = orderedPages.toSet()
        replaceViewportQueue(
            desiredPages = viewportPageIndices,
            targetWidthPx = targetWidthPx,
        )
        orderedPages.forEach { pageIndex ->
            val request = PageRequest(
                viewportWidthPx = viewportWidthPx,
                targetWidthPx = targetWidthPx,
            )
            pageRequests[pageIndex] = request
            requestPage(
                pageIndex = pageIndex,
                request = request,
                priority = when {
                    pageIndex == safeCurrentPageIndex -> 0
                    pageIndex in visiblePages -> 1
                    else -> 2
                },
            )
        }
        startRenderPump()
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
        requestPage(
            pageIndex = pageIndex,
            request = request,
            priority = if (pageIndex == content.currentPageIndex) 0 else 1,
            force = true,
        )
        startRenderPump()
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
            val acquired = files.acquire(logicalUrl)
            withContext(NonCancellable) {
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
                        } else {
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
        }
    }

    private fun requestPage(
        pageIndex: Int,
        request: PageRequest,
        priority: Int,
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
        val active = activeRender?.takeIf { it.pageIndex == pageIndex }
        if (
            !force &&
            active != null &&
            pageRequestIds[pageIndex] == active.pageRequestId &&
            active.request.targetWidthPx >= request.targetWidthPx
        ) {
            return
        }
        val pending = pendingRenders[pageIndex]
        if (
            !force &&
            pending != null &&
            pending.request.targetWidthPx == request.targetWidthPx
        ) {
            pendingRenders[pageIndex] = pending.copy(
                priority = priority,
                sequence = ++nextRenderSequence,
            )
            return
        }
        invalidateRequest(pageIndex)
        val pageRequestId = ++nextPageRequestId
        pageRequestIds[pageIndex] = pageRequestId
        setPageState(
            pageIndex,
            page.copy(isLoading = true, errorMessage = null),
        )
        val renderGeneration = generation
        val renderDocument = document ?: return
        pendingRenders[pageIndex] = ScheduledRender(
            pageIndex = pageIndex,
            request = request,
            priority = priority,
            sequence = ++nextRenderSequence,
            renderGeneration = renderGeneration,
            renderDocument = renderDocument,
            pageRequestId = pageRequestId,
        )
    }

    private fun replaceViewportQueue(
        desiredPages: Set<Int>,
        targetWidthPx: Int,
    ) {
        pendingRenders.keys.toList().forEach(::invalidateRequest)
        activeRender?.let { active ->
            if (
                active.pageIndex !in desiredPages ||
                active.request.targetWidthPx < targetWidthPx
            ) {
                invalidateRequest(active.pageIndex)
            }
        }
    }

    private fun invalidateRequest(pageIndex: Int) {
        pendingRenders.remove(pageIndex)
        pageRequestIds.remove(pageIndex)
        val page = currentPageState(pageIndex)
        if (page.isLoading) {
            setPageState(pageIndex, page.copy(isLoading = false))
        }
    }

    private fun startRenderPump() {
        if (renderPumpJob?.isActive == true || pendingRenders.isEmpty()) return
        renderPumpJob = viewModelScope.launch(dispatchers.main) {
            try {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val scheduled = pendingRenders.values.minWithOrNull(
                        compareBy<ScheduledRender> { it.priority }
                            .thenBy { it.sequence },
                    ) ?: break
                    pendingRenders.remove(scheduled.pageIndex)
                    activeRender = scheduled
                    renderScheduled(scheduled)
                    if (activeRender?.pageRequestId == scheduled.pageRequestId) {
                        activeRender = null
                    }
                }
            } finally {
                renderPumpJob = null
                activeRender = null
            }
        }
    }

    private suspend fun renderScheduled(scheduled: ScheduledRender) {
        var unclaimedResult: AppResult<Bitmap>? = null
        try {
            withContext(NonCancellable) {
                unclaimedResult = scheduled.renderDocument.renderPage(
                    pageIndex = scheduled.pageIndex,
                    targetWidthPx = scheduled.request.targetWidthPx,
                )
            }
            val initialResult = checkNotNull(unclaimedResult)
            if (!isScheduledRequestCurrent(scheduled)) {
                return
            }
            currentCoroutineContext().ensureActive()
            val result = if (
                initialResult is AppResult.Failure &&
                scheduled.request.targetWidthPx > scheduled.request.viewportWidthPx
            ) {
                clearDistantBitmaps()
                withContext(NonCancellable) {
                    unclaimedResult = scheduled.renderDocument.renderPage(
                        pageIndex = scheduled.pageIndex,
                        targetWidthPx = scheduled.request.viewportWidthPx,
                    )
                }
                checkNotNull(unclaimedResult)
            } else {
                initialResult
            }
            val renderedWidthPx = if (
                initialResult is AppResult.Failure &&
                scheduled.request.targetWidthPx > scheduled.request.viewportWidthPx
            ) {
                scheduled.request.viewportWidthPx
            } else {
                scheduled.request.targetWidthPx
            }
            publishRenderResult(
                pageIndex = scheduled.pageIndex,
                renderedWidthPx = renderedWidthPx,
                renderGeneration = scheduled.renderGeneration,
                renderDocument = scheduled.renderDocument,
                pageRequestId = scheduled.pageRequestId,
                result = result,
            )
            unclaimedResult = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            unclaimedResult?.let(::recycleSuccess)
            if (pageRequestIds[scheduled.pageIndex] == scheduled.pageRequestId) {
                pageRequestIds.remove(scheduled.pageIndex)
            }
        }
    }

    private fun isScheduledRequestCurrent(scheduled: ScheduledRender): Boolean =
        isCurrent(scheduled.renderGeneration) &&
            document === scheduled.renderDocument &&
            pageRequestIds[scheduled.pageIndex] == scheduled.pageRequestId

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
        val renderJob = renderPumpJob
        renderJob?.cancel()
        (pendingRenders.keys + listOfNotNull(activeRender?.pageIndex))
            .toSet()
            .forEach(::invalidateRequest)
        pendingRenders.clear()
        pageRequests.clear()
        viewportPageIndices = emptySet()
        return listOfNotNull(renderJob)
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

    private data class ScheduledRender(
        val pageIndex: Int,
        val request: PageRequest,
        val priority: Int,
        val sequence: Long,
        val renderGeneration: Long,
        val renderDocument: PdfDocumentHandle,
        val pageRequestId: Long,
    )
}

fun defaultPdfBitmapCacheBytes(): Int =
    minOf(
        Runtime.getRuntime().maxMemory() / 8L,
        48L * 1024L * 1024L,
    ).toInt()
