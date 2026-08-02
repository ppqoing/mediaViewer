package com.local.mediaviewer

import android.content.Context
import android.graphics.drawable.Animatable
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.asDrawable
import coil3.request.SuccessResult
import com.local.mediaviewer.image.MediaImageLoaderFactory
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GifImageLoaderInstrumentedTest {
    @Test
    fun networkGifDecodesAsRunningAnimation() = runBlocking {
        val context =
            ApplicationProvider.getApplicationContext<Context>()
        val server = MockWebServer()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .setHeader("Content-Type", "image/gif")
                .body(
                    Buffer().write(
                        Base64.decode(
                            TWO_FRAME_GIF_BASE64,
                            Base64.DEFAULT,
                        ),
                    ),
                )
                .build(),
        )
        server.start()
        val loader = MediaImageLoaderFactory.create(context)
        try {
            val result = loader.execute(
                MediaImageLoaderFactory.createRequest(
                    context = context,
                    url = server.url("/animated.gif").toString(),
                ),
            )
            val success = result as? SuccessResult
            assertTrue("GIF load failed: $result", success != null)
            val drawable = requireNotNull(success).image
                .asDrawable(context.resources)
            assertTrue(
                "GIF must decode to an animated drawable",
                drawable is Animatable,
            )
            val animation = drawable as Animatable
            animation.start()
            assertTrue(
                "Decoded GIF animation did not start",
                animation.isRunning,
            )
        } finally {
            loader.shutdown()
            server.close()
        }
    }

    private companion object {
        const val TWO_FRAME_GIF_BASE64 =
            "R0lGODlhAgACAIEAAP8AAAAAAAAAAAAAACH/C05FVFNDQVBFMi4w" +
                "AwEAAAAh+QQIDAAAACwAAAAAAgACAAAIBgABCAQQEAAh+QQIDAAA" +
                "ACwAAAAAAgACAIEAAP8AAAAAAAAAAAAIBgABCAQQEAA7"
    }
}
