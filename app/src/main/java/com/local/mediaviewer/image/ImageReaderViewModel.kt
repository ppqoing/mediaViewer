package com.local.mediaviewer.image

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.mediaviewer.browser.DirectoryContentRepository
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.session.ServerSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
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
        val refreshingImageLogicalUrl: String? = null,
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

    private enum class EndpointRefreshTrigger {
        AUTOMATIC,
        USER,
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
        if (kind == ImageLoadFailureKind.NETWORK) {
            refreshEndpoint(
                trigger = EndpointRefreshTrigger.AUTOMATIC,
                retryLogicalUrl = null,
                targetLogicalUrl = logicalUrl,
            )
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
        val failure = content.itemFailures[logicalUrl] ?: return
        if (failure.kind == ImageLoadFailureKind.NETWORK) {
            refreshEndpoint(
                trigger = EndpointRefreshTrigger.USER,
                retryLogicalUrl = logicalUrl,
                targetLogicalUrl = logicalUrl,
            )
            return
        }
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

    private fun setRefreshing(
        refreshing: Boolean,
        targetLogicalUrl: String? = null,
    ) {
        val content =
            mutableUiState.value
                as? ImageReaderUiState.Content
                ?: return
        val refreshingLogicalUrl =
            targetLogicalUrl.takeIf { refreshing }
        if (
            content.isRefreshingEndpoint ==
            refreshing &&
            content.refreshingImageLogicalUrl ==
            refreshingLogicalUrl
        ) {
            return
        }
        mutableUiState.value = content.copy(
            isRefreshingEndpoint = refreshing,
            refreshingImageLogicalUrl =
                refreshingLogicalUrl,
        )
    }

    private fun refreshEndpoint(
        trigger: EndpointRefreshTrigger,
        retryLogicalUrl: String?,
        targetLogicalUrl: String,
    ) {
        if (refreshJob?.isActive == true) return
        if (
            trigger == EndpointRefreshTrigger.AUTOMATIC &&
            automaticEndpointRefreshUsed
        ) {
            return
        }
        if (trigger == EndpointRefreshTrigger.AUTOMATIC) {
            automaticEndpointRefreshUsed = true
        }
        val job = viewModelScope.launch(
            start = CoroutineStart.LAZY,
        ) {
            try {
                setRefreshing(
                    refreshing = true,
                    targetLogicalUrl =
                        targetLogicalUrl,
                )
                when (
                    val result =
                        session
                            .refreshAfterRequestFailure()
                ) {
                    is AppResult.Success -> {
                        remapFailedRequests(
                            endpoint = result.value,
                            retryLogicalUrl =
                                retryLogicalUrl,
                        )
                    }

                    is AppResult.Failure -> {
                        retainRefreshFailure(
                            message =
                                result.error.userMessage,
                            retryLogicalUrl =
                                retryLogicalUrl,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                retainRefreshFailure(
                    message =
                        refreshFailureMessage(error),
                    retryLogicalUrl =
                        retryLogicalUrl,
                )
            } finally {
                setRefreshing(false)
                refreshJob = null
            }
        }
        refreshJob = job
        job.start()
    }

    private fun refreshFailureMessage(
        error: Exception,
    ): String {
        val detail = error.message
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return if (detail == null) {
            "重新连接失败，请稍后重试"
        } else {
            "重新连接失败：$detail"
        }
    }

    private fun remapFailedRequests(
        endpoint: SessionEndpoint,
        retryLogicalUrl: String?,
    ) {
        val content =
            mutableUiState.value
                as? ImageReaderUiState.Content
                ?: return
        val retryKeys =
            content.itemFailures
                .filterValues {
                    it.kind == ImageLoadFailureKind.NETWORK
                }
                .keys + listOfNotNull(retryLogicalUrl)
        mutableUiState.value = content.copy(
            images = content.images.map { item ->
                if (item.logicalUrl in retryKeys) {
                    item.copy(
                        requestUrl = endpoint.requestUrlFor(item.logicalUrl),
                    )
                } else {
                    item
                }
            },
            itemRequestGenerations =
                content.itemRequestGenerations.toMutableMap().apply {
                    retryKeys.forEach { key ->
                        this[key] = (this[key] ?: 0) + 1
                    }
                },
            itemFailures = content.itemFailures - retryKeys,
        )
    }

    private fun retainRefreshFailure(
        message: String,
        retryLogicalUrl: String?,
    ) {
        val content =
            mutableUiState.value
                as? ImageReaderUiState.Content
                ?: return
        mutableUiState.value = content.copy(
            itemFailures =
                content.itemFailures.mapValues {
                    (logicalUrl, failure) ->
                    if (
                        logicalUrl == retryLogicalUrl ||
                        (
                            retryLogicalUrl == null &&
                                failure.kind == ImageLoadFailureKind.NETWORK
                        )
                    ) {
                        failure.copy(message = message)
                    } else {
                        failure
                    }
                },
        )
    }
}
