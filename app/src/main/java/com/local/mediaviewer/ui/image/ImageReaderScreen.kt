package com.local.mediaviewer.ui.image

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import coil3.ImageLoader
import com.local.mediaviewer.image.ComicTransform
import com.local.mediaviewer.image.ImageLoadFailureKind
import com.local.mediaviewer.image.ImageReaderItem
import com.local.mediaviewer.image.ImageReaderMode
import com.local.mediaviewer.image.ImageReaderUiState
import com.local.mediaviewer.image.ImageSortOrder
import com.local.mediaviewer.ui.components.MediaAction
import com.local.mediaviewer.ui.components.MediaStateKind
import com.local.mediaviewer.ui.components.MediaStatePanel
import com.local.mediaviewer.ui.components.MediaTopAppBar
import com.local.mediaviewer.ui.theme.MediaTheme

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
    safeDrawingInsets: WindowInsets =
        WindowInsets.safeDrawing,
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

    ReaderPlayerTheme {
        val playerColors = MediaTheme.playerColors
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(playerColors.canvas)
                .testTag("image_reader_canvas"),
        ) {
            when (state) {
                ImageReaderUiState.Loading -> {
                    ImageReaderDirectoryState(
                        kind = MediaStateKind.LOADING,
                        title = "正在加载图片…",
                        panelTag =
                            "image_reader_state_loading",
                        onBack = onBack,
                    )
                }

                ImageReaderUiState.Empty -> {
                    ImageReaderDirectoryState(
                        kind = MediaStateKind.EMPTY,
                        title = "此目录没有图片",
                        panelTag =
                            "image_reader_state_empty",
                        onBack = onBack,
                    )
                }

                is ImageReaderUiState.Error -> {
                    ImageReaderDirectoryState(
                        kind = MediaStateKind.ERROR,
                        title = "无法加载图片目录",
                        message = state.message,
                        primaryAction = MediaAction(
                            label = "重试",
                            onClick = onRetryDirectory,
                        ),
                        panelTag =
                            "image_reader_state_error",
                        onBack = onBack,
                    )
                }

                is ImageReaderUiState.Content -> {
                    if (current == null) {
                        ImageReaderDirectoryState(
                            kind = MediaStateKind.EMPTY,
                            title = "此目录没有图片",
                            panelTag =
                                "image_reader_state_empty",
                            onBack = onBack,
                        )
                    } else {
                        ImageReaderContent(
                            state = state,
                            current = current,
                            imageLoader = imageLoader,
                            comicTransform =
                                comicTransform,
                            onComicTransformChanged = {
                                comicTransform = it
                            },
                            onModeChanged =
                                onModeChanged,
                            onSortChanged =
                                onSortChanged,
                            onAnchorChanged =
                                onAnchorChanged,
                            onImageLoadError =
                                onImageLoadError,
                            onImageLoadSuccess =
                                onImageLoadSuccess,
                            onRetryImage =
                                onRetryImage,
                            onBack = onBack,
                            safeDrawingInsets =
                                safeDrawingInsets,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageReaderContent(
    state: ImageReaderUiState.Content,
    current: ImageReaderItem,
    imageLoader: ImageLoader,
    comicTransform: ComicTransform,
    onComicTransformChanged: (ComicTransform) -> Unit,
    onModeChanged: (ImageReaderMode) -> Unit,
    onSortChanged: (ImageSortOrder) -> Unit,
    onAnchorChanged: (String) -> Unit,
    onImageLoadError:
        (String, ImageLoadFailureKind) -> Unit,
    onImageLoadSuccess: (String) -> Unit,
    onRetryImage: (String) -> Unit,
    onBack: () -> Unit,
    safeDrawingInsets: WindowInsets,
) {
    // 规格 §8.4：轻触切换顶部工具栏；默认可见。
    var toolbarVisible by rememberSaveable {
        mutableStateOf(true)
    }
    val onToggleToolbar = { toolbarVisible = !toolbarVisible }
    Box(modifier = Modifier.fillMaxSize()) {
        if (state.mode == ImageReaderMode.COMIC) {
            ComicReader(
                images = state.images,
                anchorLogicalUrl =
                    state.anchorLogicalUrl,
                sortOrder = state.sortOrder,
                imageLoader = imageLoader,
                requestGeneration =
                    state.requestGeneration,
                itemFailures = state.itemFailures,
                itemRequestGenerations =
                    state.itemRequestGenerations,
                refreshingImageLogicalUrl =
                    state.refreshingImageLogicalUrl,
                transform = comicTransform,
                onTransformChanged =
                    onComicTransformChanged,
                onAnchorChanged = onAnchorChanged,
                onImageLoadError = onImageLoadError,
                onImageLoadSuccess =
                    onImageLoadSuccess,
                onRetryImage = onRetryImage,
                onToggleToolbar = onToggleToolbar,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            SingleImagePager(
                images = state.images,
                anchorLogicalUrl =
                    state.anchorLogicalUrl,
                imageLoader = imageLoader,
                requestGeneration =
                    state.requestGeneration,
                itemFailures = state.itemFailures,
                itemRequestGenerations =
                    state.itemRequestGenerations,
                refreshingImageLogicalUrl =
                    state.refreshingImageLogicalUrl,
                onAnchorChanged = onAnchorChanged,
                onImageLoadError = onImageLoadError,
                onImageLoadSuccess =
                    onImageLoadSuccess,
                onRetryImage = onRetryImage,
                onToggleToolbar = onToggleToolbar,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (toolbarVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                horizontalAlignment =
                    Alignment.CenterHorizontally,
            ) {
                val currentIndex = state.images
                    .indexOfFirst {
                        it.logicalUrl ==
                            current.logicalUrl
                    }
                    .coerceAtLeast(0)
                ImageReaderToolbar(
                    title = current.name,
                    currentIndex = currentIndex,
                    totalCount = state.images.size,
                    mode = state.mode,
                    sortOrder = state.sortOrder,
                    onModeChanged = onModeChanged,
                    onSortChanged = onSortChanged,
                    onBack = onBack,
                    safeDrawingInsets =
                        safeDrawingInsets,
                )
                if (state.isRefreshingEndpoint) {
                    EndpointRefreshChip()
                }
            }
        }
    }
}

@Composable
private fun ImageReaderDirectoryState(
    kind: MediaStateKind,
    title: String,
    panelTag: String,
    onBack: () -> Unit,
    message: String? = null,
    primaryAction: MediaAction? = null,
) {
    val playerColors = MediaTheme.playerColors
    Column(modifier = Modifier.fillMaxSize()) {
        MediaTopAppBar(
            title = "图片阅读",
            onBack = onBack,
            containerColor = playerColors.canvas,
            contentColor = playerColors.control,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            MediaStatePanel(
                kind = kind,
                title = title,
                message = message,
                primaryAction = primaryAction,
                modifier = Modifier.testTag(panelTag),
            )
        }
    }
}

@Composable
private fun EndpointRefreshChip() {
    val playerColors = MediaTheme.playerColors
    Surface(
        modifier = Modifier
            .padding(top = MediaTheme.spacing.xs)
            .semantics {
                liveRegion = LiveRegionMode.Polite
            }
            .testTag("image_reader_refresh_chip"),
        shape = MaterialTheme.shapes.extraLarge,
        color = playerColors.unplayedTrack,
        contentColor = playerColors.control,
    ) {
        Text(
            text = "正在重新连接",
            modifier = Modifier.padding(
                horizontal = MediaTheme.spacing.md,
                vertical = MediaTheme.spacing.xs,
            ),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ReaderPlayerTheme(
    content: @Composable () -> Unit,
) {
    val playerColors = MediaTheme.playerColors
    val readerColorScheme =
        MaterialTheme.colorScheme.copy(
            primary = playerColors.active,
            onPrimary = playerColors.canvas,
            background = playerColors.canvas,
            onBackground = playerColors.control,
            surface = playerColors.canvas,
            onSurface = playerColors.control,
            surfaceVariant =
                playerColors.unplayedTrack,
            onSurfaceVariant =
                playerColors.control,
            surfaceContainerLowest =
                playerColors.canvas,
            surfaceContainerLow =
                playerColors.canvas,
            surfaceContainer =
                playerColors.canvas,
            surfaceContainerHigh =
                playerColors.canvas,
            surfaceContainerHighest =
                playerColors.canvas,
        )
    MaterialTheme(
        colorScheme = readerColorScheme,
        content = content,
    )
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
