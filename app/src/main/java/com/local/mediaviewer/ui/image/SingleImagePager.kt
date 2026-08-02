package com.local.mediaviewer.ui.image

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import coil3.ImageLoader
import com.local.mediaviewer.image.ImageItemFailure
import com.local.mediaviewer.image.ImageLoadFailureKind
import com.local.mediaviewer.image.ImageReaderItem
import com.local.mediaviewer.image.ImageSequence
import com.local.mediaviewer.image.ZoomTransform
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun SingleImagePager(
    images: List<ImageReaderItem>,
    anchorLogicalUrl: String,
    imageLoader: ImageLoader,
    requestGeneration: Int,
    itemFailures: Map<String, ImageItemFailure>,
    itemRequestGenerations: Map<String, Int>,
    refreshingImageLogicalUrl: String?,
    onAnchorChanged: (String) -> Unit,
    onImageLoadError:
        (String, ImageLoadFailureKind) -> Unit,
    onImageLoadSuccess: (String) -> Unit,
    onRetryImage: (String) -> Unit,
    onToggleToolbar: () -> Unit,
    zoomCommand: SingleImageZoomCommand? = null,
    onZoomCommandHandled: (Int) -> Unit = {},
    onCurrentZoomChanged: (ZoomTransform) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (images.isEmpty()) {
        return
    }

    val imageKeys = remember(images) {
        images.map(ImageReaderItem::logicalUrl)
    }
    val initialPage = remember(imageKeys, anchorLogicalUrl) {
        ImageSequence.indexOfAnchor(
            items = images,
            requestedLogicalUrl = anchorLogicalUrl,
        )
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = images::size,
    )
    val zoomTransforms = remember {
        mutableStateMapOf<String, ZoomTransform>()
    }
    val currentOnAnchorChanged by
        rememberUpdatedState(onAnchorChanged)
    val currentItem = images.getOrNull(
        pagerState.currentPage,
    )
    val currentTransform = currentItem?.let {
        zoomTransforms[it.logicalUrl]
    } ?: ZoomTransform()
    val currentPageZoomed =
        currentTransform.scale > 1.001f

    LaunchedEffect(
        currentItem?.logicalUrl,
        currentTransform,
    ) {
        onCurrentZoomChanged(currentTransform)
    }

    LaunchedEffect(pagerState, imageKeys) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                images.getOrNull(page)?.let { item ->
                    currentOnAnchorChanged(
                        item.logicalUrl,
                    )
                }
            }
    }
    LaunchedEffect(imageKeys, anchorLogicalUrl) {
        val targetPage = ImageSequence.indexOfAnchor(
            items = images,
            requestedLogicalUrl = anchorLogicalUrl,
        )
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    HorizontalPager(
        state = pagerState,
        key = { page -> images[page].logicalUrl },
        userScrollEnabled = !currentPageZoomed,
        modifier = modifier.testTag("single_image_pager"),
    ) { page ->
        val item = images[page]
        SingleImageViewer(
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
            failure = itemFailures[item.logicalUrl],
            onImageLoadError = onImageLoadError,
            onImageLoadSuccess = onImageLoadSuccess,
            onRetryImage = onRetryImage,
            onToggleToolbar = onToggleToolbar,
            onZoomedChanged = { zoomed ->
                if (!zoomed) {
                    zoomTransforms[item.logicalUrl] =
                        ZoomTransform()
                }
            },
            onZoomChanged = { transform ->
                zoomTransforms[item.logicalUrl] = transform
                if (page == pagerState.currentPage) {
                    onCurrentZoomChanged(transform)
                }
            },
            zoomCommand = zoomCommand,
            onZoomCommandHandled =
                onZoomCommandHandled,
            refreshingImageLogicalUrl =
                refreshingImageLogicalUrl,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
