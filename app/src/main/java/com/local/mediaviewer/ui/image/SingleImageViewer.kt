package com.local.mediaviewer.ui.image

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.local.mediaviewer.image.ImageDecodePolicy
import com.local.mediaviewer.image.ImageItemFailure
import com.local.mediaviewer.image.ImageLoadFailureKind
import com.local.mediaviewer.image.ImageReaderItem
import com.local.mediaviewer.image.MediaImageLoaderFactory
import com.local.mediaviewer.image.ZoomReducer
import com.local.mediaviewer.image.ZoomTransform
import com.local.mediaviewer.image.classifyImageLoadFailure

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
    refreshingImageLogicalUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    var zoom by remember(item.logicalUrl) {
        mutableStateOf(ZoomTransform())
    }
    val currentOnToggleToolbar by
        rememberUpdatedState(onToggleToolbar)
    val context = LocalContext.current
    val deviceBitmapLimits = remember {
        queryDeviceBitmapLimits()
    }

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .background(Color.Black)
            .testTag("media_image"),
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
        val decodeSize = remember(
            viewportWidthPx,
            viewportHeightPx,
            deviceBitmapLimits,
            zoom.scale,
        ) {
            ImageDecodePolicy.target(
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
                scale = zoom.scale,
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
                .pointerInput(item.logicalUrl) {
                    detectTransformGestures {
                            _,
                            pan,
                            gestureZoom,
                            _,
                        ->
                        zoom = ZoomReducer.gesture(
                            current = zoom,
                            zoomChange = gestureZoom,
                            panChange = pan,
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
                    onImageLoadSuccess(
                        item.logicalUrl,
                    )
                }
                SubcomposeAsyncImageContent()
            },
        )
    }
}
