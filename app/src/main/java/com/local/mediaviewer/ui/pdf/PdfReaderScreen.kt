package com.local.mediaviewer.ui.pdf

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.pdf.PdfLoadPhase
import com.local.mediaviewer.pdf.PdfPageSize
import com.local.mediaviewer.pdf.PdfPageUiState
import com.local.mediaviewer.pdf.PdfReaderUiState
import com.local.mediaviewer.ui.components.MediaIconButton
import com.local.mediaviewer.ui.icons.MediaIcons
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

@Composable
fun PdfReaderScreen(
    state: PdfReaderUiState,
    onViewportChanged: (Int, Set<Int>, Int, Float) -> Unit,
    onRetryDocument: () -> Unit,
    onRetryPage: (Int) -> Unit,
    onBack: () -> Unit,
    safeDrawingInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111111))
            .testTag("pdf_reader_root"),
    ) {
        when (state) {
            is PdfReaderUiState.Loading -> PdfLoadingState(state)
            is PdfReaderUiState.Error -> PdfDocumentErrorState(
                state = state,
                onRetryDocument = onRetryDocument,
            )

            is PdfReaderUiState.Content -> PdfContent(
                state = state,
                onViewportChanged = onViewportChanged,
                onRetryPage = onRetryPage,
                onBack = onBack,
                safeDrawingInsets = safeDrawingInsets,
            )
        }
    }
}

@Composable
private fun PdfLoadingState(state: PdfReaderUiState.Loading) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("pdf_reader_loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            text = when (state.phase) {
                PdfLoadPhase.DOWNLOADING -> "正在下载 PDF…"
                PdfLoadPhase.OPENING -> "正在打开 PDF…"
            },
            modifier = Modifier.padding(top = 16.dp),
            color = Color.White,
        )
    }
}

@Composable
private fun PdfDocumentErrorState(
    state: PdfReaderUiState.Error,
    onRetryDocument: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("pdf_reader_error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = state.message,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = onRetryDocument,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("重试")
        }
    }
}

@Composable
private fun PdfContent(
    state: PdfReaderUiState.Content,
    onViewportChanged: (Int, Set<Int>, Int, Float) -> Unit,
    onRetryPage: (Int) -> Unit,
    onBack: () -> Unit,
    safeDrawingInsets: WindowInsets,
) {
    val listState = rememberLazyListState()
    var transform by remember { mutableStateOf(PdfTransform()) }
    var toolbarVisible by remember { mutableStateOf(true) }
    var zoomAnchor by remember { mutableStateOf<ZoomAnchor?>(null) }
    var anchorGeneration by remember { mutableStateOf(0L) }
    val currentOnViewportChanged by
        rememberUpdatedState(onViewportChanged)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportWidthPx = constraints.maxWidth.coerceAtLeast(1)
        val density = LocalDensity.current
        val pageWidth = with(density) {
            (viewportWidthPx * transform.scale).toDp()
        }
        val interactionSource = remember {
            MutableInteractionSource()
        }

        LaunchedEffect(
            listState,
            state.pageSizes,
            viewportWidthPx,
        ) {
            snapshotFlow {
                viewportSnapshot(
                    listState = listState,
                    pageSizes = state.pageSizes,
                    viewportWidthPx = viewportWidthPx,
                    scale = transform.scale,
                )
            }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { snapshot ->
                    currentOnViewportChanged(
                        snapshot.pageIndex,
                        snapshot.visiblePages,
                        snapshot.viewportWidthPx,
                        snapshot.scale,
                    )
                }
        }

        LaunchedEffect(zoomAnchor) {
            val anchor = zoomAnchor ?: return@LaunchedEffect
            withFrameNanos { }
            val item = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { info ->
                    state.pageSizes.getOrNull(info.index)
                        ?.pageIndex == anchor.pageIndex
                }
            if (item != null) {
                val anchoredContentY =
                    item.offset + item.size * anchor.pageRatio
                listState.scrollBy(
                    anchoredContentY - anchor.centroidYPx,
                )
            }
            if (zoomAnchor?.generation == anchor.generation) {
                zoomAnchor = null
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("pdf_reader_list")
                .pdfTransformGestures {
                    centroid,
                    pan,
                    zoom,
                ->
                    val current = transform
                    val gestureCentroid = centroid - pan
                    val updated = reducePdfScreenGesture(
                        current = current,
                        zoomChange = zoom,
                        panXPx = pan.x,
                        currentCentroidXPx = centroid.x,
                        viewportWidthPx = viewportWidthPx.toFloat(),
                    )
                    if (updated.scale != current.scale) {
                        listState.pageAt(
                            pageSizes = state.pageSizes,
                            viewportYPx = gestureCentroid.y,
                        )?.let { (item, pageIndex) ->
                            anchorGeneration += 1L
                            zoomAnchor = ZoomAnchor(
                                pageIndex = pageIndex,
                                pageRatio = (
                                    (gestureCentroid.y - item.offset) /
                                        item.size.toFloat()
                                    ).coerceIn(0f, 1f),
                                centroidYPx = gestureCentroid.y,
                                generation = anchorGeneration,
                            )
                        }
                    }
                    transform = updated
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    toolbarVisible = !toolbarVisible
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = state.pageSizes,
                key = { pageSize -> pageSize.pageIndex },
                contentType = { "pdf_page" },
            ) { pageSize ->
                PdfPage(
                    pageSize = pageSize,
                    pageState = state.pages[pageSize.pageIndex],
                    pageWidth = pageWidth,
                    horizontalOffsetPx =
                        transform.horizontalOffsetPx,
                    onRetryPage = onRetryPage,
                )
            }
        }

        if (toolbarVisible) {
            PdfToolbar(
                fileName = state.fileName,
                currentPageIndex = state.currentPageIndex,
                totalPages = state.pageSizes.size,
                onBack = onBack,
                safeDrawingInsets = safeDrawingInsets,
            )
        }
    }
}

@Composable
private fun PdfPage(
    pageSize: PdfPageSize,
    pageState: PdfPageUiState?,
    pageWidth: androidx.compose.ui.unit.Dp,
    horizontalOffsetPx: Float,
    onRetryPage: (Int) -> Unit,
) {
    val ratio = if (
        pageSize.widthPoints > 0 &&
        pageSize.heightPoints > 0
    ) {
        pageSize.widthPoints.toFloat() /
            pageSize.heightPoints.toFloat()
    } else {
        DEFAULT_PAGE_RATIO
    }

    Box(
        modifier = Modifier
            .requiredWidth(pageWidth)
            .aspectRatio(ratio)
            .offset {
                IntOffset(
                    horizontalOffsetPx.roundToInt(),
                    0,
                )
            }
            .background(Color(0xFFE6E6E6))
            .testTag("pdf_page_${pageSize.pageIndex}"),
        contentAlignment = Alignment.Center,
    ) {
        when {
            pageState?.errorMessage != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text =
                        "第 ${pageSize.pageIndex + 1} 页渲染失败",
                    color = Color(0xFF262626),
                )
                Button(
                    onClick = {
                        onRetryPage(pageSize.pageIndex)
                    },
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag(
                            "pdf_page_retry_${pageSize.pageIndex}",
                        ),
                ) {
                    Text("重试")
                }
            }

            pageState?.bitmap != null -> Image(
                bitmap = pageState.bitmap.asImageBitmap(),
                contentDescription =
                    "第 ${pageSize.pageIndex + 1} 页",
                modifier = Modifier.fillMaxSize(),
            )

            pageState?.isLoading == true ->
                CircularProgressIndicator()

            else -> Unit
        }
    }
}

@Composable
private fun PdfToolbar(
    fileName: String,
    currentPageIndex: Int,
    totalPages: Int,
    onBack: () -> Unit,
    safeDrawingInsets: WindowInsets,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pdf_reader_toolbar"),
        color = Color.Black.copy(alpha = 0.62f),
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    safeDrawingInsets.only(
                        WindowInsetsSides.Top +
                            WindowInsetsSides.Horizontal,
                    ),
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaIconButton(
                icon = MediaIcons.Back,
                contentDescription = "返回",
                onClick = onBack,
                modifier = Modifier.testTag("pdf_reader_back"),
            )
            Text(
                text = fileName,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (totalPages == 0) {
                    "0 / 0"
                } else {
                    "${currentPageIndex.coerceIn(0, totalPages - 1) + 1} / " +
                        totalPages
                },
                modifier = Modifier.testTag("pdf_page_number"),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun viewportSnapshot(
    listState: LazyListState,
    pageSizes: List<PdfPageSize>,
    viewportWidthPx: Int,
    scale: Float,
): PdfViewportSnapshot? {
    val layoutInfo = listState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo.mapNotNull { item ->
        val pageIndex = pageSizes.getOrNull(item.index)
            ?.pageIndex ?: return@mapNotNull null
        VisiblePdfPage(
            pageIndex = pageIndex,
            offsetPx = item.offset,
            sizePx = item.size,
        )
    }
    val pageIndex = mostVisiblePdfPage(
        items = visibleItems,
        viewportStartPx = layoutInfo.viewportStartOffset,
        viewportEndPx = layoutInfo.viewportEndOffset,
    ) ?: return null
    val visiblePages = visibleItems
        .filter { page ->
            page.offsetPx + page.sizePx >
                layoutInfo.viewportStartOffset &&
                page.offsetPx < layoutInfo.viewportEndOffset
        }
        .mapTo(linkedSetOf()) { it.pageIndex }
    return PdfViewportSnapshot(
        pageIndex = pageIndex,
        visiblePages = visiblePages,
        viewportWidthPx = viewportWidthPx,
        scale = scale,
    )
}

private fun LazyListState.pageAt(
    pageSizes: List<PdfPageSize>,
    viewportYPx: Float,
): Pair<LazyListItemInfo, Int>? = layoutInfo.visibleItemsInfo
    .firstOrNull { item ->
        viewportYPx >= item.offset &&
            viewportYPx <= item.offset + item.size
    }
    ?.let { item ->
        val pageIndex = pageSizes.getOrNull(item.index)
            ?.pageIndex ?: return@let null
        item to pageIndex
    }

private fun Modifier.pdfTransformGestures(
    onGesture: (Offset, Offset, Float) -> Unit,
): Modifier = composed {
    val currentOnGesture by rememberUpdatedState(onGesture)
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    val centroid = pressed
                        .fold(Offset.Zero) { total, change ->
                            total + change.position
                        } / pressed.size.toFloat()
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    if (
                        abs(zoom - 1f) > TRANSFORM_EPSILON ||
                        pan != Offset.Zero
                    ) {
                        currentOnGesture(centroid, pan, zoom)
                    }
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}

internal fun reducePdfScreenGesture(
    current: PdfTransform,
    zoomChange: Float,
    panXPx: Float,
    currentCentroidXPx: Float,
    viewportWidthPx: Float,
): PdfTransform = PdfTransformReducer.gesture(
    current = current,
    zoomChange = zoomChange,
    panXPx = panXPx,
    centroidXPx = currentCentroidXPx - panXPx,
    viewportWidthPx = viewportWidthPx,
)

private data class ZoomAnchor(
    val pageIndex: Int,
    val pageRatio: Float,
    val centroidYPx: Float,
    val generation: Long,
)

private data class PdfViewportSnapshot(
    val pageIndex: Int,
    val visiblePages: Set<Int>,
    val viewportWidthPx: Int,
    val scale: Float,
)

private const val DEFAULT_PAGE_RATIO = 0.707f
private const val TRANSFORM_EPSILON = 0.0001f
