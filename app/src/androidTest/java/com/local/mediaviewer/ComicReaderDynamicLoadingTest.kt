package com.local.mediaviewer

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import com.local.mediaviewer.image.ComicTransform
import com.local.mediaviewer.image.ImageReaderItem
import com.local.mediaviewer.image.ImageSortOrder
import com.local.mediaviewer.image.MediaImageLoaderFactory
import com.local.mediaviewer.testing.MediaFixtureFactory
import com.local.mediaviewer.testing.MediaFixtureServer
import com.local.mediaviewer.ui.image.ComicReader
import java.io.File
import java.time.Instant
import org.junit.After
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

    private companion object {
        const val IMAGE_COUNT = 50
    }
}
