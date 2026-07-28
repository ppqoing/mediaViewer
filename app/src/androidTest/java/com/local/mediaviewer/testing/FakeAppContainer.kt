package com.local.mediaviewer.testing

import android.content.Context
import android.view.SurfaceView
import coil3.ImageLoader
import com.local.mediaviewer.app.AppContainer
import com.local.mediaviewer.browser.Breadcrumb
import com.local.mediaviewer.browser.BrowserPage
import com.local.mediaviewer.browser.BrowserRepository
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.image.MediaImageLoaderFactory
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
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import com.local.mediaviewer.settings.ServerSettingsRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAppContainer(
    context: Context,
) : AppContainer, AutoCloseable {
    private val endpoint = SessionEndpoint(
        logicalBaseUrl = "http://media.test:8080",
        requestBaseUrl = "http://127.0.0.1:8080",
        ipv4 = "127.0.0.1",
    )

    override val settingsRepository: ServerSettingsRepository =
        FakeServerSettingsRepository()
    override val sessionManager: ServerSessionManager =
        FakeServerSessionManager(endpoint)
    override val browserRepository: BrowserRepository =
        FakeBrowserRepository(endpoint)
    override val playbackEngineFactory =
        PlaybackEngineFactory {
            FakePlaybackEngine()
        }
    override val playbackPositionStore: PlaybackPositionStore =
        InMemoryPlaybackPositionStore()
    override val imageLoader: ImageLoader =
        MediaImageLoaderFactory.create(context)

    override fun close() {
        imageLoader.shutdown()
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
        return AppResult.Success(
            BrowserPage(
                root = root,
                logicalDirectoryUrl = logicalUrl,
                requestDirectoryUrl = requestDirectoryUrl,
                breadcrumbs = breadcrumbs,
                entries = listOf(
                    entry(
                        name = "样例.mp4",
                        logicalUrl = logicalUrl + "sample.mp4",
                        requestUrl =
                            requestDirectoryUrl + "sample.mp4",
                        kind = MediaKind.VIDEO,
                    ),
                    entry(
                        name = "样例.wav",
                        logicalUrl = logicalUrl + "sample.wav",
                        requestUrl =
                            requestDirectoryUrl + "sample.wav",
                        kind = MediaKind.AUDIO,
                    ),
                    entry(
                        name = "样例.png",
                        logicalUrl = logicalUrl + "sample.png",
                        requestUrl =
                            requestDirectoryUrl + "sample.png",
                        kind = MediaKind.IMAGE,
                    ),
                ),
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

    override fun attachVideoSurface(
        surfaceView: SurfaceView,
    ) = Unit

    override fun detachVideoSurface() = Unit

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
