package com.local.mediaviewer.ui.image

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.local.mediaviewer.image.ComicVisibleItem
import com.local.mediaviewer.image.ComicViewportAnchor
import com.local.mediaviewer.image.ImageDecodePolicy
import com.local.mediaviewer.image.ImageItemFailure
import com.local.mediaviewer.image.ImageLoadFailureKind
import com.local.mediaviewer.image.ImageReaderItem
import com.local.mediaviewer.image.ImageSortOrder
import com.local.mediaviewer.image.MediaImageLoaderFactory
import com.local.mediaviewer.image.classifyImageLoadFailure
import com.local.mediaviewer.image.captureComicViewportAnchor
import com.local.mediaviewer.image.comicScrollCorrectionPx
import com.local.mediaviewer.ui.theme.MediaTheme
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
val ComicViewportAnchorErrorSemanticsKey =
    SemanticsPropertyKey<Float>(
        "ComicViewportAnchorError",
    )
val ComicViewportAnchorTargetYSemanticsKey =
    SemanticsPropertyKey<Float>(
        "ComicViewportAnchorTargetY",
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
    jumpCommand: ComicJumpCommand? = null,
    onJumpHandled: (Long) -> Unit = {},
    onToggleToolbar: () -> Unit = {},
    refreshingImageLogicalUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val playerColors = MediaTheme.playerColors
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
    var pendingViewportAnchor by remember {
        mutableStateOf<PendingComicViewportAnchor?>(null)
    }
    var viewportAnchorGeneration by remember {
        mutableStateOf(0)
    }
    var viewportAnchorErrorPx by remember {
        mutableStateOf<Float?>(null)
    }
    var viewportAnchorTargetYPx by remember {
        mutableStateOf<Float?>(null)
    }
    SideEffect {
        currentTransform.value = transform
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(playerColors.canvas),
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
                centroidXPx =
                    viewportWidthPx.toFloat() / 2f,
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
        LaunchedEffect(jumpCommand?.id) {
            val command = jumpCommand
                ?: return@LaunchedEffect
            if (images.isEmpty()) {
                onJumpHandled(command.id)
                return@LaunchedEffect
            }
            val target = command.targetIndex
                .coerceIn(images.indices)
            listState.scrollToItem(target)
            onAnchorChanged(images[target].logicalUrl)
            onJumpHandled(command.id)
        }
        LaunchedEffect(pendingViewportAnchor) {
            val pending =
                pendingViewportAnchor
                    ?: return@LaunchedEffect
            withFrameNanos { }
            val updatedInfo =
                listState.layoutInfo.visibleItemsInfo
                    .firstOrNull {
                        it.index == pending.anchor.itemIndex
                    }
            if (updatedInfo != null) {
                listState.scrollBy(
                    comicScrollCorrectionPx(
                        anchor = pending.anchor,
                        updatedItem = ComicVisibleItem(
                            index = updatedInfo.index,
                            offsetPx = updatedInfo.offset,
                            sizePx = updatedInfo.size,
                        ),
                    ),
                )
                withFrameNanos { }
                val correctedInfo =
                    listState.layoutInfo.visibleItemsInfo
                        .firstOrNull {
                            it.index == pending.anchor.itemIndex
                        }
                viewportAnchorErrorPx =
                    correctedInfo?.let {
                        comicScrollCorrectionPx(
                            anchor = pending.anchor,
                            updatedItem = ComicVisibleItem(
                                index = it.index,
                                offsetPx = it.offset,
                                sizePx = it.size,
                            ),
                        )
                    }
            }
            if (
                pendingViewportAnchor?.generation ==
                pending.generation
            ) {
                pendingViewportAnchor = null
            }
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
                .semantics {
                    viewportAnchorErrorPx?.let { error ->
                        this[
                            ComicViewportAnchorErrorSemanticsKey
                        ] = error
                    }
                    viewportAnchorTargetYPx?.let { targetY ->
                        this[
                            ComicViewportAnchorTargetYSemanticsKey
                        ] = targetY
                    }
                }
                .comicTransformGestures(
                    onDoubleTap = {
                        val reset =
                            ComicTransformReducer.reset()
                        currentTransform.value = reset
                        onTransformChanged(reset)
                    },
                    onTap = onToggleToolbar,
                    onGesture = {
                        centroid,
                        zoomChange,
                        panChange,
                    ->
                        val previous = currentTransform.value
                        val anchor =
                            captureComicViewportAnchor(
                                items =
                                    listState.layoutInfo
                                        .visibleItemsInfo
                                        .map { info ->
                                            ComicVisibleItem(
                                                index = info.index,
                                                offsetPx = info.offset,
                                                sizePx = info.size,
                                            )
                                        },
                                centroidYPx = centroid.y,
                            )
                        val updated =
                            ComicTransformReducer.gesture(
                            current =
                                previous,
                            zoomChange = zoomChange,
                            panXPx = panChange.x,
                            centroidXPx = centroid.x,
                            viewportWidthPx =
                                viewportWidthPx
                                    .toFloat(),
                        )
                        currentTransform.value = updated
                        onTransformChanged(updated)
                        if (
                            anchor != null &&
                            updated.scale !=
                            previous.scale
                        ) {
                            viewportAnchorGeneration += 1
                            viewportAnchorErrorPx = null
                            val adjustedAnchor = anchor.copy(
                                centroidYPx =
                                    anchor.centroidYPx +
                                        panChange.y,
                            )
                            viewportAnchorTargetYPx =
                                adjustedAnchor.centroidYPx
                            pendingViewportAnchor =
                                PendingComicViewportAnchor(
                                    generation =
                                        viewportAnchorGeneration,
                                    anchor = adjustedAnchor,
                                )
                        }
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

private data class PendingComicViewportAnchor(
    val generation: Int,
    val anchor: ComicViewportAnchor,
)

@Composable
private fun ComicImage(
    item: ImageReaderItem,
    imageLoader: ImageLoader,
    requestGeneration: Int,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    deviceBitmapLimits: DeviceBitmapLimits,
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
    ) {
        ImageDecodePolicy.comicTarget(
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
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
    ImageItemLoadingPanel(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        errorMessage = message,
    )
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
