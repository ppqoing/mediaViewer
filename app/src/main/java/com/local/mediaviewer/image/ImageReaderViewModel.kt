package com.local.mediaviewer.image

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.mediaviewer.browser.DirectoryContentRepository
import com.local.mediaviewer.core.AppResult
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
) : ViewModel() {
    private val mutableUiState =
        MutableStateFlow<ImageReaderUiState>(
            ImageReaderUiState.Loading,
        )
    val uiState: StateFlow<ImageReaderUiState> =
        mutableUiState.asStateFlow()

    private var loadJob: Job? = null

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
}
