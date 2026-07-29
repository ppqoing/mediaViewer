package com.local.mediaviewer.ui.image

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import coil3.ImageLoader
import com.local.mediaviewer.image.ComicTransform
import com.local.mediaviewer.image.ImageReaderMode
import com.local.mediaviewer.image.ImageReaderUiState
import com.local.mediaviewer.image.ImageLoadFailureKind
import com.local.mediaviewer.image.ImageSortOrder
import com.local.mediaviewer.ui.components.AppErrorPanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageReaderScreen(
    state: ImageReaderUiState,
    imageLoader: ImageLoader,
    onModeChanged: (ImageReaderMode) -> Unit,
    onSortChanged: (ImageSortOrder) -> Unit,
    onAnchorChanged: (String) -> Unit,
    onRetryDirectory: () -> Unit,
    onImageLoadError:
        (String, ImageLoadFailureKind) -> Unit,
    onImageLoadSuccess: (String) -> Unit,
    onRetryImage: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var comicTransform by rememberSaveable(
        stateSaver = comicTransformSaver,
    ) {
        mutableStateOf(ComicTransform())
    }
    val content =
        state as? ImageReaderUiState.Content
    val current = content
        ?.images
        ?.firstOrNull {
            it.logicalUrl ==
                content.anchorLogicalUrl
        }
        ?: content?.images?.firstOrNull()

    Scaffold(
        containerColor = Color.Black,
        contentColor = Color.White,
        topBar = {
            if (content != null && current != null) {
                ImageReaderToolbar(
                    title = current.name,
                    mode = content.mode,
                    sortOrder = content.sortOrder,
                    onModeChanged = onModeChanged,
                    onSortChanged = onSortChanged,
                    onBack = onBack,
                )
            } else {
                TopAppBar(
                    title = { Text("图片阅读") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector =
                                    Icons.AutoMirrored
                                        .Default
                                        .ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults
                            .topAppBarColors(
                                containerColor =
                                    Color.Black,
                                titleContentColor =
                                    Color.White,
                                navigationIconContentColor =
                                    Color.White,
                            ),
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                ImageReaderUiState.Loading -> {
                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Text("正在加载图片…")
                    }
                }

                ImageReaderUiState.Empty -> {
                    Text("此目录没有图片")
                }

                is ImageReaderUiState.Error -> {
                    AppErrorPanel(
                        message = state.message,
                        onRetry = onRetryDirectory,
                    )
                }

                is ImageReaderUiState.Content -> {
                    if (current == null) {
                        Text("此目录没有图片")
                    } else if (
                        state.mode ==
                        ImageReaderMode.COMIC
                    ) {
                        ComicReader(
                            images = state.images,
                            anchorLogicalUrl =
                                state.anchorLogicalUrl,
                            sortOrder = state.sortOrder,
                            imageLoader = imageLoader,
                            requestGeneration =
                                state.requestGeneration,
                            itemFailures =
                                state.itemFailures,
                            itemRequestGenerations =
                                state
                                    .itemRequestGenerations,
                            transform =
                                comicTransform,
                            onTransformChanged = {
                                comicTransform = it
                            },
                            onAnchorChanged =
                                onAnchorChanged,
                            onImageLoadError =
                                onImageLoadError,
                            onImageLoadSuccess =
                                onImageLoadSuccess,
                            onRetryImage =
                                onRetryImage,
                            modifier =
                                Modifier.fillMaxSize(),
                        )
                    } else {
                        SingleImageViewer(
                            item = current,
                            imageLoader = imageLoader,
                            requestGeneration =
                                effectiveRequestGeneration(
                                    requestGeneration =
                                        state
                                            .requestGeneration,
                                    itemRequestGeneration =
                                        state
                                            .itemRequestGenerations[
                                                current
                                                    .logicalUrl
                                            ] ?: 0,
                                ),
                            failure =
                                state.itemFailures[
                                    current.logicalUrl
                                ],
                            onImageLoadError =
                                onImageLoadError,
                            onImageLoadSuccess =
                                onImageLoadSuccess,
                            onRetryImage =
                                onRetryImage,
                            modifier =
                                Modifier.fillMaxSize(),
                        )
                    }
                    if (state.isRefreshingEndpoint) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

private val comicTransformSaver = listSaver(
    save = {
        listOf(
            it.scale,
            it.horizontalOffsetPx,
        )
    },
    restore = {
        ComicTransform(
            scale = it[0],
            horizontalOffsetPx = it[1],
        )
    },
)
