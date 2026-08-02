package com.local.mediaviewer.app

import android.content.Context
import coil3.ImageLoader
import com.local.mediaviewer.browser.BrowserRepository
import com.local.mediaviewer.browser.DefaultBrowserRepository
import com.local.mediaviewer.browser.DefaultDirectoryContentRepository
import com.local.mediaviewer.browser.DirectoryContentRepository
import com.local.mediaviewer.image.DataStoreReaderPreferencesRepository
import com.local.mediaviewer.image.MediaImageLoaderFactory
import com.local.mediaviewer.image.ReaderPreferencesRepository
import com.local.mediaviewer.image.readerPreferencesDataStore
import com.local.mediaviewer.network.DefaultCaddyDirectoryClient
import com.local.mediaviewer.network.DefaultConnectionProbe
import com.local.mediaviewer.network.DefaultDirectoryJsonParser
import com.local.mediaviewer.network.DefaultShareDiscoveryParser
import com.local.mediaviewer.network.OkHttpShareDiscoveryTransport
import com.local.mediaviewer.network.SystemIpv4Resolver
import com.local.mediaviewer.playback.AndroidVlcPlaybackEngine
import com.local.mediaviewer.playback.MediaViewerDatabase
import com.local.mediaviewer.playback.MediaViewerDatabaseFactory
import com.local.mediaviewer.playback.PlaybackEngine
import com.local.mediaviewer.playback.PlaybackEngineFactory
import com.local.mediaviewer.playback.PlaybackPositionStore
import com.local.mediaviewer.playback.RoomPlaybackPositionStore
import com.local.mediaviewer.pdf.AndroidPdfDocumentFactory
import com.local.mediaviewer.pdf.DefaultPdfFileClient
import com.local.mediaviewer.pdf.DefaultPdfTemporaryFileRepository
import com.local.mediaviewer.pdf.PdfDocumentFactory
import com.local.mediaviewer.pdf.PdfTemporaryFileRepository
import com.local.mediaviewer.player.Media3PlaybackController
import com.local.mediaviewer.player.QueuePlaybackController
import com.local.mediaviewer.queue.PlaybackCoordinator
import com.local.mediaviewer.queue.PlaybackQueueRepository
import com.local.mediaviewer.queue.RoomPlaybackQueueRepository
import com.local.mediaviewer.session.DefaultServerSessionManager
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.settings.DataStoreServerSettingsRepository
import com.local.mediaviewer.settings.DataStorePlayerPreferencesRepository
import com.local.mediaviewer.settings.PlayerPreferencesRepository
import com.local.mediaviewer.settings.ServerSettingsRepository
import com.local.mediaviewer.settings.playerPreferencesDataStore
import com.local.mediaviewer.settings.serverSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

interface AppContainer {
    val settingsRepository: ServerSettingsRepository
    val readerPreferencesRepository: ReaderPreferencesRepository
    val playerPreferencesRepository: PlayerPreferencesRepository
    val sessionManager: ServerSessionManager
    val directoryContentRepository: DirectoryContentRepository
    val browserRepository: BrowserRepository
    val playbackController: QueuePlaybackController
    val playbackPositionStore: PlaybackPositionStore
    val imageLoader: ImageLoader
    val pdfTemporaryFileRepository: PdfTemporaryFileRepository
    val pdfDocumentFactory: PdfDocumentFactory

    fun createPlaybackCoordinator(scope: CoroutineScope): PlaybackCoordinator
}

class DefaultAppContainer(
    context: Context,
    private val playbackEngineFactory: PlaybackEngineFactory =
        PlaybackEngineFactory { AndroidVlcPlaybackEngine(context.applicationContext) },
) : AppContainer {
    private val appContext = context.applicationContext
    override val settingsRepository: ServerSettingsRepository =
        DataStoreServerSettingsRepository(context.serverSettingsDataStore)
    override val readerPreferencesRepository:
        ReaderPreferencesRepository =
        DataStoreReaderPreferencesRepository(
            appContext.readerPreferencesDataStore,
        )
    override val playerPreferencesRepository: PlayerPreferencesRepository =
        DataStorePlayerPreferencesRepository(appContext.playerPreferencesDataStore)

    private val directoryParser = DefaultDirectoryJsonParser()
    private val directoryClient = DefaultCaddyDirectoryClient(
        parser = directoryParser,
    )
    private val resolver = SystemIpv4Resolver()
    private val probe = DefaultConnectionProbe(
        transport = OkHttpShareDiscoveryTransport(),
        parser = DefaultShareDiscoveryParser(),
    )

    override val sessionManager: ServerSessionManager =
        DefaultServerSessionManager(
            settings = settingsRepository,
            resolver = resolver,
            probe = probe,
        )

    override val directoryContentRepository:
        DirectoryContentRepository =
        DefaultDirectoryContentRepository(
            directoryClient = directoryClient,
            session = sessionManager,
        )

    override val browserRepository: BrowserRepository =
        DefaultBrowserRepository(
            contentRepository = directoryContentRepository,
            session = sessionManager,
        )

    private val database: MediaViewerDatabase by lazy {
        MediaViewerDatabaseFactory.create(appContext)
    }

    override val playbackPositionStore: PlaybackPositionStore by lazy {
        RoomPlaybackPositionStore(database.playbackPositionDao())
    }

    private val playbackQueueRepository: PlaybackQueueRepository by lazy {
        RoomPlaybackQueueRepository(database.playbackQueueDao())
    }

    private val playbackScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate,
    )

    override val imageLoader: ImageLoader by lazy {
        MediaImageLoaderFactory.create(appContext)
    }

    override val pdfTemporaryFileRepository: PdfTemporaryFileRepository =
        DefaultPdfTemporaryFileRepository(
            cacheRoot = appContext.cacheDir,
            client = DefaultPdfFileClient(),
            session = sessionManager,
        )

    override val pdfDocumentFactory: PdfDocumentFactory =
        AndroidPdfDocumentFactory()

    private val playbackEngineLock = Any()
    private var activePlaybackEngine: PlaybackEngine? = null
    override val playbackController: QueuePlaybackController by lazy {
        Media3PlaybackController(
            context = appContext,
            scope = playbackScope,
        )
    }

    override fun createPlaybackCoordinator(
        scope: CoroutineScope,
    ): PlaybackCoordinator =
        synchronized(playbackEngineLock) {
            activePlaybackEngine?.close()
            playbackEngineFactory.create().also { engine ->
                activePlaybackEngine = engine
            }.let { engine ->
                PlaybackCoordinator(
                    engine = engine,
                    queueRepository = playbackQueueRepository,
                    positionStore = playbackPositionStore,
                    session = sessionManager,
                    scope = scope,
                ).start()
            }
        }
}
