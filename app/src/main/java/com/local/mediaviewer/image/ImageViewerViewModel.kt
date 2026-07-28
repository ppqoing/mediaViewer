package com.local.mediaviewer.image

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.session.ServerSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ImageViewerUiState(
    val requestUrl: String,
    val requestGeneration: Int = 0,
    val isRefreshingEndpoint: Boolean = false,
    val errorMessage: String? = null,
)

class ImageViewerViewModel(
    private val logicalUrl: String,
    initialRequestUrl: String,
    private val session: ServerSessionManager,
) : ViewModel() {
    private var endpointRetryUsed = false
    private val mutableUiState = MutableStateFlow(
        ImageViewerUiState(requestUrl = initialRequestUrl),
    )
    val uiState: StateFlow<ImageViewerUiState> =
        mutableUiState.asStateFlow()

    fun onLoadError() {
        if (mutableUiState.value.isRefreshingEndpoint) return
        if (endpointRetryUsed) {
            mutableUiState.value = mutableUiState.value.copy(
                errorMessage = "图片加载失败",
            )
            return
        }

        endpointRetryUsed = true
        mutableUiState.value = mutableUiState.value.copy(
            isRefreshingEndpoint = true,
            errorMessage = null,
        )
        viewModelScope.launch {
            when (
                val refreshed =
                    session.refreshAfterRequestFailure()
            ) {
                is AppResult.Success -> {
                    mutableUiState.value = mutableUiState.value.copy(
                        requestUrl =
                            refreshed.value.requestUrlFor(logicalUrl),
                        requestGeneration =
                            mutableUiState.value.requestGeneration + 1,
                        isRefreshingEndpoint = false,
                        errorMessage = null,
                    )
                }

                is AppResult.Failure -> {
                    mutableUiState.value = mutableUiState.value.copy(
                        isRefreshingEndpoint = false,
                        errorMessage = refreshed.error.userMessage,
                    )
                }
            }
        }
    }

    fun retry() {
        mutableUiState.value = mutableUiState.value.copy(
            requestGeneration =
                mutableUiState.value.requestGeneration + 1,
            errorMessage = null,
        )
    }
}
