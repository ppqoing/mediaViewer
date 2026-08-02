package com.local.mediaviewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.local.mediaviewer.pdf.PdfLoadPhase
import com.local.mediaviewer.pdf.PdfPageSize
import com.local.mediaviewer.pdf.PdfPageUiState
import com.local.mediaviewer.pdf.PdfReaderUiState
import com.local.mediaviewer.ui.pdf.PdfReaderScreen
import com.local.mediaviewer.ui.theme.MediaViewerTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PdfReaderScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun loadingStateUsesChineseText() {
        setScreen(
            PdfReaderUiState.Loading(
                fileName = "sample.pdf",
                phase = PdfLoadPhase.DOWNLOADING,
            ),
        )

        rule.onNodeWithText("正在下载 PDF…")
            .assertIsDisplayed()
    }

    @Test
    fun documentErrorRetriesTheDocument() {
        var retries = 0
        setScreen(
            state = PdfReaderUiState.Error(
                fileName = "broken.pdf",
                message = "PDF 文档无法打开",
            ),
            onRetryDocument = { retries += 1 },
        )

        rule.onNodeWithText("PDF 文档无法打开")
            .assertIsDisplayed()
        rule.onNodeWithText("重试").performClick()
        rule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun contentShowsThreeVerticalPagesAndOneBasedToolbarPage() {
        setScreen(
            contentState(
                currentPageIndex = 1,
                heightPoints = 30,
            ),
        )

        val first = rule.onNodeWithTag(
            "pdf_page_0",
            useUnmergedTree = true,
        )
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val second = rule.onNodeWithTag(
            "pdf_page_1",
            useUnmergedTree = true,
        )
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val third = rule.onNodeWithTag(
            "pdf_page_2",
            useUnmergedTree = true,
        )
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot

        assertTrue(first.top < second.top)
        assertTrue(second.top < third.top)
        rule.onNodeWithText("2 / 3").assertIsDisplayed()
    }

    @Test
    fun pageErrorOnlyRetriesItsZeroBasedPage() {
        var retriedPage = -1
        val base = contentState(heightPoints = 30)
        setScreen(
            state = base.copy(
                pages = mapOf(
                    1 to PdfPageUiState(
                        errorMessage = "render failed",
                    ),
                ),
            ),
            onRetryPage = { retriedPage = it },
        )

        rule.onNodeWithText("第 2 页渲染失败")
            .assertIsDisplayed()
        rule.onNodeWithTag(
            "pdf_page_0",
            useUnmergedTree = true,
        )
            .assertIsDisplayed()
        rule.onNodeWithTag(
            "pdf_page_2",
            useUnmergedTree = true,
        )
            .assertIsDisplayed()
        rule.onNodeWithTag("pdf_page_retry_1")
            .performClick()
        rule.runOnIdle { assertEquals(1, retriedPage) }
    }

    @Test
    fun pageTapOnlyTogglesTheTopToolbar() {
        setScreen(contentState(heightPoints = 30))

        rule.onNodeWithTag("pdf_reader_toolbar")
            .assertIsDisplayed()
        rule.onNodeWithTag("pdf_reader_list")
            .performTouchInput { click(center) }
        rule.onNodeWithTag("pdf_reader_toolbar")
            .assertDoesNotExist()
        rule.onNodeWithTag("pdf_reader_list")
            .performTouchInput { click(center) }
        rule.onNodeWithTag("pdf_reader_toolbar")
            .assertIsDisplayed()
    }

    @Test
    fun toolbarControlsStayInsideInjectedSafeDrawingInsets() {
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 1f),
            ) {
                Box(
                    Modifier
                        .size(600.dp, 568.dp)
                        .testTag("pdf_reader_window"),
                ) {
                    MediaViewerTheme(darkTheme = true) {
                        PdfReaderScreen(
                            state = contentState(
                                currentPageIndex = 1,
                                heightPoints = 30,
                            ),
                            onViewportChanged =
                                { _, _, _, _ -> },
                            onRetryDocument = {},
                            onRetryPage = {},
                            onBack = {},
                            safeDrawingInsets = WindowInsets(
                                left = 16.dp,
                                top = 24.dp,
                                right = 20.dp,
                                bottom = 32.dp,
                            ),
                        )
                    }
                }
            }
        }

        val window = rule.onNodeWithTag("pdf_reader_window")
            .fetchSemanticsNode().boundsInRoot
        val back = rule.onNodeWithContentDescription("返回")
            .fetchSemanticsNode().boundsInRoot
        val pageNumber = rule.onNodeWithTag("pdf_page_number")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(back.left >= window.left + 16f)
        assertTrue(back.top >= window.top + 24f)
        assertTrue(pageNumber.top >= window.top + 24f)
        assertTrue(pageNumber.right <= window.right - 20f)
    }

    @Test
    fun viewportCallbackReportsDominantPageVisibleSetWidthAndScale() {
        var event: ViewportEvent? = null
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 1f),
            ) {
                Box(
                    Modifier
                        .size(300.dp, 500.dp)
                        .testTag("pdf_viewport"),
                ) {
                    MediaViewerTheme(darkTheme = true) {
                        PdfReaderScreen(
                            state = contentState(
                                heightPoints = 100,
                                widthPoints = 300,
                            ),
                            onViewportChanged =
                                { page, visible, width, scale ->
                                    event = ViewportEvent(
                                        page,
                                        visible,
                                        width,
                                        scale,
                                    )
                                },
                            onRetryDocument = {},
                            onRetryPage = {},
                            onBack = {},
                            safeDrawingInsets = WindowInsets(0),
                        )
                    }
                }
            }
        }

        rule.waitUntil(timeoutMillis = 5_000) {
            event != null
        }
        val listWidth = rule.onNodeWithTag("pdf_reader_list")
            .fetchSemanticsNode().boundsInRoot.width.roundToInt()
        rule.runOnIdle {
            assertEquals(0, event?.pageIndex)
            assertEquals(setOf(0, 1, 2), event?.visiblePages)
            assertEquals(listWidth, event?.viewportWidthPx)
            assertEquals(1f, event?.scale ?: 0f, 0.001f)
        }
    }

    @Test
    fun pinchKeepsTheCentroidOnTheSamePageContent() {
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 1f),
            ) {
                Box(Modifier.size(400.dp, 700.dp)) {
                    MediaViewerTheme(darkTheme = true) {
                        PdfReaderScreen(
                            state = contentState(
                                heightPoints = 50,
                                widthPoints = 100,
                            ),
                            onViewportChanged =
                                { _, _, _, _ -> },
                            onRetryDocument = {},
                            onRetryPage = {},
                            onBack = {},
                            safeDrawingInsets = WindowInsets(0),
                        )
                    }
                }
            }
        }

        val centroidY = 300f
        val before = rule.onNodeWithTag(
            "pdf_page_1",
            useUnmergedTree = true,
        )
            .fetchSemanticsNode().boundsInRoot
        val beforeRatio =
            (centroidY - before.top) / before.height

        rule.onNodeWithTag("pdf_reader_list")
            .performTouchInput {
                val centroid = Offset(center.x, centroidY)
                down(0, centroid + Offset(-50f, 0f))
                down(1, centroid + Offset(50f, 0f))
                moveTo(
                    0,
                    centroid + Offset(-100f, 0f),
                    delayMillis = 120L,
                )
                moveTo(
                    1,
                    centroid + Offset(100f, 0f),
                    delayMillis = 120L,
                )
                up(0)
                up(1)
            }
        rule.waitForIdle()

        val after = rule.onNodeWithTag(
            "pdf_page_1",
            useUnmergedTree = true,
        )
            .fetchSemanticsNode().boundsInRoot
        val afterRatio =
            (centroidY - after.top) / after.height
        assertTrue(after.height > before.height)
        assertTrue(abs(afterRatio - beforeRatio) < 0.08f)
    }

    @Test
    fun verticalSwipeStillScrollsWhileTapAndTransformGesturesAreInstalled() {
        var reportedPageIndex = 0
        rule.setContent {
            var state by remember {
                mutableStateOf(
                    contentState(heightPoints = 140),
                )
            }
            MediaViewerTheme(darkTheme = true) {
                PdfReaderScreen(
                    state = state,
                    onViewportChanged =
                        { page, _, _, _ ->
                            reportedPageIndex = page
                            state = state.copy(
                                currentPageIndex = page,
                            )
                        },
                    onRetryDocument = {},
                    onRetryPage = {},
                    onBack = {},
                    safeDrawingInsets = WindowInsets(0),
                )
            }
        }

        rule.onNodeWithText("1 / 3").assertIsDisplayed()
        rule.onNodeWithTag("pdf_reader_list")
            .performTouchInput { swipeUp() }
        rule.waitUntil(timeoutMillis = 5_000) {
            reportedPageIndex > 0
        }
        rule.onNodeWithTag("pdf_reader_list")
            .performTouchInput { click(center) }
        rule.onNodeWithTag("pdf_reader_toolbar")
            .assertDoesNotExist()
    }

    private fun setScreen(
        state: PdfReaderUiState,
        onRetryDocument: () -> Unit = {},
        onRetryPage: (Int) -> Unit = {},
    ) {
        rule.setContent {
            MediaViewerTheme(darkTheme = true) {
                PdfReaderScreen(
                    state = state,
                    onViewportChanged =
                        { _, _, _, _ -> },
                    onRetryDocument = onRetryDocument,
                    onRetryPage = onRetryPage,
                    onBack = {},
                    safeDrawingInsets = WindowInsets(0),
                )
            }
        }
    }

    private fun contentState(
        currentPageIndex: Int = 0,
        widthPoints: Int = 100,
        heightPoints: Int = 100,
    ): PdfReaderUiState.Content = PdfReaderUiState.Content(
        fileName = "sample.pdf",
        pageSizes = (0..2).map { pageIndex ->
            PdfPageSize(
                pageIndex = pageIndex,
                widthPoints = widthPoints,
                heightPoints = heightPoints,
            )
        },
        pages = emptyMap(),
        currentPageIndex = currentPageIndex,
    )

    private data class ViewportEvent(
        val pageIndex: Int,
        val visiblePages: Set<Int>,
        val viewportWidthPx: Int,
        val scale: Float,
    )
}
