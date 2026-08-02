package com.local.mediaviewer.pdf

import android.graphics.Bitmap

enum class PdfLoadPhase { DOWNLOADING, OPENING }

sealed interface PdfReaderUiState {
    data class Loading(
        val fileName: String,
        val phase: PdfLoadPhase,
    ) : PdfReaderUiState

    data class Content(
        val fileName: String,
        val pageSizes: List<PdfPageSize>,
        val pages: Map<Int, PdfPageUiState>,
        val currentPageIndex: Int,
    ) : PdfReaderUiState

    data class Error(
        val fileName: String,
        val message: String,
    ) : PdfReaderUiState
}

data class PdfPageUiState(
    val bitmap: Bitmap? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val renderedWidthPx: Int = 0,
)
