package com.local.mediaviewer

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.local.mediaviewer.app.MediaViewerApp
import com.local.mediaviewer.image.ImageReaderItem
import com.local.mediaviewer.image.ImageSortOrder
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.testing.FakeAppContainer
import com.local.mediaviewer.testing.defaultDirectoryContent
import com.local.mediaviewer.ui.image.ComicHorizontalOffsetSemanticsKey
import com.local.mediaviewer.ui.image.ComicItemIndexSemanticsKey
import com.local.mediaviewer.ui.image.ComicReader
import com.local.mediaviewer.ui.image.ComicScaleSemanticsKey
import com.local.mediaviewer.ui.image.imageSortLabel
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MediaEnhancementsEndToEndTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var container: FakeAppContainer

    @Before
    fun setUp() {
        val context =
            ApplicationProvider
                .getApplicationContext<Context>()
        container = FakeAppContainer(context)
    }

    @After
    fun tearDown() {
        container.close()
    }

    @Test
    fun imageNavigationKeepsAnchorAcrossAllSortMenus() {
        setApplication()
        openNestedDirectory()
        rule.onNodeWithText("002.jpg").performClick()
        rule.onNodeWithTag("comic_reader")
            .assertIsDisplayed()
        assertVisibleTitle("002.jpg")

        val expectedFirst = mapOf(
            ImageSortOrder.NAME_ASC to "001.jpg",
            ImageSortOrder.NAME_DESC to "样例.png",
            ImageSortOrder.MODIFIED_ASC to "002.jpg",
            ImageSortOrder.MODIFIED_DESC to "001.jpg",
            ImageSortOrder.SIZE_ASC to "002.jpg",
            ImageSortOrder.SIZE_DESC to "样例.png",
        )
        ImageSortOrder.entries.forEach { order ->
            selectSort(order)
            val expectedName =
                requireNotNull(expectedFirst[order])
            waitForItemAtIndex(
                name = expectedName,
                index = 0,
            )
            assertVisibleTitle("002.jpg")
        }

        selectSort(ImageSortOrder.NAME_ASC)
        val reader =
            rule.onNodeWithTag("comic_reader")
        reader.performScrollToIndex(0)
        rule.onNodeWithTag("comic_item:001.jpg")
            .assertIsDisplayed()
        reader.performScrollToIndex(2)
        rule.onNodeWithTag("comic_item:003.jpg")
            .assertIsDisplayed()
    }

    @Test
    fun settingsChangesDefaultReaderWithoutChangingServer() {
        setApplication()
        val serverBefore = runBlocking {
            container.settingsRepository
                .current()
                .logicalBaseUrl
        }
        rule.onNodeWithContentDescription("设置")
            .performClick()
        rule.onNodeWithTag("default_reader_comic")
            .assertIsDisplayed()
        rule.onNodeWithTag("default_reader_single")
            .performClick()
        rule.waitUntil(5_000) {
            container.savedReaderModes ==
                listOf(
                    com.local.mediaviewer.image
                        .ImageReaderMode.SINGLE,
                )
        }
        rule.onNodeWithContentDescription("返回")
            .performClick()
        waitForHome()

        val serverAfter = runBlocking {
            container.settingsRepository
                .current()
                .logicalBaseUrl
        }
        assertEquals(serverBefore, serverAfter)
        assertEquals(
            listOf(
                com.local.mediaviewer.image
                    .ImageReaderMode.SINGLE,
            ),
            container.savedReaderModes,
        )

        openNestedDirectory()
        rule.onNodeWithText("002.jpg").performClick()
        rule.onNodeWithTag("media_image")
            .assertIsDisplayed()
        rule.onNodeWithTag("comic_reader")
            .assertDoesNotExist()
    }

    @Test
    fun everyVisibleComicItemSharesScaleAndOffset() {
        val items = defaultDirectoryContent()
            .entries
            .filter {
                it.kind == MediaKind.IMAGE &&
                    it.name in setOf(
                        "001.jpg",
                        "002.jpg",
                        "003.jpg",
                    )
            }
            .map { entry ->
                ImageReaderItem(
                    name = entry.name,
                    size = entry.size,
                    modifiedAt = entry.modifiedAt,
                    logicalUrl = entry.logicalUrl,
                    requestUrl = entry.requestUrl,
                )
            }
            .sortedBy(ImageReaderItem::name)
        rule.setContent {
            MaterialTheme {
                ComicReader(
                    images = items,
                    anchorLogicalUrl =
                        items.first().logicalUrl,
                    sortOrder =
                        ImageSortOrder.NAME_ASC,
                    imageLoader =
                        container.imageLoader,
                    requestGeneration = 0,
                    itemFailures = emptyMap(),
                    itemRequestGenerations =
                        emptyMap(),
                    onAnchorChanged = {},
                    onImageLoadError = { _, _ -> },
                    onImageLoadSuccess = {},
                    onRetryImage = {},
                )
            }
        }
        waitForComicItem("001.jpg")
        waitForComicItem("002.jpg")

        rule.onNodeWithTag("comic_reader")
            .performTouchInput {
                val middle = center
                down(
                    pointerId = 0,
                    position =
                        middle + Offset(-40f, 0f),
                )
                down(
                    pointerId = 1,
                    position =
                        middle + Offset(40f, 0f),
                )
                moveTo(
                    pointerId = 0,
                    position =
                        middle + Offset(-80f, 0f),
                    delayMillis = 100L,
                )
                moveTo(
                    pointerId = 1,
                    position =
                        middle + Offset(80f, 0f),
                    delayMillis = 100L,
                )
                moveTo(
                    pointerId = 0,
                    position =
                        middle + Offset(-120f, 0f),
                    delayMillis = 100L,
                )
                moveTo(
                    pointerId = 1,
                    position =
                        middle + Offset(120f, 0f),
                    delayMillis = 100L,
                )
                moveTo(
                    pointerId = 0,
                    position =
                        middle + Offset(-160f, 0f),
                    delayMillis = 100L,
                )
                moveTo(
                    pointerId = 1,
                    position =
                        middle + Offset(160f, 0f),
                    delayMillis = 100L,
                )
                up(pointerId = 0)
                up(pointerId = 1)
            }
        val firstScale = comicScale("001.jpg")
        val secondScale = comicScale("002.jpg")
        assertTrue(firstScale > 3.5f)
        assertEquals(
            firstScale,
            secondScale,
            0.001f,
        )

        rule.onNodeWithTag("comic_reader")
            .performTouchInput {
                val start = center
                down(
                    pointerId = 0,
                    position = start,
                )
                moveTo(
                    pointerId = 0,
                    position =
                        start + Offset(80f, 0f),
                    delayMillis = 200L,
                )
                up(pointerId = 0)
            }
        val firstOffset =
            comicOffset("001.jpg")
        val secondOffset =
            comicOffset("002.jpg")
        assertTrue(abs(firstOffset) > 0.1f)
        assertEquals(
            firstOffset,
            secondOffset,
            0.001f,
        )

        rule.onNodeWithTag("comic_reader")
            .performTouchInput {
                val middle = center
                down(
                    pointerId = 0,
                    position =
                        middle + Offset(-120f, 0f),
                )
                down(
                    pointerId = 1,
                    position =
                        middle + Offset(120f, 0f),
                )
                moveTo(
                    pointerId = 0,
                    position =
                        middle + Offset(-30f, 0f),
                )
                moveTo(
                    pointerId = 1,
                    position =
                        middle + Offset(30f, 0f),
                )
                up(pointerId = 0)
                up(pointerId = 1)
            }
        assertEquals(
            1f,
            comicScale("001.jpg"),
            0.001f,
        )
        assertEquals(
            0f,
            comicOffset("001.jpg"),
            0.001f,
        )
    }

    private fun setApplication() {
        rule.setContent {
            MediaViewerApp(container)
        }
        waitForHome()
    }

    private fun waitForHome() {
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("MiddleDir")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun openNestedDirectory() {
        rule.onNodeWithText("MiddleDir")
            .performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("示例目录")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        rule.onNodeWithText("示例目录")
            .performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("002.jpg")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun selectSort(order: ImageSortOrder) {
        rule.onNodeWithTag("image_sort_menu")
            .performClick()
        rule.onNodeWithText(imageSortLabel(order))
            .performClick()
    }

    private fun waitForItemAtIndex(
        name: String,
        index: Int,
    ) {
        val matcher =
            hasTestTag("comic_item:$name") and
                SemanticsMatcher.expectValue(
                    ComicItemIndexSemanticsKey,
                    index,
                )
        rule.waitUntil(5_000) {
            rule.onAllNodes(matcher)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun assertVisibleTitle(name: String) {
        rule.onAllNodesWithText(name)
            .onFirst()
            .assertIsDisplayed()
    }

    private fun waitForComicItem(name: String) {
        rule.waitUntil(5_000) {
            rule.onAllNodes(
                hasTestTag("comic_item:$name"),
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun comicScale(name: String): Float =
        rule.onNodeWithTag("comic_item:$name")
            .fetchSemanticsNode()
            .config[ComicScaleSemanticsKey]

    private fun comicOffset(name: String): Float =
        rule.onNodeWithTag("comic_item:$name")
            .fetchSemanticsNode()
            .config[
                ComicHorizontalOffsetSemanticsKey
            ]
}
