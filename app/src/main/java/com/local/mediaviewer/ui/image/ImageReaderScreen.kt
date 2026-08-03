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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.local.mediaviewer.image.ReaderControlsReducer
import com.local.mediaviewer.image.ReaderControlsState
import com.local.mediaviewer.image.ZoomTransform
import com.local.mediaviewer.settings.VideoControlsAutoHide
import com.local.mediaviewer.ui.components.MediaAction
import com.local.mediaviewer.ui.components.MediaStateKind
import com.local.mediaviewer.ui.components.MediaStatePanel
import com.local.mediaviewer.ui.components.MediaTopAppBar
import com.local.mediaviewer.ui.theme.MediaTheme
import kotlinx.coroutines.delay

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
    controlsAutoHide: VideoControlsAutoHide =
        VideoControlsAutoHide.THREE_SECONDS,
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
                            controlsAutoHide =
                                controlsAutoHide,
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
    controlsAutoHide: VideoControlsAutoHide,
    safeDrawingInsets: WindowInsets,
) {
    var controlsState by remember {
        mutableStateOf(ReaderControlsState())
    }
    var singleImageZoom by remember {
        mutableStateOf(ZoomTransform())
    }
    var zoomCommandId by rememberSaveable {
        mutableStateOf(0)
    }
    var zoomCommand by remember {
        mutableStateOf<SingleImageZoomCommand?>(null)
    }
    var progressPreviewIndex by rememberSaveable {
        mutableStateOf<Int?>(null)
    }
    var comicJumpCommandId by rememberSaveable {
        mutableStateOf(0L)
    }
    var comicJumpCommand by remember {
        mutableStateOf<ComicJumpCommand?>(null)
    }
    LaunchedEffect(
        controlsState.visible,
        controlsState.interactionActive,
        controlsState.autoHideEpoch,
        controlsAutoHide,
    ) {
        val delayMs = ReaderControlsReducer.autoHideDelayMs(
            state = controlsState,
            preference = controlsAutoHide,
        ) ?: return@LaunchedEffect
        delay(delayMs)
        controlsState = controlsState.copy(visible = false)
    }
    val onToggleToolbar = {
        controlsState = ReaderControlsReducer.toggle(
            controlsState,
        )
    }
    val currentIndex = state.images
        .indexOfFirst {
            it.logicalUrl == current.logicalUrl
        }
        .coerceAtLeast(0)
    val sendZoomCommand: (SingleImageZoomAction) -> Unit = {
            action,
        ->
        zoomCommandId += 1
        zoomCommand = SingleImageZoomCommand(
            id = zoomCommandId,
            targetLogicalUrl = current.logicalUrl,
            action = action,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .trackReaderInteraction(
                onBeginInteraction = {
                    controlsState =
                        ReaderControlsReducer.beginInteraction(
                            controlsState,
                        )
                },
                onEndInteraction = {
                    controlsState =
                        ReaderControlsReducer.endInteraction(
                            controlsState,
                        )
                },
            )
            .testTag("image_reader_root"),
    ) {
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
                jumpCommand = comicJumpCommand,
                onJumpHandled = { handledId ->
                    if (comicJumpCommand?.id == handledId) {
                        comicJumpCommand = null
                        progressPreviewIndex = null
                    }
                },
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
                zoomCommand = zoomCommand,
                onZoomCommandHandled = { handledId ->
                    if (zoomCommand?.id == handledId) {
                        zoomCommand = null
                    }
                },
                onCurrentZoomChanged = {
                    singleImageZoom = it
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (controlsState.visible) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                horizontalAlignment =
                    Alignment.CenterHorizontally,
            ) {
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
            ImageReaderOverlayControls(
                currentIndex = currentIndex,
                comicDisplayIndex =
                    progressPreviewIndex ?: currentIndex,
                totalCount = state.images.size,
                currentItemName = current.name,
                mode = state.mode,
                scale = singleImageZoom.scale,
                onPrevious = {
                    state.images
                        .getOrNull(currentIndex - 1)
                        ?.let {
                            onAnchorChanged(it.logicalUrl)
                        }
                },
                onNext = {
                    state.images
                        .getOrNull(currentIndex + 1)
                        ?.let {
                            onAnchorChanged(it.logicalUrl)
                        }
                },
                onZoomOut = {
                    sendZoomCommand(
                        SingleImageZoomAction.ZOOM_OUT,
                    )
                },
                onZoomIn = {
                    sendZoomCommand(
                        SingleImageZoomAction.ZOOM_IN,
                    )
                },
                onFitScreen = {
                    sendZoomCommand(
                        SingleImageZoomAction.FIT_SCREEN,
                    )
                },
                onComicProgressChanged = { value ->
                    progressPreviewIndex = comicProgressIndex(
                        value = value,
                        totalCount = state.images.size,
                    )
                },
                onComicProgressFinished = {
                    val targetIndex = progressPreviewIndex
                        ?: currentIndex
                    comicJumpCommandId += 1L
                    comicJumpCommand = ComicJumpCommand(
                        id = comicJumpCommandId,
                        targetIndex = targetIndex,
                    )
                },
                onModeChanged = onModeChanged,
                hasStaticImages = state.images.any {
                    !isAnimatedGifName(it.name)
                },
                hasAnimatedGifs = state.images.any {
                    isAnimatedGifName(it.name)
                },
                onSingleContentTypeSelected = {
                        animatedGif,
                    ->
                    nearestSingleImageIndex(
                        itemNames = state.images.map {
                            it.name
                        },
                        currentIndex = currentIndex,
                        animatedGif = animatedGif,
                    )?.let { targetIndex ->
                        state.images[targetIndex].let {
                            if (
                                it.logicalUrl !=
                                current.logicalUrl
                            ) {
                                onAnchorChanged(
                                    it.logicalUrl,
                                )
                            }
                        }
                    }
                    if (state.mode != ImageReaderMode.SINGLE) {
                        onModeChanged(ImageReaderMode.SINGLE)
                    }
                },
                safeDrawingInsets = safeDrawingInsets,
            )
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
