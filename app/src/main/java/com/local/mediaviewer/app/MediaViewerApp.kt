package com.local.mediaviewer.app

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
import com.local.mediaviewer.home.HomeViewModel
import com.local.mediaviewer.navigation.HomeRoute
import com.local.mediaviewer.navigation.SettingsRoute
import com.local.mediaviewer.settings.SettingsViewModel
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
                onOpenRoot = {},
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
    }
}
