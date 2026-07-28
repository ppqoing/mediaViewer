package com.local.mediaviewer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.local.mediaviewer.testing.MediaFixtureFactory
import com.local.mediaviewer.testing.MediaFixtureServer
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaFixtureServerTest {
    @Test
    fun caddyDirectoriesAndByteRangesAreServed() {
        val context =
            ApplicationProvider.getApplicationContext<Context>()
        val directory = File(
            context.cacheDir,
            "fixture-server-test",
        ).apply {
            deleteRecursively()
            mkdirs()
        }
        val fixtures = MediaFixtureFactory(directory).create()

        MediaFixtureServer(
            fixtures = fixtures,
            imageCount = 50,
        ).use { server ->
            server.start()
            val client = OkHttpClient()

            client.newCall(
                Request.Builder()
                    .url(server.url("/middle/"))
                    .header("Accept", "application/json")
                    .build(),
            ).execute().use { listing ->
                assertEquals(200, listing.code)
                val json = requireNotNull(listing.body).string()
                assertTrue(
                    json.contains("\"name\":\"sample.mp4\""),
                )
                assertTrue(
                    json.contains("\"name\":\"sample.wav\""),
                )
                assertTrue(
                    json.contains("\"name\":\"sample.png\""),
                )
            }

            client.newCall(
                Request.Builder()
                    .url(
                        server.url(
                            "/pik/page-050.png",
                        ),
                    )
                    .build(),
            ).execute().use { page ->
                assertEquals(200, page.code)
                assertEquals(
                    "image/png",
                    page.header("Content-Type"),
                )
            }

            client.newCall(
                Request.Builder()
                    .url(server.url("/middle/sample.mp4"))
                    .header("Range", "bytes=4-11")
                    .build(),
            ).execute().use { ranged ->
                assertEquals(206, ranged.code)
                assertEquals(
                    "bytes 4-11/${fixtures.mp4.length()}",
                    ranged.header("Content-Range"),
                )
                assertEquals(
                    8L,
                    requireNotNull(ranged.body).contentLength(),
                )
            }

            client.newCall(
                Request.Builder()
                    .url(server.url("/middle/sample.mp4"))
                    .header("Range", "bytes=-4")
                    .build(),
            ).execute().use { suffix ->
                assertEquals(206, suffix.code)
                assertEquals(
                    4L,
                    requireNotNull(suffix.body).contentLength(),
                )
            }

            client.newCall(
                Request.Builder()
                    .url(server.url("/middle/sample.mp4"))
                    .head()
                    .build(),
            ).execute().use { head ->
                assertEquals(200, head.code)
                assertEquals(
                    fixtures.mp4.length().toString(),
                    head.header("Content-Length"),
                )
            }

            client.newCall(
                Request.Builder()
                    .url(server.url("/middle/sample.mp4"))
                    .header(
                        "Range",
                        "bytes=${fixtures.mp4.length()}-",
                    )
                    .build(),
            ).execute().use { unsatisfiable ->
                assertEquals(416, unsatisfiable.code)
                assertEquals(
                    "bytes */${fixtures.mp4.length()}",
                    unsatisfiable.header("Content-Range"),
                )
            }
            assertEquals(
                3,
                server.rangeRequestCount(
                    "/middle/sample.mp4",
                ),
            )
        }
    }
}
