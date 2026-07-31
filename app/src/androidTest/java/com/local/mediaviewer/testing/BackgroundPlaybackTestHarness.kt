package com.local.mediaviewer.testing

import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import androidx.room.Room
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import com.google.common.util.concurrent.ListenableFuture
import com.local.mediaviewer.MediaViewerApplication
import com.local.mediaviewer.app.AppContainer
import com.local.mediaviewer.browser.BrowserRepository
import com.local.mediaviewer.browser.DirectoryContentRepository
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.image.ReaderPreferencesRepository
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.network.ConnectionTestResult
import com.local.mediaviewer.playback.AndroidVlcPlaybackEngine
import com.local.mediaviewer.playback.MediaViewerDatabase
import com.local.mediaviewer.playback.PlaybackEngine
import com.local.mediaviewer.playback.PlaybackPositionStore
import com.local.mediaviewer.playback.RoomPlaybackPositionStore
import com.local.mediaviewer.player.Media3PlaybackController
import com.local.mediaviewer.player.QueuePlaybackController
import com.local.mediaviewer.queue.PlaybackCoordinator
import com.local.mediaviewer.queue.PlaybackQueueRepository
import com.local.mediaviewer.queue.RoomPlaybackQueueRepository
import com.local.mediaviewer.service.ACTION_STOP_AND_RELEASE
import com.local.mediaviewer.service.PlaybackService
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import com.local.mediaviewer.settings.PlayerPreferencesRepository
import com.local.mediaviewer.settings.ServerSettingsRepository
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

@UnstableApi
class BackgroundPlaybackTestHarness : Closeable {
    val application =
        ApplicationProvider.getApplicationContext<MediaViewerApplication>()
    private val originalContainer = application.container
    private val fixtureDirectory = File(
        application.cacheDir,
        "background-playback-fixtures",
    ).apply {
        deleteRecursively()
        mkdirs()
    }
    private val server = MediaFixtureServer(
        MediaFixtureFactory(
            directory = fixtureDirectory,
            videoDurationSeconds = 20,
        ).create(),
    ).also(MediaFixtureServer::start)
    val container = BackgroundPlaybackAppContainer(
        context = application,
        requestBaseUrl = server.url("/").trimEnd('/'),
    )
    private var closed = false

    init {
        application.container = container
    }

    fun videoQueue(): List<MediaItem> = listOf(
        mediaItem(
            name = VIDEO_TITLE,
            logicalPath = "/middle/sample.mp4",
            mediaType = MediaMetadata.MEDIA_TYPE_VIDEO,
        ),
    )

    fun mediaQueue(): List<MediaItem> = listOf(
        mediaItem(
            name = VIDEO_TITLE,
            logicalPath = "/middle/sample.mp4",
            mediaType = MediaMetadata.MEDIA_TYPE_VIDEO,
        ),
        mediaItem(
            name = AUDIO_TITLE,
            logicalPath = "/pik/sample.wav",
            mediaType = MediaMetadata.MEDIA_TYPE_MUSIC,
        ),
    )

    fun connectController(): TestMediaControllerConnection =
        TestMediaControllerConnection(
            buildControllerFuture(),
        )

    fun connectControllerAfterRelease(
        timeoutMs: Long = 5_000L,
    ): TestMediaControllerConnection {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastRejection: ExecutionException? = null
        while (System.currentTimeMillis() < deadline) {
            val future = buildControllerFuture()
            try {
                return TestMediaControllerConnection(
                    future = future,
                    timeoutMs = (deadline - System.currentTimeMillis())
                        .coerceAtLeast(1L),
                )
            } catch (error: Throwable) {
                onMain { MediaController.releaseFuture(future) }
                val cause = (error as? ExecutionException)?.cause
                if (
                    cause !is SecurityException ||
                    cause.message?.contains("Session rejected") != true
                ) {
                    throw error
                }
                lastRejection = error
            }
            Thread.sleep(25L)
        }
        throw IllegalStateException(
            "Timed out waiting for playback service cold connection",
            lastRejection,
        )
    }

    fun waitUntil(
        diagnostic: String,
        timeoutMs: Long = 20_000L,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(25L)
        }
        error("Timed out waiting for $diagnostic")
    }

    fun failNextSnapshotSave() {
        container.testPositionStore.failNextRecord = true
    }

    fun retryPersistence() {
        onMain {
            container.playbackController.retryPersistence()
        }
    }

    val snapshotSaveCalls: Int
        get() = container.testPositionStore.recordCalls

    val lastSnapshotSave: String?
        get() = container.testPositionStore.lastRecordSummary

    override fun close() {
        if (closed) return
        closed = true
        try {
            if (
                container.playbackEngineCreationCount >
                container.playbackEngineCloseCount
            ) {
                application.startService(
                    Intent(application, PlaybackService::class.java)
                        .setAction(ACTION_STOP_AND_RELEASE),
                )
                waitUntil("playback service release", timeoutMs = 5_000L) {
                    container.playbackEngineCreationCount ==
                        container.playbackEngineCloseCount
                }
            }
        } finally {
            container.close()
            application.container = originalContainer
            server.close()
            fixtureDirectory.deleteRecursively()
        }
    }

    private fun mediaItem(
        name: String,
        logicalPath: String,
        mediaType: Int,
    ): MediaItem {
        val logicalUrl = LOGICAL_BASE_URL + logicalPath
        return MediaItem.Builder()
            .setMediaId(logicalUrl)
            .setUri(logicalUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setMediaType(mediaType)
                    .build(),
            )
            .build()
    }

    private fun buildControllerFuture(): ListenableFuture<MediaController> =
        MediaController.Builder(
            application,
            SessionToken(
                application,
                ComponentName(application, PlaybackService::class.java),
            ),
        ).buildAsync()

    private companion object {
        const val LOGICAL_BASE_URL = "http://media.test:8080"
        const val VIDEO_TITLE = "后台测试视频"
        const val AUDIO_TITLE = "后台测试音频"
    }
}

@UnstableApi
class TestMediaControllerConnection internal constructor(
    private val future: ListenableFuture<MediaController>,
    timeoutMs: Long = 20_000L,
) : Closeable {
    private val controller = future.get(timeoutMs, TimeUnit.MILLISECONDS)

    fun run(block: MediaController.() -> Unit) {
        onMain { controller.block() }
    }

    fun <T> read(block: (MediaController) -> T): T =
        onMain { block(controller) }

    override fun close() {
        onMain { MediaController.releaseFuture(future) }
    }
}

@UnstableApi
class BackgroundPlaybackAppContainer(
    context: android.content.Context,
    requestBaseUrl: String,
) : AppContainer, Closeable {
    private val delegate = FakeAppContainer(context)
    private val appContext = context.applicationContext
    private val playbackScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate,
    )
    private val database = Room.inMemoryDatabaseBuilder(
        appContext,
        MediaViewerDatabase::class.java,
    ).build()
    private val queueRepository: PlaybackQueueRepository =
        RoomPlaybackQueueRepository(database.playbackQueueDao())
    private val roomPositionStore: PlaybackPositionStore =
        RoomPlaybackPositionStore(database.playbackPositionDao())
    internal val testPositionStore =
        FailOncePositionStore(roomPositionStore)
    private val positionStore: PlaybackPositionStore = testPositionStore
    private val endpoint = SessionEndpoint(
        logicalBaseUrl = "http://media.test:8080",
        requestBaseUrl = requestBaseUrl,
        ipv4 = "127.0.0.1",
    )
    private var controller: Media3PlaybackController? = null
    @Volatile
    var playbackEngineCreationCount = 0
        private set
    @Volatile
    var playbackEngineCloseCount = 0
        private set

    override val settingsRepository: ServerSettingsRepository
        get() = delegate.settingsRepository
    override val readerPreferencesRepository: ReaderPreferencesRepository
        get() = delegate.readerPreferencesRepository
    override val playerPreferencesRepository: PlayerPreferencesRepository
        get() = delegate.playerPreferencesRepository
    override val sessionManager: ServerSessionManager =
        FixtureServerSessionManager(endpoint)
    override val directoryContentRepository: DirectoryContentRepository
        get() = delegate.directoryContentRepository
    override val browserRepository: BrowserRepository
        get() = delegate.browserRepository
    override val playbackController: QueuePlaybackController
        get() = controller ?: Media3PlaybackController(
            context = appContext,
            scope = playbackScope,
        ).also { controller = it }
    override val playbackPositionStore: PlaybackPositionStore
        get() = positionStore
    override val imageLoader: ImageLoader
        get() = delegate.imageLoader

    override fun createPlaybackCoordinator(
        scope: CoroutineScope,
    ): PlaybackCoordinator {
        playbackEngineCreationCount += 1
        val engine = CountingPlaybackEngine(
            AndroidVlcPlaybackEngine(appContext),
        ) {
            playbackEngineCloseCount += 1
        }
        return PlaybackCoordinator(
            engine = engine,
            queueRepository = queueRepository,
            positionStore = positionStore,
            session = sessionManager,
            scope = scope,
        ).start()
    }

    fun persistedPosition(mediaKey: String): Long =
        runBlocking {
            positionStore.resumePosition(mediaKey) ?: 0L
        }

    override fun close() {
        onMain { controller?.close() }
        playbackScope.cancel()
        database.close()
        delegate.close()
    }
}

internal class FailOncePositionStore(
    private val delegate: PlaybackPositionStore,
) : PlaybackPositionStore {
    private val mutableFailNextRecord = AtomicBoolean(false)
    private val mutableRecordCalls = AtomicInteger(0)
    @Volatile
    private var mutableLastRecordSummary: String? = null

    var failNextRecord: Boolean
        get() = mutableFailNextRecord.get()
        set(value) {
            mutableFailNextRecord.set(value)
        }

    val recordCalls: Int
        get() = mutableRecordCalls.get()

    val lastRecordSummary: String?
        get() = mutableLastRecordSummary

    override suspend fun resumePosition(mediaKey: String): Long? =
        delegate.resumePosition(mediaKey)

    override suspend fun record(
        mediaKey: String,
        positionMs: Long,
        durationMs: Long,
        updatedAtEpochMs: Long,
        ended: Boolean,
    ) {
        mutableRecordCalls.incrementAndGet()
        mutableLastRecordSummary =
            "mediaKey=$mediaKey, positionMs=$positionMs, " +
                "durationMs=$durationMs, ended=$ended"
        if (mutableFailNextRecord.compareAndSet(true, false)) {
            throw IOException("position save fixture failure")
        }
        delegate.record(
            mediaKey = mediaKey,
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAtEpochMs = updatedAtEpochMs,
            ended = ended,
        )
    }

    override suspend fun clear(mediaKey: String) {
        delegate.clear(mediaKey)
    }
}

private class FixtureServerSessionManager(
    endpoint: SessionEndpoint,
) : ServerSessionManager {
    private val mutable = MutableStateFlow<ServerSessionState>(
        ServerSessionState.Connected(endpoint, listOf(endpoint.ipv4)),
    )
    override val state: StateFlow<ServerSessionState> = mutable

    override suspend fun connectSaved() = Unit

    override suspend fun testCandidate(
        input: String,
    ): AppResult<ConnectionTestResult> =
        error("not used by background playback tests: $input")

    override suspend fun saveCandidate(
        result: ConnectionTestResult,
    ) = error("not used by background playback tests: $result")

    override suspend fun refreshAfterRequestFailure(): AppResult<SessionEndpoint> =
        AppResult.Success(
            (mutable.value as ServerSessionState.Connected).endpoint,
        )
}

private class CountingPlaybackEngine(
    private val delegate: PlaybackEngine,
    private val onClose: () -> Unit,
) : PlaybackEngine by delegate {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        delegate.close()
        onClose()
    }
}

private fun <T> onMain(block: () -> T): T {
    if (Looper.myLooper() == Looper.getMainLooper()) return block()
    val task = FutureTask(block)
    InstrumentationRegistry.getInstrumentation().runOnMainSync(task)
    return task.get()
}
