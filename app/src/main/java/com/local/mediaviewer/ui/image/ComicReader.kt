package com.local.mediaviewer.ui.image

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.local.mediaviewer.image.ComicTransform
import com.local.mediaviewer.image.ComicTransformReducer
import com.local.mediaviewer.image.ImageDecodePolicy
import com.local.mediaviewer.image.ImageItemFailure
import com.local.mediaviewer.image.ImageLoadFailureKind
import com.local.mediaviewer.image.ImageReaderItem
import com.local.mediaviewer.image.ImageSortOrder
import com.local.mediaviewer.image.MediaImageLoaderFactory
import com.local.mediaviewer.image.classifyImageLoadFailure
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

val ComicItemIndexSemanticsKey =
    SemanticsPropertyKey<Int>("ComicItemIndex")
val ComicScaleSemanticsKey =
    SemanticsPropertyKey<Float>("ComicScale")
val ComicHorizontalOffsetSemanticsKey =
    SemanticsPropertyKey<Float>(
        "ComicHorizontalOffset",
    )

@Composable
fun ComicReader(
    images: List<ImageReaderItem>,
    anchorLogicalUrl: String,
    sortOrder: ImageSortOrder,
    imageLoader: ImageLoader,
    requestGeneration: Int,
    itemFailures: Map<String, ImageItemFailure>,
    itemRequestGenerations: Map<String, Int>,
    transform: ComicTransform,
    onTransformChanged: (ComicTransform) -> Unit,
    onAnchorChanged: (String) -> Unit,
    onImageLoadError:
        (String, ImageLoadFailureKind) -> Unit,
    onImageLoadSuccess: (String) -> Unit,
    onRetryImage: (String) -> Unit,
    refreshingImageLogicalUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val deviceBitmapLimits = remember {
        queryDeviceBitmapLimits()
    }
    val initialAnchorIndex =
        images.indexOfFirst {
            it.logicalUrl == anchorLogicalUrl
        }.coerceAtLeast(0)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex =
            initialAnchorIndex,
    )
    val currentTransform = remember {
        mutableStateOf(transform)
    }
    SideEffect {
        currentTransform.value = transform
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val viewportWidthPx =
            constraints.maxWidth.coerceAtLeast(1)
        val viewportHeightPx =
            constraints.maxHeight.coerceAtLeast(1)
        val density = LocalDensity.current
        val itemWidth = with(density) {
            (
                viewportWidthPx *
                    transform.scale
            ).toDp()
        }
        val dragState = rememberDraggableState { delta ->
            val updated = ComicTransformReducer.gesture(
                current = currentTransform.value,
                zoomChange = 1f,
                panXPx = delta,
                viewportWidthPx =
                    viewportWidthPx.toFloat(),
            )
            currentTransform.value = updated
            onTransformChanged(updated)
        }

        LaunchedEffect(viewportWidthPx) {
            val updated = ComicTransformReducer.clamp(
                current = currentTransform.value,
                viewportWidthPx =
                    viewportWidthPx.toFloat(),
            )
            currentTransform.value = updated
            onTransformChanged(updated)
        }
        LaunchedEffect(images, sortOrder) {
            val anchorIndex =
                images.indexOfFirst {
                    it.logicalUrl == anchorLogicalUrl
                }.coerceAtLeast(0)
            listState.scrollToItem(anchorIndex)
        }
        LaunchedEffect(listState, images) {
            snapshotFlow {
                if (listState.isScrollInProgress) {
                    mostVisibleLogicalUrl(
                        listState.layoutInfo,
                    )
                } else {
                    null
                }
            }
                .filterNotNull()
                .distinctUntilChanged()
                .collect(onAnchorChanged)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("comic_reader")
                .comicTransformGestures(
                    onDoubleTap = {
                        val reset =
                            ComicTransformReducer.reset()
                        currentTransform.value = reset
                        onTransformChanged(reset)
                    },
                    onGesture = {
                        zoomChange,
                        panXPx,
                    ->
                        val updated =
                            ComicTransformReducer.gesture(
                            current =
                                currentTransform.value,
                            zoomChange = zoomChange,
                            panXPx = panXPx,
                            viewportWidthPx =
                                viewportWidthPx
                                    .toFloat(),
                        )
                        currentTransform.value = updated
                        onTransformChanged(updated)
                    },
                )
                .draggable(
                    state = dragState,
                    orientation =
                        Orientation.Horizontal,
                    enabled = transform.scale > 1f,
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
        ) {
            itemsIndexed(
                items = images,
                key = { _, item ->
                    item.logicalUrl
                },
                contentType = { _, _ -> "image" },
            ) { index, item ->
                Box(
                    modifier = Modifier
                        .requiredWidth(itemWidth)
                        .offset {
                            IntOffset(
                                x =
                                    transform
                                        .horizontalOffsetPx
                                        .roundToInt(),
                                y = 0,
                            )
                        }
                        .testTag(
                            "comic_item:${item.name}",
                        )
                        .semantics {
                            this[
                                ComicItemIndexSemanticsKey
                            ] = index
                            this[
                                ComicScaleSemanticsKey
                            ] = transform.scale
                            this[
                                ComicHorizontalOffsetSemanticsKey
                            ] =
                                transform
                                    .horizontalOffsetPx
                        },
                ) {
                    ComicImage(
                        item = item,
                        imageLoader = imageLoader,
                        requestGeneration =
                            effectiveRequestGeneration(
                                requestGeneration =
                                    requestGeneration,
                                itemRequestGeneration =
                                    itemRequestGenerations[
                                        item.logicalUrl
                                    ] ?: 0,
                            ),
                        failure =
                            itemFailures[item.logicalUrl],
                        viewportWidthPx =
                            viewportWidthPx,
                        viewportHeightPx =
                            viewportHeightPx,
                        deviceBitmapLimits =
                            deviceBitmapLimits,
                        visualScale = transform.scale,
                        onImageLoadError =
                            onImageLoadError,
                        onImageLoadSuccess =
                            onImageLoadSuccess,
                        onRetryImage = onRetryImage,
                        isRefreshing =
                            refreshingImageLogicalUrl ==
                                item.logicalUrl,
                        modifier =
                            Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ComicImage(
    item: ImageReaderItem,
    imageLoader: ImageLoader,
    requestGeneration: Int,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    deviceBitmapLimits: DeviceBitmapLimits,
    visualScale: Float,
    failure: ImageItemFailure?,
    onImageLoadError:
        (String, ImageLoadFailureKind) -> Unit,
    onImageLoadSuccess: (String) -> Unit,
    onRetryImage: (String) -> Unit,
    isRefreshing: Boolean,
    modifier: Modifier,
) {
    if (failure != null) {
        ImageItemErrorPanel(
            item = item,
            failure = failure,
            onRetry = {
                onRetryImage(item.logicalUrl)
            },
            isRefreshing = isRefreshing,
            modifier = modifier,
        )
        return
    }
    var hasIntrinsicSize by remember(
        item.logicalUrl,
        requestGeneration,
    ) {
        mutableStateOf(false)
    }
    val context = LocalContext.current
    val decodeSize = remember(
        viewportWidthPx,
        viewportHeightPx,
        deviceBitmapLimits,
        visualScale,
    ) {
        ImageDecodePolicy.target(
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            scale = visualScale,
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
        contentScale = ContentScale.FillWidth,
        modifier = modifier
            .then(
                if (hasIntrinsicSize) {
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                },
            )
            .testTag(
                "comic_image:${item.name}",
            ),
        loading = {
            ComicPlaceholder()
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
            ComicPlaceholder("图片加载失败")
        },
        success = { state ->
            LaunchedEffect(
                item.logicalUrl,
                requestGeneration,
                state.result,
            ) {
                hasIntrinsicSize = true
                onImageLoadSuccess(
                    item.logicalUrl,
                )
            }
            SubcomposeAsyncImageContent()
        },
    )
}

@Composable
private fun ComicPlaceholder(
    message: String? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (message == null) {
            CircularProgressIndicator()
        } else {
            Text(
                text = message,
                color = Color.White,
            )
        }
    }
}

internal data class VisibleImageBounds(
    val logicalUrl: String,
    val offsetPx: Int,
    val sizePx: Int,
)

internal fun mostVisibleLogicalUrl(
    items: List<VisibleImageBounds>,
    viewportStartPx: Int,
    viewportEndPx: Int,
): String? =
    items
        .map { item ->
            val visibleStart =
                maxOf(item.offsetPx, viewportStartPx)
            val visibleEnd =
                minOf(
                    item.offsetPx + item.sizePx,
                    viewportEndPx,
                )
            item to
                (visibleEnd - visibleStart)
                    .coerceAtLeast(0)
        }
        .filter { (_, visiblePx) ->
            visiblePx > 0
        }
        .maxByOrNull { (_, visiblePx) ->
            visiblePx
        }
        ?.first
        ?.logicalUrl

internal fun mostVisibleLogicalUrl(
    layoutInfo: LazyListLayoutInfo,
): String? =
    mostVisibleLogicalUrl(
        items =
            layoutInfo.visibleItemsInfo.mapNotNull {
                item ->
                val logicalUrl =
                    item.key as? String
                        ?: return@mapNotNull null
                VisibleImageBounds(
                    logicalUrl = logicalUrl,
                    offsetPx = item.offset,
                    sizePx = item.size,
                )
            },
        viewportStartPx =
            layoutInfo.viewportStartOffset,
        viewportEndPx =
            layoutInfo.viewportEndOffset,
    )

internal fun effectiveRequestGeneration(
    requestGeneration: Int,
    itemRequestGeneration: Int,
): Int =
    requestGeneration * REQUEST_GENERATION_FACTOR +
        itemRequestGeneration

private const val REQUEST_GENERATION_FACTOR = 1_000_000
