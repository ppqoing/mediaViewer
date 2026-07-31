package com.local.mediaviewer.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.mediaviewer.model.ServerShare
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Connecting : HomeUiState

    data class Connected(
        val ipv4: String,
        val shares: List<ServerShare>,
    ) : HomeUiState

    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(
    private val session: ServerSessionManager,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = session.state
        .map { state ->
            when (state) {
                ServerSessionState.Connecting -> HomeUiState.Connecting
                is ServerSessionState.Connected ->
                    HomeUiState.Connected(
                        ipv4 = state.endpoint.ipv4,
                        shares = state.shares,
                    )
                is ServerSessionState.Failed ->
                    HomeUiState.Error(state.error.userMessage)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = HomeUiState.Connecting,
        )

    fun retry() {
        viewModelScope.launch {
            session.connectSaved()
        }
    }
}
