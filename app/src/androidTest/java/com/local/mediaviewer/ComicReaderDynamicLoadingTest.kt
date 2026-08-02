package com.local.mediaviewer

import android.content.Context
import android.os.SystemClock
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import com.local.mediaviewer.image.ComicTransform
import com.local.mediaviewer.image.ImageReaderItem
import com.local.mediaviewer.image.ImageSortOrder
import com.local.mediaviewer.image.MediaImageLoaderFactory
import com.local.mediaviewer.testing.MediaFixtureFactory
import com.local.mediaviewer.testing.MediaFixtureServer
import com.local.mediaviewer.ui.image.ComicReader
import com.local.mediaviewer.ui.image.ComicViewportAnchorErrorSemanticsKey
import java.io.File
import java.time.Instant
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ComicReaderDynamicLoadingTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var fixtureDirectory: File
    private lateinit var server: MediaFixtureServer
    private lateinit var imageLoader: ImageLoader

    @Before
    fun setUp() {
        val context =
            ApplicationProvider
                .getApplicationContext<Context>()
        fixtureDirectory = File(
            context.cacheDir,
            "comic-reader-fixtures",
        ).apply {
            deleteRecursively()
            mkdirs()
        }
        server = MediaFixtureServer(
            fixtures =
                MediaFixtureFactory(
                    fixtureDirectory,
                ).create(),
            imageCount = IMAGE_COUNT,
        )
        server.start()
        imageLoader =
            MediaImageLoaderFactory.create(context)
    }

    @After
    fun tearDown() {
        imageLoader.shutdown()
        server.close()
        fixtureDirectory.deleteRecursively()
    }

    @Test
    fun fiftyImagesAreRequestedLazilyAsReaderScrolls() {
        val images = (1..IMAGE_COUNT).map { index ->
            val name =
                "page-" +
                    index.toString()
                        .padStart(3, '0') +
                    ".png"
            ImageReaderItem(
                name = name,
                size = 1_024L,
                modifiedAt =
                    Instant.parse(
                        "2026-07-28T00:00:00Z",
                    ),
                logicalUrl =
                    "http://media.example/pik/$name",
                requestUrl =
                    server.url("/pik/$name"),
            )
        }
        rule.setContent {
            var transform by remember {
                mutableStateOf(ComicTransform())
            }
            MaterialTheme {
                ComicReader(
                    images = images,
                    anchorLogicalUrl =
                        images.first().logicalUrl,
                    sortOrder =
                        ImageSortOrder.NAME_ASC,
                    imageLoader = imageLoader,
                    requestGeneration = 0,
                    itemFailures = emptyMap(),
                    itemRequestGenerations =
                        emptyMap(),
                    transform = transform,
                    onTransformChanged = {
                        transform = it
                    },
                    onAnchorChanged = {},
                    onImageLoadError = { _, _ -> },
                    onImageLoadSuccess = {},
                    onRetryImage = {},
                )
            }
        }

        rule.waitUntil(10_000) {
            server.mediaRequestCount() > 0
        }
        val initialCount =
            server.mediaRequestCount()
        val initialPaths =
            server.requestedMediaPaths()
        assertTrue(
            "initialCount=$initialCount; " +
                "initialPaths=$initialPaths",
            initialCount in 1 until IMAGE_COUNT,
        )
        assertTrue(
            "initialPaths=$initialPaths",
            initialPaths.size < IMAGE_COUNT,
        )

        rule.onNodeWithTag("comic_reader")
            .performScrollToIndex(IMAGE_COUNT - 1)
        rule.waitUntil(10_000) {
            server.requestedMediaPaths().contains(
                "/pik/page-050.png",
            )
        }
        val scrolledPaths =
            server.requestedMediaPaths()
        assertTrue(
            "scrolledPaths=$scrolledPaths",
            scrolledPaths.size < IMAGE_COUNT,
        )
    }

    @Test
    fun zoomingLoadedImageKeepsTheExistingRequest() {
        val image = imageItem(1)
        var successfulLoads = 0
        rule.setContent {
            var transform by remember {
                mutableStateOf(ComicTransform())
            }
            MaterialTheme {
                ComicReader(
                    images = listOf(image),
                    anchorLogicalUrl = image.logicalUrl,
                    sortOrder = ImageSortOrder.NAME_ASC,
                    imageLoader = imageLoader,
                    requestGeneration = 0,
                    itemFailures = emptyMap(),
                    itemRequestGenerations = emptyMap(),
                    transform = transform,
                    onTransformChanged = {
                        transform = it
                    },
                    onAnchorChanged = {},
                    onImageLoadError = { _, _ -> },
                    onImageLoadSuccess = {
                        successfulLoads += 1
                    },
                    onRetryImage = {},
                )
            }
        }
        rule.waitUntil(10_000) {
            successfulLoads > 0
        }
        val initialRequestCount =
            server.mediaRequestCount()
        val initialSuccessCount = successfulLoads
        zoomComic()
        zoomComic()
        rule.waitForIdle()
        waitForPossibleReload(
            initialRequestCount = initialRequestCount,
        )

        assertEquals(
            initialRequestCount,
            server.mediaRequestCount(),
        )
        assertEquals(
            initialSuccessCount,
            successfulLoads,
        )
    }

    @Test
    fun offCenterZoomKeepsTheSameImagePointAtTheFingerCentroid() {
        val images = (1..5).map(::imageItem)
        var successfulLoads = 0
        rule.setContent {
            var transform by remember {
                mutableStateOf(ComicTransform())
            }
            MaterialTheme {
                ComicReader(
                    images = images,
                    anchorLogicalUrl = images.first().logicalUrl,
                    sortOrder = ImageSortOrder.NAME_ASC,
                    imageLoader = imageLoader,
                    requestGeneration = 0,
                    itemFailures = emptyMap(),
                    itemRequestGenerations = emptyMap(),
                    transform = transform,
                    onTransformChanged = {
                        transform = it
                    },
                    onAnchorChanged = {},
                    onImageLoadError = { _, _ -> },
                    onImageLoadSuccess = {
                        successfulLoads += 1
                    },
                    onRetryImage = {},
                )
            }
        }
        rule.waitUntil(10_000) {
            successfulLoads >= images.size
        }
        zoomComic()
        rule.waitForIdle()
        val anchorError = rule.onNodeWithTag("comic_reader")
            .fetchSemanticsNode().config[
                ComicViewportAnchorErrorSemanticsKey
            ]
        assertTrue(
            "anchorError=$anchorError",
            abs(anchorError) <= 3f,
        )
    }

    private fun imageItem(index: Int): ImageReaderItem {
        val name =
            "page-" +
                index.toString().padStart(3, '0') +
                ".png"
        return ImageReaderItem(
            name = name,
            size = 1_024L,
            modifiedAt =
                Instant.parse(
                    "2026-07-28T00:00:00Z",
                ),
            logicalUrl =
                "http://media.example/pik/$name",
            requestUrl = server.url("/pik/$name"),
        )
    }

    private fun zoomComic() {
        rule.onNodeWithTag("comic_reader")
            .performTouchInput {
                val centroid = Offset(
                    x = center.x * 1.44f,
                    y = center.y * 0.4f,
                )
                down(0, centroid + Offset(-40f, 0f))
                down(1, centroid + Offset(40f, 0f))
                moveTo(
                    0,
                    centroid + Offset(-120f, 0f),
                    delayMillis = 120L,
                )
                moveTo(
                    1,
                    centroid + Offset(120f, 0f),
                    delayMillis = 120L,
                )
                up(0)
                up(1)
            }
    }

    private fun waitForPossibleReload(
        initialRequestCount: Int,
    ) {
        val deadline = SystemClock.uptimeMillis() + 750L
        while (
            server.mediaRequestCount() ==
                initialRequestCount &&
            SystemClock.uptimeMillis() < deadline
        ) {
            SystemClock.sleep(20L)
        }
    }

    private companion object {
        const val IMAGE_COUNT = 50
    }
}
