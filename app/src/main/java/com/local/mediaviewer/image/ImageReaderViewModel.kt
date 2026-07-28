package com.local.mediaviewer.image

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.mediaviewer.browser.DirectoryContentRepository
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.session.ServerSessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ImageReaderUiState {
    data object Loading : ImageReaderUiState

    data class Content(
        val images: List<ImageReaderItem>,
        val mode: ImageReaderMode,
        val sortOrder: ImageSortOrder,
        val anchorLogicalUrl: String,
        val requestGeneration: Int = 0,
        val isRefreshingEndpoint: Boolean = false,
        val itemFailures:
            Map<String, ImageItemFailure> = emptyMap(),
        val itemRequestGenerations:
            Map<String, Int> = emptyMap(),
    ) : ImageReaderUiState

    data object Empty : ImageReaderUiState

    data class Error(
        val message: String,
    ) : ImageReaderUiState
}

class ImageReaderViewModel(
    private val directoryLogicalUrl: String,
    private val selectedLogicalUrl: String,
    private val contentRepository: DirectoryContentRepository,
    private val preferences: ReaderPreferencesRepository,
    private val session: ServerSessionManager,
) : ViewModel() {
    private val mutableUiState =
        MutableStateFlow<ImageReaderUiState>(
            ImageReaderUiState.Loading,
        )
    val uiState: StateFlow<ImageReaderUiState> =
        mutableUiState.asStateFlow()

    private var loadJob: Job? = null
    private var refreshJob: Job? = null
    private var automaticEndpointRefreshUsed = false

    init {
        load()
    }

    fun retryDirectory() {
        load()
    }

    fun setMode(mode: ImageReaderMode) {
        val content =
            mutableUiState.value
                as? ImageReaderUiState.Content
                ?: return
        if (content.mode == mode) return
        mutableUiState.value = content.copy(mode = mode)
    }

    fun setSortOrder(order: ImageSortOrder) {
        val content =
            mutableUiState.value
                as? ImageReaderUiState.Content
                ?: return
        if (content.sortOrder == order) return
        mutableUiState.value = content.copy(
            images = ImageSequence.sort(
                content.images,
                order,
            ),
            sortOrder = order,
        )
    }

    fun updateAnchor(logicalUrl: String) {
        val content =
            mutableUiState.value
                as? ImageReaderUiState.Content
                ?: return
        if (content.anchorLogicalUrl == logicalUrl) return
        if (
            content.images.none {
                it.logicalUrl == logicalUrl
            }
        ) {
            return
        }
        mutableUiState.value =
            content.copy(anchorLogicalUrl = logicalUrl)
    }

    fun onImageLoadError(
        logicalUrl: String,
        kind: ImageLoadFailureKind,
    ) {
        val content = contentWith(logicalUrl) ?: return
        mutableUiState.value = content.copy(
            itemFailures =
                content.itemFailures +
                    (
                        logicalUrl to
                            ImageItemFailure(
                                message =
                                    kind.userMessage(),
                                kind = kind,
                            )
                    ),
        )
        if (kind != ImageLoadFailureKind.NETWORK) {
            return
        }
        if (
            automaticEndpointRefreshUsed ||
            refreshJob?.isActive == true
        ) {
            return
        }
        automaticEndpointRefreshUsed = true
        refreshJob = viewModelScope.launch {
            setRefreshing(true)
            when (
                val result =
                    session
                        .refreshAfterRequestFailure()
            ) {
                is AppResult.Success -> {
                    remapRequests(result.value)
                }

                is AppResult.Failure -> {
                    retainRefreshFailure(
                        result.error.userMessage,
                    )
                }
            }
            refreshJob = null
        }
    }

    fun onImageLoadSuccess(logicalUrl: String) {
        val content = contentWith(logicalUrl) ?: return
        if (logicalUrl !in content.itemFailures) return
        mutableUiState.value = content.copy(
            itemFailures =
                content.itemFailures - logicalUrl,
        )
    }

    fun retryImage(logicalUrl: String) {
        val content = contentWith(logicalUrl) ?: return
        val nextGeneration =
            (
                content.itemRequestGenerations[
                    logicalUrl
                ] ?: 0
            ) + 1
        mutableUiState.value = content.copy(
            itemFailures =
                content.itemFailures - logicalUrl,
            itemRequestGenerations =
                content.itemRequestGenerations +
                    (
                        logicalUrl to
                            nextGeneration
                    ),
        )
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            mutableUiState.value =
                ImageReaderUiState.Loading
            val mode = preferences.currentDefaultMode()
            when (
                val result =
                    contentRepository.load(
                        directoryLogicalUrl,
                    )
            ) {
                is AppResult.Success -> {
                    val images =
                        ImageSequence.fromEntries(
                            result.value.entries,
                            ImageSortOrder.NAME_ASC,
                        )
                    val anchor =
                        ImageSequence.anchorOrFirst(
                            images,
                            selectedLogicalUrl,
                        )
                    mutableUiState.value =
                        if (anchor == null) {
                            ImageReaderUiState.Empty
                        } else {
                            ImageReaderUiState.Content(
                                images = images,
                                mode = mode,
                                sortOrder =
                                    ImageSortOrder.NAME_ASC,
                                anchorLogicalUrl = anchor,
                            )
                        }
                }

                is AppResult.Failure -> {
                    mutableUiState.value =
                        ImageReaderUiState.Error(
                            result.error.userMessage,
                        )
                }
            }
        }
    }

    private fun contentWith(
        logicalUrl: String,
    ): ImageReaderUiState.Content? {
        val content =
            mutableUiState.value
                as? ImageReaderUiState.Content
                ?: return null
        return content.takeIf {
            it.images.any { item ->
                item.logicalUrl == logicalUrl
            }
        }
    }

    private fun setRefreshing(refreshing: Boolean) {
        val content =
            mutableUiState.value
                as? ImageReaderUiState.Content
                ?: return
        if (
            content.isRefreshingEndpoint ==
            refreshing
        ) {
            return
        }
        mutableUiState.value = content.copy(
            isRefreshingEndpoint = refreshing,
        )
    }

    private fun remapRequests(
        endpoint: SessionEndpoint,
    ) {
        val content =
            mutableUiState.value
                as? ImageReaderUiState.Content
                ?: return
        mutableUiState.value = content.copy(
            images = content.images.map { item ->
                item.copy(
                    requestUrl =
                        endpoint.requestUrlFor(
                            item.logicalUrl,
                        ),
                )
            },
            requestGeneration =
                content.requestGeneration + 1,
            isRefreshingEndpoint = false,
            itemFailures =
                content.itemFailures.filterValues {
                    it.kind !=
                        ImageLoadFailureKind.NETWORK
                },
        )
    }

    private fun retainRefreshFailure(
        message: String,
    ) {
        val content =
            mutableUiState.value
                as? ImageReaderUiState.Content
                ?: return
        mutableUiState.value = content.copy(
            isRefreshingEndpoint = false,
            itemFailures =
                content.itemFailures.mapValues {
                    (_, failure) ->
                    if (
                        failure.kind ==
                        ImageLoadFailureKind.NETWORK
                    ) {
                        failure.copy(message = message)
                    } else {
                        failure
                    }
                },
        )
    }
}
