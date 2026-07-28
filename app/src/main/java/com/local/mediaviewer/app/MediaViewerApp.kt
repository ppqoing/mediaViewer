package com.local.mediaviewer.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.model.RootShare
import com.local.mediaviewer.navigation.BrowserRoute
import com.local.mediaviewer.navigation.HomeRoute
import com.local.mediaviewer.navigation.ImageRoute
import com.local.mediaviewer.navigation.PlayerRoute
import com.local.mediaviewer.navigation.SettingsRoute
import com.local.mediaviewer.settings.SettingsViewModel
import com.local.mediaviewer.ui.browser.BrowserScreen
import com.local.mediaviewer.ui.components.MediaRouteShell
import com.local.mediaviewer.ui.home.HomeScreen
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
            MediaRouteShell(
                title = route.name,
                typeLabel = "媒体播放器",
                onBack = { navController.popBackStack() },
            )
        }
        composable<ImageRoute> { entry ->
            val route = entry.toRoute<ImageRoute>()
            MediaRouteShell(
                title = route.name,
                typeLabel = "图片查看器",
                onBack = { navController.popBackStack() },
            )
        }
    }
}
