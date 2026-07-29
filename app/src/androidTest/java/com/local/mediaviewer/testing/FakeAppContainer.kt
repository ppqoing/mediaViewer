package com.local.mediaviewer.testing

import android.content.Context
import android.view.ViewGroup
import coil3.ImageLoader
import com.local.mediaviewer.app.AppContainer
import com.local.mediaviewer.browser.Breadcrumb
import com.local.mediaviewer.browser.BrowserPage
import com.local.mediaviewer.browser.BrowserRepository
import com.local.mediaviewer.browser.DirectoryContent
import com.local.mediaviewer.browser.DirectoryContentRepository
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.image.ImageReaderMode
import com.local.mediaviewer.image.MediaImageLoaderFactory
import com.local.mediaviewer.image.ReaderPreferencesRepository
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.RootShare
import com.local.mediaviewer.model.ServerConfig
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.network.ConnectionTestResult
import com.local.mediaviewer.playback.PlaybackPositionStore
import com.local.mediaviewer.playback.PlaybackEngine
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.playback.VideoScaleMode
import com.local.mediaviewer.player.PlaybackController
import com.local.mediaviewer.player.QueuePlaybackController
import com.local.mediaviewer.queue.PlaybackMode
import com.local.mediaviewer.queue.PlaybackCoordinator
import com.local.mediaviewer.queue.PlaybackQueue
import com.local.mediaviewer.queue.PlaybackQueueRepository
import com.local.mediaviewer.queue.PlaybackSessionState
import com.local.mediaviewer.queue.QueueMediaItem
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import com.local.mediaviewer.settings.ServerSettingsRepository
import com.local.mediaviewer.settings.PlayerPreferencesRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAppContainer(
    context: Context,
    initialReaderMode:
        ImageReaderMode = ImageReaderMode.COMIC,
    directoryContent:
        DirectoryContent = defaultDirectoryContent(),
) : AppContainer, AutoCloseable {
    private val endpoint = SessionEndpoint(
        logicalBaseUrl = FAKE_LOGICAL_BASE_URL,
        requestBaseUrl = FAKE_REQUEST_BASE_URL,
        ipv4 = "127.0.0.1",
    )

    override val settingsRepository: ServerSettingsRepository =
        FakeServerSettingsRepository()
    private val readerPreferences =
        FakeReaderPreferencesRepository(
            initialReaderMode,
        )
    override val readerPreferencesRepository:
        ReaderPreferencesRepository =
        readerPreferences
    override val playerPreferencesRepository: PlayerPreferencesRepository =
        FakePlayerPreferencesRepository()
    override val sessionManager: ServerSessionManager =
        FakeServerSessionManager(endpoint)
    override val directoryContentRepository:
        DirectoryContentRepository =
        FakeDirectoryContentRepository(
            endpoint = endpoint,
            template = directoryContent,
        )
    override val browserRepository: BrowserRepository =
        FakeBrowserRepository(
            endpoint = endpoint,
            template = directoryContent,
        )
    val fakePlaybackController = FakeQueuePlaybackController()
    override val playbackController: QueuePlaybackController =
        fakePlaybackController
    override val playbackPositionStore: PlaybackPositionStore =
        InMemoryPlaybackPositionStore()
    override val imageLoader: ImageLoader =
        MediaImageLoaderFactory.create(context)
    var playbackEngineCreationCount: Int = 0
        private set
    var playbackEngineCloseCount: Int = 0
        private set

    override fun createPlaybackCoordinator(
        scope: CoroutineScope,
    ): PlaybackCoordinator {
        playbackEngineCreationCount += 1
        return PlaybackCoordinator(
            engine = FakeServicePlaybackEngine {
                playbackEngineCloseCount += 1
            },
            queueRepository = InMemoryPlaybackQueueRepository(),
            positionStore = playbackPositionStore,
            session = sessionManager,
            scope = scope,
        ).start()
    }

    val savedReaderModes: List<ImageReaderMode>
        get() = readerPreferences.savedModes.toList()

    override fun close() {
        imageLoader.shutdown()
    }
}

private class FakeReaderPreferencesRepository(
    initialMode: ImageReaderMode,
) :
    ReaderPreferencesRepository {
    private val mutable =
        MutableStateFlow(initialMode)
    val savedModes =
        mutableListOf<ImageReaderMode>()
    override val defaultMode: Flow<ImageReaderMode> = mutable

    override suspend fun currentDefaultMode(): ImageReaderMode =
        mutable.value

    override suspend fun setDefaultMode(mode: ImageReaderMode) {
        savedModes += mode
        mutable.value = mode
    }
}

private class FakePlayerPreferencesRepository : PlayerPreferencesRepository {
    private val mutable = MutableStateFlow(false)
    override val hasShownVideoGestures: Flow<Boolean> = mutable

    override suspend fun markVideoGesturesShown() {
        mutable.value = true
    }
}

private class FakeDirectoryContentRepository(
    private val endpoint: SessionEndpoint,
    private val template: DirectoryContent,
) : DirectoryContentRepository {
    override suspend fun load(
        logicalDirectoryUrl: String,
    ): AppResult<DirectoryContent> {
        return AppResult.Success(
            rebaseDirectoryContent(
                template = template,
                logicalDirectoryUrl =
                    logicalDirectoryUrl,
                requestDirectoryUrl =
                    endpoint.requestUrlFor(
                        logicalDirectoryUrl,
                    ),
            ),
        )
    }
}

private class FakeServerSettingsRepository :
    ServerSettingsRepository {
    private val mutable = MutableStateFlow(
        ServerConfig("http://media.test:8080"),
    )
    override val config: Flow<ServerConfig> = mutable

    override suspend fun current(): ServerConfig =
        mutable.value

    override suspend fun save(config: ServerConfig) {
        mutable.value = config
    }
}

private class FakeServerSessionManager(
    private val endpoint: SessionEndpoint,
) : ServerSessionManager {
    private val mutable = MutableStateFlow<ServerSessionState>(
        ServerSessionState.Connected(
            endpoint,
            listOf(endpoint.ipv4),
        ),
    )
    override val state: StateFlow<ServerSessionState> = mutable

    override suspend fun connectSaved() {
        mutable.value = ServerSessionState.Connected(
            endpoint,
            listOf(endpoint.ipv4),
        )
    }

    override suspend fun testCandidate(
        input: String,
    ): AppResult<ConnectionTestResult> =
        error("导航测试不进入设置探测：$input")

    override suspend fun saveCandidate(
        result: ConnectionTestResult,
    ) {
        error(
            "导航测试不保存设置：" +
                result.endpoint.logicalBaseUrl,
        )
    }

    override suspend fun refreshAfterRequestFailure():
        AppResult<SessionEndpoint> =
        AppResult.Success(endpoint)
}

private class FakeBrowserRepository(
    private val endpoint: SessionEndpoint,
    private val template: DirectoryContent,
) : BrowserRepository {
    override suspend fun openRoot(
        root: RootShare,
    ): AppResult<BrowserPage> {
        val logical = endpoint.logicalBaseUrl + root.path
        val request = endpoint.requestBaseUrl + root.path
        val folder = entry(
            name = "示例目录",
            logicalUrl = logical + "nested/",
            requestUrl = request + "nested/",
            kind = MediaKind.DIRECTORY,
        )
        return AppResult.Success(
            BrowserPage(
                root = root,
                logicalDirectoryUrl = logical,
                requestDirectoryUrl = request,
                breadcrumbs = listOf(
                    Breadcrumb(root.displayName, logical),
                ),
                entries = listOf(folder),
            ),
        )
    }

    override suspend fun openDirectory(
        root: RootShare,
        logicalUrl: String,
        breadcrumbs: List<Breadcrumb>,
    ): AppResult<BrowserPage> {
        val requestDirectoryUrl =
            endpoint.requestUrlFor(logicalUrl)
        val content = rebaseDirectoryContent(
            template = template,
            logicalDirectoryUrl = logicalUrl,
            requestDirectoryUrl =
                requestDirectoryUrl,
        )
        return AppResult.Success(
            BrowserPage(
                root = root,
                logicalDirectoryUrl = logicalUrl,
                requestDirectoryUrl = requestDirectoryUrl,
                breadcrumbs = breadcrumbs,
                entries = content.entries,
            ),
        )
    }

    private fun entry(
        name: String,
        logicalUrl: String,
        requestUrl: String,
        kind: MediaKind,
    ) = DirectoryEntry(
        name = name,
        size = 1_536L,
        modifiedAt =
            Instant.parse("2026-07-28T00:00:00Z"),
        mode = 420L,
        isDirectory = kind == MediaKind.DIRECTORY,
        isSymlink = false,
        logicalUrl = logicalUrl,
        requestUrl = requestUrl,
        kind = kind,
    )
}

fun defaultDirectoryContent(): DirectoryContent {
    val logicalDirectoryUrl =
        "$FAKE_LOGICAL_BASE_URL/middle/nested/"
    val requestDirectoryUrl =
        "$FAKE_REQUEST_BASE_URL/middle/nested/"
    return DirectoryContent(
        logicalDirectoryUrl = logicalDirectoryUrl,
        requestDirectoryUrl = requestDirectoryUrl,
        entries = listOf(
            fixtureEntry(
                name = "样例.mp4",
                relativeUrl = "sample.mp4",
                size = 4_096L,
                modifiedAt =
                    "2026-07-28T04:00:00Z",
                kind = MediaKind.VIDEO,
                logicalDirectoryUrl =
                    logicalDirectoryUrl,
                requestDirectoryUrl =
                    requestDirectoryUrl,
            ),
            fixtureEntry(
                name = "样例.wav",
                relativeUrl = "sample.wav",
                size = 3_072L,
                modifiedAt =
                    "2026-07-28T03:00:00Z",
                kind = MediaKind.AUDIO,
                logicalDirectoryUrl =
                    logicalDirectoryUrl,
                requestDirectoryUrl =
                    requestDirectoryUrl,
            ),
            fixtureEntry(
                name = "样例.png",
                relativeUrl = "sample.png",
                size = 2_048L,
                modifiedAt =
                    "2026-07-28T02:00:00Z",
                kind = MediaKind.IMAGE,
                logicalDirectoryUrl =
                    logicalDirectoryUrl,
                requestDirectoryUrl =
                    requestDirectoryUrl,
            ),
            fixtureEntry(
                name = "001.jpg",
                relativeUrl = "001.jpg",
                size = 300L,
                modifiedAt =
                    "2026-07-28T03:00:00Z",
                kind = MediaKind.IMAGE,
                logicalDirectoryUrl =
                    logicalDirectoryUrl,
                requestDirectoryUrl =
                    requestDirectoryUrl,
            ),
            fixtureEntry(
                name = "002.jpg",
                relativeUrl = "002.jpg",
                size = 100L,
                modifiedAt =
                    "2026-07-28T01:00:00Z",
                kind = MediaKind.IMAGE,
                logicalDirectoryUrl =
                    logicalDirectoryUrl,
                requestDirectoryUrl =
                    requestDirectoryUrl,
            ),
            fixtureEntry(
                name = "003.jpg",
                relativeUrl = "003.jpg",
                size = 200L,
                modifiedAt =
                    "2026-07-28T02:00:00Z",
                kind = MediaKind.IMAGE,
                logicalDirectoryUrl =
                    logicalDirectoryUrl,
                requestDirectoryUrl =
                    requestDirectoryUrl,
            ),
            fixtureEntry(
                name = "clip.mp4",
                relativeUrl = "clip.mp4",
                size = 5_120L,
                modifiedAt =
                    "2026-07-28T05:00:00Z",
                kind = MediaKind.VIDEO,
                logicalDirectoryUrl =
                    logicalDirectoryUrl,
                requestDirectoryUrl =
                    requestDirectoryUrl,
            ),
            fixtureEntry(
                name = "subfolder",
                relativeUrl = "subfolder/",
                size = 0L,
                modifiedAt =
                    "2026-07-28T00:00:00Z",
                kind = MediaKind.DIRECTORY,
                logicalDirectoryUrl =
                    logicalDirectoryUrl,
                requestDirectoryUrl =
                    requestDirectoryUrl,
            ),
        ),
    )
}

private fun rebaseDirectoryContent(
    template: DirectoryContent,
    logicalDirectoryUrl: String,
    requestDirectoryUrl: String,
): DirectoryContent =
    DirectoryContent(
        logicalDirectoryUrl = logicalDirectoryUrl,
        requestDirectoryUrl = requestDirectoryUrl,
        entries = template.entries.map { entry ->
            val relativeUrl =
                entry.logicalUrl.removePrefix(
                    template.logicalDirectoryUrl,
                )
            check(relativeUrl != entry.logicalUrl) {
                "测试目录条目必须属于模板目录"
            }
            entry.copy(
                logicalUrl =
                    logicalDirectoryUrl + relativeUrl,
                requestUrl =
                    requestDirectoryUrl + relativeUrl,
            )
        },
    )

private fun fixtureEntry(
    name: String,
    relativeUrl: String,
    size: Long,
    modifiedAt: String,
    kind: MediaKind,
    logicalDirectoryUrl: String,
    requestDirectoryUrl: String,
) = DirectoryEntry(
    name = name,
    size = size,
    modifiedAt = Instant.parse(modifiedAt),
    mode = 420L,
    isDirectory = kind == MediaKind.DIRECTORY,
    isSymlink = false,
    logicalUrl =
        logicalDirectoryUrl + relativeUrl,
    requestUrl =
        requestDirectoryUrl + relativeUrl,
    kind = kind,
)

private class FakePlaybackController : PlaybackController {
    private val mutable = MutableStateFlow(
        PlaybackState(
            status = PlaybackStatus.IDLE,
            durationMs = 60_000L,
            isSeekable = true,
        ),
    )
    override val state: StateFlow<PlaybackState> = mutable

    override fun prepare(url: String) {
        mutable.value = mutable.value.copy(
            status = PlaybackStatus.PAUSED,
        )
    }

    override fun attachVideoOutput(host: ViewGroup) = Unit

    override fun detachVideoOutput() = Unit

    override fun setVideoScaleMode(mode: VideoScaleMode) = Unit

    override fun setPlaybackSpeed(speed: Float) = Unit

    override fun play() {
        mutable.value = mutable.value.copy(
            status = PlaybackStatus.PLAYING,
        )
    }

    override fun pause() {
        mutable.value = mutable.value.copy(
            status = PlaybackStatus.PAUSED,
        )
    }

    override fun stop() = Unit

    override fun seekTo(positionMs: Long) {
        mutable.value = mutable.value.copy(
            positionMs = positionMs,
        )
    }

    override fun close() = Unit

    fun emit(state: PlaybackState) {
        mutable.value = state
    }
}

class FakeQueuePlaybackController : QueuePlaybackController {
    private val playback = FakePlaybackController()
    private val mutableSession = MutableStateFlow(PlaybackSessionState())

    override val state: StateFlow<PlaybackState> = playback.state
    override val sessionState: StateFlow<PlaybackSessionState> = mutableSession

    override fun prepare(url: String) = playback.prepare(url)

    override fun play() = playback.play()

    override fun pause() = playback.pause()

    override fun stop() = playback.stop()

    override fun seekTo(positionMs: Long) = playback.seekTo(positionMs)

    override fun setPlaybackSpeed(speed: Float) = playback.setPlaybackSpeed(speed)

    override fun attachVideoOutput(host: ViewGroup) = playback.attachVideoOutput(host)

    override fun detachVideoOutput() = playback.detachVideoOutput()

    override fun setVideoScaleMode(mode: VideoScaleMode) =
        playback.setVideoScaleMode(mode)

    override fun replaceQueue(items: List<QueueMediaItem>, startMediaKey: String) {
        updateQueue(items, startMediaKey)
        playback.play()
    }

    override fun playNext(item: QueueMediaItem) {
        val queue = mutableSession.value.queue
        val existing = queue.items.filterNot { it.mediaKey == item.mediaKey }
        val insertAt = (queue.currentIndex + 1).coerceIn(0, existing.size)
        updateQueue(
            existing.toMutableList().apply { add(insertAt, item) },
            queue.currentMediaKey,
        )
    }

    override fun append(item: QueueMediaItem) {
        val queue = mutableSession.value.queue
        updateQueue(
            queue.items.filterNot { it.mediaKey == item.mediaKey } + item,
            queue.currentMediaKey,
        )
    }

    override fun select(mediaKey: String) {
        val queue = mutableSession.value.queue
        if (queue.items.any { it.mediaKey == mediaKey }) {
            updateQueue(queue.items, mediaKey)
        }
    }

    override fun reloadCurrent() = Unit

    override fun skipPrevious() = Unit

    override fun skipNext() = Unit

    override fun move(mediaKey: String, toIndex: Int) = Unit

    override fun remove(mediaKey: String) = Unit

    override fun clearExceptCurrent() = Unit

    override fun clearAll() = updateQueue(emptyList(), null)

    override fun setPlaybackMode(mode: PlaybackMode) {
        mutableSession.value = mutableSession.value.copy(
            queue = mutableSession.value.queue.copy(mode = mode),
        )
    }

    override fun close() = Unit

    fun emitSessionState(state: PlaybackSessionState) {
        mutableSession.value = state
        playback.emit(state.playback)
    }

    private fun updateQueue(
        items: List<QueueMediaItem>,
        currentMediaKey: String?,
    ) {
        val current = currentMediaKey ?: items.firstOrNull()?.mediaKey
        val queue = PlaybackQueue(items = items, currentMediaKey = current)
        mutableSession.value = mutableSession.value.copy(
            queue = queue,
            currentItem = queue.currentItem,
        )
    }
}

private const val FAKE_LOGICAL_BASE_URL =
    "http://media.test:8080"
private const val FAKE_REQUEST_BASE_URL =
    "http://127.0.0.1:8080"

private class InMemoryPlaybackPositionStore :
    PlaybackPositionStore {
    private val positions = mutableMapOf<String, Long>()

    override suspend fun resumePosition(
        mediaKey: String,
    ): Long? = positions[mediaKey]

    override suspend fun record(
        mediaKey: String,
        positionMs: Long,
        durationMs: Long,
        updatedAtEpochMs: Long,
        ended: Boolean,
    ) {
        if (ended) {
            positions.remove(mediaKey)
        } else {
            positions[mediaKey] = positionMs
        }
    }

    override suspend fun clear(mediaKey: String) {
        positions.remove(mediaKey)
    }
}

private class InMemoryPlaybackQueueRepository : PlaybackQueueRepository {
    private val mutable = MutableStateFlow(PlaybackQueue())
    override val queue: StateFlow<PlaybackQueue> = mutable

    override suspend fun restore(): PlaybackQueue = mutable.value

    override suspend fun save(queue: PlaybackQueue) {
        mutable.value = queue
    }
}

private class FakeServicePlaybackEngine(
    private val onClose: () -> Unit,
) : PlaybackEngine {
    private val mutable = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutable
    private var closed = false

    override fun prepare(url: String) {
        check(!closed)
        mutable.value = mutable.value.copy(status = PlaybackStatus.PAUSED)
    }

    override fun attachVideoOutput(host: ViewGroup) = Unit

    override fun detachVideoOutput() = Unit

    override fun setVideoScaleMode(mode: VideoScaleMode) = Unit

    override fun setPlaybackSpeed(speed: Float) = Unit

    override fun play() {
        mutable.value = mutable.value.copy(status = PlaybackStatus.PLAYING)
    }

    override fun pause() {
        mutable.value = mutable.value.copy(status = PlaybackStatus.PAUSED)
    }

    override fun stop() {
        mutable.value = mutable.value.copy(status = PlaybackStatus.IDLE)
    }

    override fun seekTo(positionMs: Long) {
        mutable.value = mutable.value.copy(positionMs = positionMs)
    }

    override fun close() {
        if (closed) return
        closed = true
        onClose()
    }
}
