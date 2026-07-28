package com.local.mediaviewer

import android.content.Context
import android.content.pm.ActivityInfo
import android.view.SurfaceView
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.request.SuccessResult
import com.local.mediaviewer.image.MediaImageLoaderFactory
import com.local.mediaviewer.playback.AndroidVlcPlaybackEngine
import com.local.mediaviewer.playback.MediaViewerDatabase
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.playback.RoomPlaybackPositionStore
import com.local.mediaviewer.testing.MediaFixtureFactory
import com.local.mediaviewer.testing.MediaFixtureServer
import com.local.mediaviewer.ui.player.FullscreenController
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaPlaybackInstrumentedTest {
    private lateinit var context: Context
    private lateinit var fixtureDirectory: File
    private lateinit var server: MediaFixtureServer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fixtureDirectory = File(
            context.cacheDir,
            "playback-fixtures",
        ).apply {
            deleteRecursively()
            mkdirs()
        }
        server = MediaFixtureServer(
            MediaFixtureFactory(fixtureDirectory).create(),
        )
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
        fixtureDirectory.deleteRecursively()
    }

    @Test
    fun videoUsesRangePlaysSeeksAndReattachesAfterActivityRecreation() {
        val engine = AndroidVlcPlaybackEngine(context)
        try {
            ActivityScenario.launch(
                MainActivity::class.java,
            ).use { scenario ->
                scenario.onActivity { activity ->
                    val surface = SurfaceView(activity)
                    activity.setContentView(surface)
                    engine.attachVideoSurface(surface)
                }
                engine.prepare(
                    server.url("/middle/sample.mp4"),
                )
                engine.play()
                var seekableObserved = false
                waitUntil(
                    timeoutMs = 20_000,
                    diagnostic = {
                        "${engine.state.value}; rangeRequests=" +
                            server.rangeRequestCount(
                                "/middle/sample.mp4",
                            ) +
                            "; ranges=" +
                            server.rangeRequests(
                                "/middle/sample.mp4",
                            ) +
                            "; seekableObserved=" +
                            seekableObserved
                    },
                ) {
                    val state = engine.state.value
                    seekableObserved =
                        seekableObserved || state.isSeekable
                    state.durationMs > 0L &&
                        seekableObserved &&
                        state.status in setOf(
                            PlaybackStatus.PLAYING,
                            PlaybackStatus.PAUSED,
                            PlaybackStatus.ENDED,
                        )
                }

                val duration = engine.state.value.durationMs
                val target = duration / 2
                engine.seekTo(target)
                engine.play()
                waitUntil(
                    timeoutMs = 10_000,
                    diagnostic = {
                        engine.state.value.toString()
                    },
                ) {
                    abs(
                        engine.state.value.positionMs - target,
                    ) < 2_000L
                }
                val positionBeforeRecreation =
                    engine.state.value.positionMs
                scenario.onActivity {
                    engine.detachVideoSurface()
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    val replacementSurface =
                        SurfaceView(activity)
                    activity.setContentView(
                        replacementSurface,
                    )
                    engine.attachVideoSurface(
                        replacementSurface,
                    )
                }
                engine.play()
                waitUntil(
                    timeoutMs = 10_000,
                    diagnostic = {
                        engine.state.value.toString()
                    },
                ) {
                    engine.state.value.status !=
                        PlaybackStatus.ERROR &&
                        engine.state.value.durationMs == duration &&
                        engine.state.value.positionMs >=
                        (
                            positionBeforeRecreation - 1_000L
                        ).coerceAtLeast(0L)
                }
                assertTrue(
                    server.rangeRequestCount(
                        "/middle/sample.mp4",
                    ) > 0,
                )
            }
        } finally {
            engine.close()
        }
    }

    @Test
    fun audioUsesRangeAndBecomesSeekable() {
        val engine = AndroidVlcPlaybackEngine(context)
        try {
            engine.prepare(server.url("/pik/sample.wav"))
            engine.play()
            waitUntil(
                timeoutMs = 20_000,
                diagnostic = {
                    engine.state.value.toString()
                },
            ) {
                engine.state.value.durationMs > 0L &&
                    engine.state.value.isSeekable &&
                    engine.state.value.status != PlaybackStatus.ERROR
            }
            val target = engine.state.value.durationMs / 2
            engine.seekTo(target)
            waitUntil(
                timeoutMs = 10_000,
                diagnostic = {
                    engine.state.value.toString()
                },
            ) {
                abs(
                    engine.state.value.positionMs - target,
                ) < 2_000L ||
                    engine.state.value.status ==
                    PlaybackStatus.ENDED
            }
            assertTrue(
                server.rangeRequestCount(
                    "/pik/sample.wav",
                ) > 0,
            )
        } finally {
            engine.close()
        }
    }

    @Test
    fun fixtureImageLoadsWithMemoryOnlyCoil() = runBlocking {
        val loader = MediaImageLoaderFactory.create(context)
        try {
            val request =
                MediaImageLoaderFactory.createRequest(
                    context = context,
                    url = server.url("/pik/sample.png"),
                )
            val result = loader.execute(request)
            assertTrue(result is SuccessResult)
            assertNull(loader.diskCache)
        } finally {
            loader.shutdown()
        }
    }

    @Test
    fun playbackPositionSurvivesDatabaseReopen() = runBlocking {
        val databaseName = "playback-restart-test.db"
        val mediaKey =
            "http://media.test:8080/middle/sample.mp4"
        context.deleteDatabase(databaseName)
        try {
            val firstDatabase = Room.databaseBuilder(
                context,
                MediaViewerDatabase::class.java,
                databaseName,
            ).build()
            RoomPlaybackPositionStore(
                firstDatabase.playbackPositionDao(),
            ).record(
                mediaKey = mediaKey,
                positionMs = 15_000L,
                durationMs = 60_000L,
                updatedAtEpochMs = 1_722_124_800_000L,
                ended = false,
            )
            firstDatabase.close()

            val reopenedDatabase = Room.databaseBuilder(
                context,
                MediaViewerDatabase::class.java,
                databaseName,
            ).build()
            try {
                val reopenedStore =
                    RoomPlaybackPositionStore(
                        reopenedDatabase
                            .playbackPositionDao(),
                    )
                assertEquals(
                    15_000L,
                    reopenedStore.resumePosition(mediaKey),
                )
            } finally {
                reopenedDatabase.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun fullscreenControllerRequestsLandscapeAndRestores() {
        ActivityScenario.launch(
            MainActivity::class.java,
        ).use { scenario ->
            scenario.onActivity { activity ->
                val controller =
                    FullscreenController(activity)
                try {
                    controller.enter()
                    assertEquals(
                        ActivityInfo
                            .SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                        activity.requestedOrientation,
                    )
                    assertTrue(
                        controller.isFullscreen.value,
                    )
                } finally {
                    controller.close()
                }
                assertEquals(
                    ActivityInfo
                        .SCREEN_ORIENTATION_UNSPECIFIED,
                    activity.requestedOrientation,
                )
            }
        }
    }

    private fun waitUntil(
        timeoutMs: Long,
        diagnostic: () -> String,
        condition: () -> Boolean,
    ) {
        val deadline =
            System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        error(
            "在 ${timeoutMs}ms 内未达到播放条件，" +
                "当前状态：${diagnostic()}",
        )
    }
}
