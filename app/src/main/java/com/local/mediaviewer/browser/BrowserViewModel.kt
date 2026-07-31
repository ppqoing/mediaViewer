package com.local.mediaviewer.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.ServerShare
import com.local.mediaviewer.queue.QueueMediaItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BrowserUiState {
    data class Loading(val previous: BrowserPage? = null) : BrowserUiState

    data class Content(val page: BrowserPage) : BrowserUiState

    data class Empty(val page: BrowserPage) : BrowserUiState

    data class Error(
        val error: AppError,
        val previous: BrowserPage? = null,
        val failedLogicalUrl: String? = null,
    ) : BrowserUiState
}

class BrowserViewModel(
    private val root: ServerShare,
    private val repository: BrowserRepository,
) : ViewModel() {
    private val pages = mutableListOf<BrowserPage>()
    private var pendingLoad: BrowserLoadRequest =
        BrowserLoadRequest.Root
    private var loadJob: Job? = null

    private val mutableUiState =
        MutableStateFlow<BrowserUiState>(BrowserUiState.Loading())
    val uiState: StateFlow<BrowserUiState> = mutableUiState.asStateFlow()

    private val mutableMediaLaunches = MutableSharedFlow<MediaLaunchRequest>()
    val mediaLaunches: SharedFlow<MediaLaunchRequest> =
        mutableMediaLaunches.asSharedFlow()

    private val mutablePlaybackRequests =
        MutableSharedFlow<BrowserPlaybackRequest>()
    val playbackRequests: SharedFlow<BrowserPlaybackRequest> =
        mutablePlaybackRequests.asSharedFlow()

    init {
        load(pendingLoad, replaceFromIndex = 0)
    }

    fun open(entry: DirectoryEntry) {
        val current = pages.lastOrNull() ?: return
        if (entry.kind != MediaKind.DIRECTORY) {
            viewModelScope.launch {
                if (entry.kind == MediaKind.IMAGE) {
                    mutableMediaLaunches.emit(
                        MediaLaunchRequest(
                            name = entry.name,
                            logicalUrl = entry.logicalUrl,
                            requestUrl = entry.requestUrl,
                            mediaKey = entry.logicalUrl,
                            kind = entry.kind,
                            rootId = root.id,
                            directoryLogicalUrl =
                                current.logicalDirectoryUrl,
                        ),
                    )
                } else {
                    emitPlaybackRequest(
                        BrowserPlaybackAction.PLAY_DIRECTORY,
                        entry,
                        current,
                    )
                }
            }
            return
        }

        val breadcrumbs = current.breadcrumbs +
            Breadcrumb(entry.name, entry.logicalUrl)
        load(
            request = BrowserLoadRequest.Directory(
                logicalUrl = entry.logicalUrl,
                breadcrumbs = breadcrumbs,
            ),
            replaceFromIndex = pages.size,
        )
    }

    fun requestPlayback(
        action: BrowserPlaybackAction,
        entry: DirectoryEntry,
    ) {
        val current = pages.lastOrNull() ?: return
        if (!entry.isPlayable) return
        viewModelScope.launch {
            emitPlaybackRequest(action, entry, current)
        }
    }

    fun openBreadcrumb(index: Int) {
        if (index !in pages.indices) return
        cancelPendingLoad()
        pages.subList(index + 1, pages.size).clear()
        show(pages[index])
    }

    fun goBack(): Boolean {
        val retainedPage = when (val state = mutableUiState.value) {
            is BrowserUiState.Loading -> state.previous
            is BrowserUiState.Error -> state.previous
            is BrowserUiState.Content,
            is BrowserUiState.Empty -> null
        }
        cancelPendingLoad()
        if (retainedPage != null) {
            show(retainedPage)
            return true
        }
        if (pages.size <= 1) {
            pages.lastOrNull()?.let(::show)
            return false
        }
        pages.removeAt(pages.lastIndex)
        show(pages.last())
        return true
    }

    fun retry() {
        val current = pages.lastOrNull()
        val failedLogicalUrl =
            (mutableUiState.value as? BrowserUiState.Error)
                ?.failedLogicalUrl
        val request = when {
            failedLogicalUrl != null -> {
                val failedRequest =
                    pendingLoad as? BrowserLoadRequest.Directory
                if (failedRequest?.logicalUrl != failedLogicalUrl) return
                failedRequest
            }

            current != null -> BrowserLoadRequest.Directory(
                logicalUrl = current.logicalDirectoryUrl,
                breadcrumbs = current.breadcrumbs,
            )

            else -> BrowserLoadRequest.Root
        }
        val replaceFromIndex = when (request) {
            BrowserLoadRequest.Root -> 0
            is BrowserLoadRequest.Directory ->
                if (
                    request.logicalUrl ==
                    current?.logicalDirectoryUrl
                ) {
                    pages.lastIndex
                } else {
                    pages.size
                }
        }
        load(request, replaceFromIndex)
    }

    private fun load(
        request: BrowserLoadRequest,
        replaceFromIndex: Int,
    ) {
        loadJob?.cancel()
        pendingLoad = request
        val previous = pages.lastOrNull()
        loadJob = viewModelScope.launch {
            mutableUiState.value = BrowserUiState.Loading(previous)
            val result = when (request) {
                BrowserLoadRequest.Root -> repository.openRoot(root)
                is BrowserLoadRequest.Directory ->
                    repository.openDirectory(
                        root = root,
                        logicalUrl = request.logicalUrl,
                        breadcrumbs = request.breadcrumbs,
                    )
            }
            when (result) {
                is AppResult.Success -> {
                    while (pages.size > replaceFromIndex) {
                        pages.removeAt(pages.lastIndex)
                    }
                    val existingIndex = pages.indexOfFirst {
                        it.logicalDirectoryUrl ==
                            result.value.logicalDirectoryUrl
                    }
                    if (existingIndex >= 0) {
                        while (pages.lastIndex > existingIndex) {
                            pages.removeAt(pages.lastIndex)
                        }
                        pages[existingIndex] = result.value
                    } else {
                        pages += result.value
                    }
                    show(pages.last())
                }

                is AppResult.Failure -> {
                    mutableUiState.value = BrowserUiState.Error(
                        error = result.error,
                        previous = previous,
                        failedLogicalUrl =
                            (request as? BrowserLoadRequest.Directory)
                                ?.logicalUrl,
                    )
                }
            }
        }
    }

    private fun cancelPendingLoad() {
        loadJob?.cancel()
        loadJob = null
    }

    private fun show(page: BrowserPage) {
        mutableUiState.value = if (page.entries.isEmpty()) {
            BrowserUiState.Empty(page)
        } else {
            BrowserUiState.Content(page)
        }
    }

    private suspend fun emitPlaybackRequest(
        action: BrowserPlaybackAction,
        entry: DirectoryEntry,
        page: BrowserPage,
    ) {
        mutablePlaybackRequests.emit(
            BrowserPlaybackRequest(
                action = action,
                selected = entry.toQueueItem(),
                directoryItems = page.entries
                    .filter(DirectoryEntry::isPlayable)
                    .map(DirectoryEntry::toQueueItem),
            ),
        )
    }
}

private sealed interface BrowserLoadRequest {
    data object Root : BrowserLoadRequest

    data class Directory(
        val logicalUrl: String,
        val breadcrumbs: List<Breadcrumb>,
    ) : BrowserLoadRequest
}

private val DirectoryEntry.isPlayable: Boolean
    get() = kind == MediaKind.VIDEO ||
        kind == MediaKind.AUDIO ||
        kind == MediaKind.UNKNOWN

private fun DirectoryEntry.toQueueItem() = QueueMediaItem(
    mediaKey = logicalUrl,
    name = name,
    logicalUrl = logicalUrl,
    kind = kind,
)
