package com.local.mediaviewer.ui.image

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.local.mediaviewer.image.ImageItemFailure
import com.local.mediaviewer.image.ImageLoadFailureKind
import com.local.mediaviewer.image.ImageReaderItem
import com.local.mediaviewer.image.MediaImageLoaderFactory
import com.local.mediaviewer.image.ZoomReducer
import com.local.mediaviewer.image.ZoomTransform
import com.local.mediaviewer.image.classifyImageLoadFailure
import com.local.mediaviewer.ui.theme.MediaTheme

enum class SingleImageZoomAction {
    ZOOM_OUT,
    ZOOM_IN,
    FIT_SCREEN,
}

data class SingleImageZoomCommand(
    val id: Int,
    val targetLogicalUrl: String,
    val action: SingleImageZoomAction,
)

val SingleImageScaleSemanticsKey =
    SemanticsPropertyKey<Float>("SingleImageScale")
val SingleImageOffsetXSemanticsKey =
    SemanticsPropertyKey<Float>("SingleImageOffsetX")
val SingleImageOffsetYSemanticsKey =
    SemanticsPropertyKey<Float>("SingleImageOffsetY")

@Composable
fun SingleImageViewer(
    item: ImageReaderItem,
    imageLoader: ImageLoader,
    requestGeneration: Int,
    failure: ImageItemFailure?,
    onImageLoadError:
        (String, ImageLoadFailureKind) -> Unit,
    onImageLoadSuccess: (String) -> Unit,
    onRetryImage: (String) -> Unit,
    onToggleToolbar: () -> Unit = {},
    onZoomedChanged: (Boolean) -> Unit = {},
    onZoomChanged: (ZoomTransform) -> Unit = {},
    zoomCommand: SingleImageZoomCommand? = null,
    onZoomCommandHandled: (Int) -> Unit = {},
    refreshingImageLogicalUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    var zoom by remember(item.logicalUrl) {
        mutableStateOf(ZoomTransform())
    }
    var intrinsicSize by remember(item.logicalUrl) {
        mutableStateOf<Size?>(null)
    }
    val currentOnToggleToolbar by
        rememberUpdatedState(onToggleToolbar)
    val currentOnZoomedChanged by
        rememberUpdatedState(onZoomedChanged)
    val currentOnZoomChanged by
        rememberUpdatedState(onZoomChanged)
    val currentOnZoomCommandHandled by
        rememberUpdatedState(onZoomCommandHandled)
    val isZoomed = zoom.scale > 1.001f
    LaunchedEffect(item.logicalUrl, zoom) {
        currentOnZoomedChanged(isZoomed)
        currentOnZoomChanged(zoom)
    }
    val context = LocalContext.current
    val playerColors = MediaTheme.playerColors
    val deviceBitmapLimits = remember {
        queryDeviceBitmapLimits()
    }
    val animatedGif = remember(item.name) {
        isAnimatedGifName(item.name)
    }

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .background(playerColors.canvas)
            .testTag("media_image")
            .semantics {
                this[SingleImageScaleSemanticsKey] = zoom.scale
                this[SingleImageOffsetXSemanticsKey] = zoom.offset.x
                this[SingleImageOffsetYSemanticsKey] = zoom.offset.y
            },
    ) {
        if (failure != null) {
            ImageItemErrorPanel(
                item = item,
                failure = failure,
                onRetry = {
                    onRetryImage(item.logicalUrl)
                },
                isRefreshing =
                    refreshingImageLogicalUrl ==
                        item.logicalUrl,
                modifier = Modifier.fillMaxSize(),
            )
            return@BoxWithConstraints
        }
        val viewportWidthPx =
            constraints.maxWidth.coerceAtLeast(1)
        val viewportHeightPx =
            constraints.maxHeight.coerceAtLeast(1)
        val viewportSize = Size(
            width = viewportWidthPx.toFloat(),
            height = viewportHeightPx.toFloat(),
        )
        val fittedContentSize = remember(
            viewportSize,
            intrinsicSize,
        ) {
            fittedContentSize(
                viewportSize = viewportSize,
                intrinsicSize = intrinsicSize,
            )
        }
        LaunchedEffect(viewportSize, fittedContentSize) {
            zoom = ZoomReducer.clamp(
                current = zoom,
                viewportSize = viewportSize,
                fittedContentSize = fittedContentSize,
            )
        }
        LaunchedEffect(
            item.logicalUrl,
            zoomCommand?.id,
            viewportSize,
            fittedContentSize,
        ) {
            val command = zoomCommand
            if (command?.targetLogicalUrl == item.logicalUrl) {
                zoom = when (command.action) {
                    SingleImageZoomAction.ZOOM_OUT ->
                        ZoomReducer.gesture(
                            current = zoom,
                            zoomChange = 0.8f,
                            panChange = Offset.Zero,
                            centroid = Offset(
                                viewportSize.width / 2f,
                                viewportSize.height / 2f,
                            ),
                            viewportSize = viewportSize,
                            fittedContentSize = fittedContentSize,
                        )

                    SingleImageZoomAction.ZOOM_IN ->
                        ZoomReducer.gesture(
                            current = zoom,
                            zoomChange = 1.25f,
                            panChange = Offset.Zero,
                            centroid = Offset(
                                viewportSize.width / 2f,
                                viewportSize.height / 2f,
                            ),
                            viewportSize = viewportSize,
                            fittedContentSize = fittedContentSize,
                        )

                    SingleImageZoomAction.FIT_SCREEN ->
                        ZoomReducer.reset()
                }
                currentOnZoomCommandHandled(command.id)
            }
        }
        val decodeScale = if (animatedGif) {
            1f
        } else {
            zoom.scale
        }
        val decodeSize = remember(
            viewportWidthPx,
            viewportHeightPx,
            deviceBitmapLimits,
            decodeScale,
            animatedGif,
        ) {
            SingleImageDecodePolicy.target(
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
                scale = decodeScale,
                animatedGif = animatedGif,
                maxBitmapWidthPx =
                    deviceBitmapLimits.maxWidthPx,
                maxBitmapHeightPx =
                    deviceBitmapLimits.maxHeightPx,
            )
        }
        val request = remember(
            context,
            item.requestUrl,
            decodeSize,
            requestGeneration,
        ) {
            MediaImageLoaderFactory.createRequest(
                context = context,
                url = item.requestUrl,
                decodeSize = decodeSize,
                requestGeneration = requestGeneration,
            )
        }

        SubcomposeAsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(
                    item.logicalUrl,
                    viewportSize,
                    fittedContentSize,
                ) {
                    awaitEachGesture {
                        var sawMultiTouch = false
                        var singlePanPastSlop = false
                        var accumulatedSinglePan = Offset.Zero
                        awaitFirstDown(
                            requireUnconsumed = false,
                        )
                        do {
                            val event = awaitPointerEvent()
                            if (
                                event.changes.count {
                                    it.pressed
                                } >= 2
                            ) {
                                sawMultiTouch = true
                                zoom = ZoomReducer.gesture(
                                    current = zoom,
                                    zoomChange =
                                        event.calculateZoom(),
                                    panChange =
                                        event.calculatePan(),
                                    centroid =
                                        event.calculateCentroid(
                                            useCurrent = false,
                                        ),
                                    viewportSize = viewportSize,
                                    fittedContentSize =
                                        fittedContentSize,
                                )
                                event.changes.forEach {
                                    it.consume()
                                }
                            } else if (
                                event.changes.count {
                                    it.pressed
                                } == 1 &&
                                !sawMultiTouch &&
                                zoom.scale > 1.001f
                            ) {
                                val eventPan =
                                    event.calculatePan()
                                val panToApply = if (
                                    singlePanPastSlop
                                ) {
                                    eventPan
                                } else {
                                    accumulatedSinglePan +=
                                        eventPan
                                    if (
                                        accumulatedSinglePan
                                            .getDistance() >
                                        viewConfiguration
                                            .touchSlop
                                    ) {
                                        singlePanPastSlop = true
                                        accumulatedSinglePan
                                    } else {
                                        Offset.Zero
                                    }
                                }
                                if (panToApply != Offset.Zero) {
                                    zoom = ZoomReducer.gesture(
                                        current = zoom,
                                        zoomChange = 1f,
                                        panChange = panToApply,
                                        centroid =
                                            event.calculateCentroid(
                                                useCurrent = false,
                                            ),
                                        viewportSize = viewportSize,
                                        fittedContentSize =
                                            fittedContentSize,
                                    )
                                    accumulatedSinglePan = Offset.Zero
                                    event.changes.forEach {
                                        it.consume()
                                    }
                                }
                            }
                        } while (
                            event.changes.any { it.pressed }
                        )
                    }
                }
                .pointerInput(item.logicalUrl) {
                    detectTapGestures(
                        onDoubleTap = {
                            zoom = ZoomReducer.reset()
                        },
                        onTap = {
                            currentOnToggleToolbar()
                        },
                    )
                }
                .graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offset.x
                    translationY = zoom.offset.y
                },
            loading = {
                ImageItemLoadingPanel(
                    modifier = Modifier.fillMaxSize(),
                )
            },
            error = { state ->
                LaunchedEffect(state.result) {
                    onImageLoadError(
                        item.logicalUrl,
                        classifyImageLoadFailure(
                            state.result.throwable,
                        ),
                    )
                }
                ImageItemLoadingPanel(
                    modifier = Modifier.fillMaxSize(),
                    errorMessage = "图片加载失败",
                )
            },
            success = { state ->
                LaunchedEffect(
                    item.logicalUrl,
                    requestGeneration,
                    state.result,
                ) {
                    val image = state.result.image
                    intrinsicSize = if (
                        image.width > 0 && image.height > 0
                    ) {
                        Size(
                            width = image.width.toFloat(),
                            height = image.height.toFloat(),
                        )
                    } else {
                        null
                    }
                    onImageLoadSuccess(
                        item.logicalUrl,
                    )
                }
                SubcomposeAsyncImageContent()
            },
        )
    }
}

private fun fittedContentSize(
    viewportSize: Size,
    intrinsicSize: Size?,
): Size {
    val intrinsic = intrinsicSize ?: return viewportSize
    if (
        intrinsic.width <= 0f ||
        intrinsic.height <= 0f ||
        !intrinsic.width.isFinite() ||
        !intrinsic.height.isFinite()
    ) {
        return viewportSize
    }
    val fitScale = minOf(
        viewportSize.width / intrinsic.width,
        viewportSize.height / intrinsic.height,
    )
    return Size(
        width = intrinsic.width * fitScale,
        height = intrinsic.height * fitScale,
    )
}
