package com.local.mediaviewer.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import com.local.mediaviewer.settings.ServerSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class AppSessionUiState(
    val current: ServerSessionState = ServerSessionState.Connecting,
    val lastConnected: ServerSessionState.Connected? = null,
    val needsConfiguration: Boolean = false,
)

class AppSessionViewModel(
    private val session: ServerSessionManager,
    private val settings: ServerSettingsRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AppSessionUiState())
    val uiState: StateFlow<AppSessionUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            val hasSuccessfulEndpoint =
                settings.current().lastSuccessfulIpv4 != null
            launch {
                session.state.collect { next ->
                    mutableUiState.value = reduceAppSession(
                        previous = mutableUiState.value,
                        next = next,
                        hasSuccessfulEndpoint = hasSuccessfulEndpoint,
                    )
                }
            }
            session.connectSaved()
        }
    }

    fun retry() {
        viewModelScope.launch {
            session.connectSaved()
        }
    }
}

internal fun reduceAppSession(
    previous: AppSessionUiState,
    next: ServerSessionState,
    hasSuccessfulEndpoint: Boolean,
): AppSessionUiState = AppSessionUiState(
    current = next,
    lastConnected = when (next) {
        is ServerSessionState.Connected -> next
        else -> previous.lastConnected
    },
    needsConfiguration =
        next is ServerSessionState.Failed &&
            previous.lastConnected == null &&
            !hasSuccessfulEndpoint,
)
