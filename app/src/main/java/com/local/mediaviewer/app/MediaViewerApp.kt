package com.local.mediaviewer.app

import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import com.local.mediaviewer.browser.BrowserViewModel
import com.local.mediaviewer.browser.BrowserPlaybackAction
import com.local.mediaviewer.home.HomeViewModel
import com.local.mediaviewer.image.ImageReaderViewModel
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.RootShare
import com.local.mediaviewer.navigation.BrowserRoute
import com.local.mediaviewer.navigation.HomeRoute
import com.local.mediaviewer.navigation.ImageReaderRoute
import com.local.mediaviewer.navigation.PlayerRoute
import com.local.mediaviewer.navigation.SettingsRoute
import com.local.mediaviewer.player.PlayerRequest
import com.local.mediaviewer.player.PlayerViewModel
import com.local.mediaviewer.settings.SettingsViewModel
import com.local.mediaviewer.ui.browser.BrowserScreen
import com.local.mediaviewer.ui.home.HomeScreen
import com.local.mediaviewer.ui.image.ImageReaderScreen
import com.local.mediaviewer.ui.player.AudioPlayerScreen
import com.local.mediaviewer.ui.player.FullscreenController
import com.local.mediaviewer.ui.player.NowPlayingBar
import com.local.mediaviewer.ui.player.PlaybackQueueSheet
import com.local.mediaviewer.ui.player.SystemVolumeController
import com.local.mediaviewer.ui.player.VideoPlayerScreen
import com.local.mediaviewer.ui.player.WindowBrightnessController
import com.local.mediaviewer.ui.settings.SettingsScreen

@Composable
fun MediaViewerApp(container: AppContainer) {
    val navController = rememberNavController()
    val playbackSession by container.playbackController.sessionState
        .collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()
    var queueSheetVisible by remember { mutableStateOf(false) }
    val showsMiniPlayer = playbackSession.currentItem != null && (
        currentEntry?.destination?.hasRoute<HomeRoute>() == true ||
            currentEntry?.destination?.hasRoute<BrowserRoute>() == true
        )

    Box {
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
        ) {
        composable<HomeRoute> {
            val home: HomeViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        HomeViewModel(container.sessionManager)
                    }
                },
            )
            val state by home.uiState.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onRetry = home::retry,
                onOpenSettings = {
                    navController.navigate(SettingsRoute)
                },
                onOpenRoot = { root ->
                    navController.navigate(BrowserRoute(root.id))
                },
            )
        }
        composable<SettingsRoute> {
            val settings: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        SettingsViewModel(
                            settings = container.settingsRepository,
                            readerPreferences =
                                container.readerPreferencesRepository,
                            session = container.sessionManager,
                        )
                    }
                },
            )
            val state by settings.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(settings) {
                settings.saved.collect {
                    navController.popBackStack()
                }
            }
            SettingsScreen(
                state = state,
                onInputChanged = settings::onInputChanged,
                onTest = settings::testConnection,
                onSave = settings::save,
                onDefaultImageModeChanged =
                    settings::onDefaultImageModeChanged,
                onBack = { navController.popBackStack() },
            )
        }
        composable<BrowserRoute> { entry ->
            val route = entry.toRoute<BrowserRoute>()
            val root = RootShare.fromId(route.rootId)
            val browser: BrowserViewModel = viewModel(
                key = "browser:${root.id}",
                factory = viewModelFactory {
                    initializer {
                        BrowserViewModel(
                            root = root,
                            repository = container.browserRepository,
                        )
                    }
                },
            )
            val state by browser.uiState.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }
            LaunchedEffect(browser) {
                browser.mediaLaunches.collect { media ->
                    navController.navigate(
                        ImageReaderRoute(
                            rootId = media.rootId,
                            directoryLogicalUrl =
                                media.directoryLogicalUrl,
                            selectedLogicalUrl = media.logicalUrl,
                            selectedName = media.name,
                        ),
                    )
                }
            }
            LaunchedEffect(browser, snackbarHostState) {
                browser.playbackRequests.collect { request ->
                    when (request.action) {
                        BrowserPlaybackAction.PLAY_DIRECTORY -> {
                            container.playbackController.replaceQueue(
                                request.directoryItems,
                                request.selected.mediaKey,
                            )
                            navController.navigate(
                                PlayerRoute(request.selected.mediaKey),
                            )
                        }

                        BrowserPlaybackAction.PLAY_NEXT -> {
                            container.playbackController.playNext(
                                request.selected,
                            )
                            snackbarHostState.showSnackbar("已加入下一项播放")
                        }

                        BrowserPlaybackAction.ADD_TO_QUEUE -> {
                            container.playbackController.append(
                                request.selected,
                            )
                            snackbarHostState.showSnackbar("已添加到队列")
                        }
                    }
                }
            }
            BackHandler {
                if (!browser.goBack()) {
                    navController.popBackStack()
                }
            }
            BrowserScreen(
                state = state,
                onEntryClick = browser::open,
                onBreadcrumbClick = browser::openBreadcrumb,
                onPlaybackAction = browser::requestPlayback,
                snackbarHostState = snackbarHostState,
                onRetry = browser::retry,
                onBack = {
                    if (!browser.goBack()) {
                        navController.popBackStack()
                    }
                },
            )
        }
        composable<PlayerRoute> { entry ->
            val route = entry.toRoute<PlayerRoute>()
            val item = playbackSession.queue.items.firstOrNull {
                it.mediaKey == route.mediaKey
            } ?: return@composable
            val player: PlayerViewModel = viewModel(
                key = "player:${route.mediaKey}",
                factory = viewModelFactory {
                    initializer {
                        PlayerViewModel(
                            initialRequest = PlayerRequest(
                                name = item.name,
                                logicalUrl = item.logicalUrl,
                                requestUrl = item.logicalUrl,
                                mediaKey = route.mediaKey,
                                kind = item.kind,
                            ),
                            controller = container.playbackController,
                            positionStore =
                                container.playbackPositionStore,
                            session = container.sessionManager,
                            autoStart = false,
                        )
                    }
                },
            )
            val state by player.uiState.collectAsStateWithLifecycle()
            val activity = requireNotNull(LocalActivity.current) {
                "播放器必须托管在 Activity 中"
            }
            val fullscreenController = remember(activity) {
                FullscreenController(activity)
            }
            val volumeController = remember(activity) {
                SystemVolumeController(
                    requireNotNull(activity.getSystemService(AudioManager::class.java)),
                )
            }
            DisposableEffect(fullscreenController) {
                onDispose {
                    fullscreenController.close()
                }
            }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                volumeController.refresh()
            }
            val leave = {
                player.leave {
                    navController.popBackStack()
                }
            }

            if (item.kind == MediaKind.AUDIO) {
                AudioPlayerScreen(
                    state = state,
                    onPlay = player::play,
                    onPause = player::pause,
                    onReplay = player::replay,
                    onSeekBack = player::seekBack,
                    onSeekForward = player::seekForward,
                    onBeginScrub = player::beginScrub,
                    onPreviewScrub = player::previewScrub,
                    onCommitScrub = player::commitScrub,
                    onPrevious = player::previous,
                    onNext = player::next,
                    onSpeedChanged = player::setPlaybackSpeed,
                    playbackMode = playbackSession.queue.mode,
                    onPlaybackModeChanged = container.playbackController::setPlaybackMode,
                    onOpenQueue = { queueSheetVisible = true },
                    onRetry = player::retry,
                    volumeController = volumeController,
                    onResumeHintShown = player::onResumeHintShown,
                    onBack = leave,
                )
            } else {
                val brightnessController = remember(activity) {
                    WindowBrightnessController(activity)
                }
                DisposableEffect(brightnessController) {
                    onDispose(brightnessController::close)
                }
                VideoPlayerScreen(
                    state = state,
                    controller = player.controller,
                    fullscreenController = fullscreenController,
                    preferences = container.playerPreferencesRepository,
                    volumeController = volumeController,
                    brightnessController = brightnessController,
                    onPlay = player::play,
                    onPause = player::pause,
                    onReplay = player::replay,
                    onSeekBack = player::seekBack,
                    onSeekForward = player::seekForward,
                    onBeginScrub = player::beginScrub,
                    onPreviewScrub = player::previewScrub,
                    onCommitScrub = player::commitScrub,
                    onPrevious = player::previous,
                    onNext = player::next,
                    onSpeedChanged = player::setPlaybackSpeed,
                    playbackMode = playbackSession.queue.mode,
                    onPlaybackModeChanged = container.playbackController::setPlaybackMode,
                    onOpenQueue = { queueSheetVisible = true },
                    onRetry = player::retry,
                    onResumeHintShown = player::onResumeHintShown,
                    onVideoScaleModeChanged =
                        player::setVideoScaleMode,
                    onBack = leave,
                )
            }
        }
        composable<ImageReaderRoute> { entry ->
            val route =
                entry.toRoute<ImageReaderRoute>()
            val reader: ImageReaderViewModel = viewModel(
                key =
                    "image-reader:" +
                        route.selectedLogicalUrl,
                factory = viewModelFactory {
                    initializer {
                        ImageReaderViewModel(
                            directoryLogicalUrl =
                                route.directoryLogicalUrl,
                            selectedLogicalUrl =
                                route.selectedLogicalUrl,
                            contentRepository =
                                container
                                    .directoryContentRepository,
                            preferences =
                                container
                                    .readerPreferencesRepository,
                            session =
                                container.sessionManager,
                        )
                    }
                },
            )
            val state by
                reader.uiState
                    .collectAsStateWithLifecycle()
            ImageReaderScreen(
                state = state,
                imageLoader = container.imageLoader,
                onModeChanged = reader::setMode,
                onSortChanged =
                    reader::setSortOrder,
                onAnchorChanged =
                    reader::updateAnchor,
                onRetryDirectory =
                    reader::retryDirectory,
                onImageLoadError =
                    reader::onImageLoadError,
                onImageLoadSuccess =
                    reader::onImageLoadSuccess,
                onRetryImage =
                    reader::retryImage,
                onBack = {
                    navController.popBackStack()
                },
            )
        }
    }
        if (showsMiniPlayer) {
            NowPlayingBar(
                state = playbackSession,
                onToggle = {
                    if (playbackSession.playback.status ==
                        com.local.mediaviewer.playback.PlaybackStatus.PLAYING
                    ) container.playbackController.pause()
                    else container.playbackController.play()
                },
                onNext = container.playbackController::skipNext,
                onOpenQueue = { queueSheetVisible = true },
                onOpenPlayer = {
                    playbackSession.currentItem?.let { item ->
                        navController.navigate(PlayerRoute(item.mediaKey)) {
                            launchSingleTop = true
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (queueSheetVisible) {
            PlaybackQueueSheet(
                queue = playbackSession.queue,
                onSelect = {
                    container.playbackController.select(it)
                    queueSheetVisible = false
                },
                onMove = container.playbackController::move,
                onRemove = container.playbackController::remove,
                onClearExceptCurrent = container.playbackController::clearExceptCurrent,
                onStopAndClear = container.playbackController::clearAll,
                onDismiss = { queueSheetVisible = false },
            )
        }
    }
}
