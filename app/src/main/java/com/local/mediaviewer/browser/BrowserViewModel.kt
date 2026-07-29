package com.local.mediaviewer.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.RootShare
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
    data object Loading : BrowserUiState

    data class Content(val page: BrowserPage) : BrowserUiState

    data class Empty(val page: BrowserPage) : BrowserUiState

    data class Error(val error: AppError) : BrowserUiState
}

class BrowserViewModel(
    private val root: RootShare,
    private val repository: BrowserRepository,
) : ViewModel() {
    private val pages = mutableListOf<BrowserPage>()
    private var pendingLoad: suspend () -> AppResult<BrowserPage> =
        { repository.openRoot(root) }
    private var loadJob: Job? = null

    private val mutableUiState =
        MutableStateFlow<BrowserUiState>(BrowserUiState.Loading)
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
        val loader = suspend {
            repository.openDirectory(
                root = root,
                logicalUrl = entry.logicalUrl,
                breadcrumbs = breadcrumbs,
            )
        }
        pendingLoad = loader
        load(loader, replaceFromIndex = pages.size)
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
        cancelPendingLoad()
        if (pages.size <= 1) {
            pages.lastOrNull()?.let(::show)
            return false
        }
        pages.removeAt(pages.lastIndex)
        show(pages.last())
        return true
    }

    fun retry() {
        load(pendingLoad, replaceFromIndex = pages.size)
    }

    private fun load(
        loader: suspend () -> AppResult<BrowserPage>,
        replaceFromIndex: Int,
    ) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            mutableUiState.value = BrowserUiState.Loading
            when (val result = loader()) {
                is AppResult.Success -> {
                    while (pages.size > replaceFromIndex) {
                        pages.removeAt(pages.lastIndex)
                    }
                    if (
                        pages.lastOrNull()?.logicalDirectoryUrl !=
                        result.value.logicalDirectoryUrl
                    ) {
                        pages += result.value
                    }
                    show(result.value)
                }

                is AppResult.Failure -> {
                    mutableUiState.value = BrowserUiState.Error(result.error)
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
