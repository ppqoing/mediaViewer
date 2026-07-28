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
import com.local.mediaviewer.playback.PlaybackEngine
import com.local.mediaviewer.playback.PlaybackEngineFactory
import com.local.mediaviewer.playback.PlaybackPositionStore
import com.local.mediaviewer.playback.PlaybackState
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.playback.VideoScaleMode
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import com.local.mediaviewer.settings.ServerSettingsRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
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
    override val playbackEngineFactory =
        PlaybackEngineFactory {
            FakePlaybackEngine()
        }
    override val playbackPositionStore: PlaybackPositionStore =
        InMemoryPlaybackPositionStore()
    override val imageLoader: ImageLoader =
        MediaImageLoaderFactory.create(context)

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

private class FakePlaybackEngine : PlaybackEngine {
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

    override fun seekTo(positionMs: Long) {
        mutable.value = mutable.value.copy(
            positionMs = positionMs,
        )
    }

    override fun close() = Unit
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
