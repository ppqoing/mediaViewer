package com.local.mediaviewer.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.local.mediaviewer.browser.BrowserViewModel
import com.local.mediaviewer.home.HomeViewModel
import com.local.mediaviewer.image.ImageViewerViewModel
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.RootShare
import com.local.mediaviewer.navigation.BrowserRoute
import com.local.mediaviewer.navigation.HomeRoute
import com.local.mediaviewer.navigation.ImageRoute
import com.local.mediaviewer.navigation.PlayerRoute
import com.local.mediaviewer.navigation.SettingsRoute
import com.local.mediaviewer.player.PlayerRequest
import com.local.mediaviewer.player.PlayerViewModel
import com.local.mediaviewer.settings.SettingsViewModel
import com.local.mediaviewer.ui.browser.BrowserScreen
import com.local.mediaviewer.ui.home.HomeScreen
import com.local.mediaviewer.ui.image.ImageViewerScreen
import com.local.mediaviewer.ui.player.AudioPlayerScreen
import com.local.mediaviewer.ui.player.FullscreenController
import com.local.mediaviewer.ui.player.VideoPlayerScreen
import com.local.mediaviewer.ui.settings.SettingsScreen

@Composable
fun MediaViewerApp(container: AppContainer) {
    val navController = rememberNavController()
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
            LaunchedEffect(browser) {
                browser.mediaLaunches.collect { media ->
                    if (media.kind == MediaKind.IMAGE) {
                        navController.navigate(
                            ImageRoute(
                                name = media.name,
                                logicalUrl = media.logicalUrl,
                                requestUrl = media.requestUrl,
                            ),
                        )
                    } else {
                        navController.navigate(
                            PlayerRoute(
                                name = media.name,
                                logicalUrl = media.logicalUrl,
                                requestUrl = media.requestUrl,
                                mediaKey = media.mediaKey,
                                kind = media.kind,
                            ),
                        )
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
            val player: PlayerViewModel = viewModel(
                key = "player:${route.mediaKey}",
                factory = viewModelFactory {
                    initializer {
                        PlayerViewModel(
                            initialRequest = PlayerRequest(
                                name = route.name,
                                logicalUrl = route.logicalUrl,
                                requestUrl = route.requestUrl,
                                mediaKey = route.mediaKey,
                                kind = route.kind,
                            ),
                            engine =
                                container.playbackEngineFactory.create(),
                            positionStore =
                                container.playbackPositionStore,
                            session = container.sessionManager,
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
            DisposableEffect(fullscreenController) {
                onDispose {
                    fullscreenController.close()
                }
            }
            LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
                player.onBackgrounded()
            }
            val leave = {
                player.leave {
                    navController.popBackStack()
                }
            }

            if (route.kind == MediaKind.AUDIO) {
                AudioPlayerScreen(
                    state = state,
                    onPlay = player::play,
                    onPause = player::pause,
                    onSeek = player::seekTo,
                    onBack = leave,
                )
            } else {
                VideoPlayerScreen(
                    state = state,
                    engine = player.engine,
                    fullscreenController = fullscreenController,
                    onPlay = player::play,
                    onPause = player::pause,
                    onSeek = player::seekTo,
                    onBack = leave,
                )
            }
        }
        composable<ImageRoute> { entry ->
            val route = entry.toRoute<ImageRoute>()
            val viewer: ImageViewerViewModel = viewModel(
                key = "image:${route.logicalUrl}",
                factory = viewModelFactory {
                    initializer {
                        ImageViewerViewModel(
                            logicalUrl = route.logicalUrl,
                            initialRequestUrl = route.requestUrl,
                            session = container.sessionManager,
                        )
                    }
                },
            )
            val state by viewer.uiState.collectAsStateWithLifecycle()
            ImageViewerScreen(
                name = route.name,
                state = state,
                imageLoader = container.imageLoader,
                onLoadError = viewer::onLoadError,
                onRetry = viewer::retry,
                onBack = {
                    navController.popBackStack()
                },
            )
        }
    }
}
