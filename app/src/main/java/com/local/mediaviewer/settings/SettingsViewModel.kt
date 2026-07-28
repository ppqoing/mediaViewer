package com.local.mediaviewer.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.network.ConnectionTestResult
import com.local.mediaviewer.session.ServerSessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val input: String = "",
    val isTesting: Boolean = false,
    val resolvedIpv4s: List<String> = emptyList(),
    val selectedIpv4: String? = null,
    val errorMessage: String? = null,
    val canSave: Boolean = false,
)

class SettingsViewModel(
    private val settings: ServerSettingsRepository,
    private val session: ServerSessionManager,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()

    private val mutableSaved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = mutableSaved.asSharedFlow()

    private var successfulResult: ConnectionTestResult? = null
    private var candidateVersion = 0L
    private var testJob: Job? = null

    init {
        val initialVersion = candidateVersion
        viewModelScope.launch {
            val logicalBaseUrl = settings.current().logicalBaseUrl
            if (candidateVersion == initialVersion) {
                mutableUiState.value = mutableUiState.value.copy(
                    input = logicalBaseUrl,
                )
            }
        }
    }

    fun onInputChanged(value: String) {
        candidateVersion += 1
        testJob?.cancel()
        testJob = null
        successfulResult = null
        mutableUiState.value = mutableUiState.value.copy(
            input = value,
            isTesting = false,
            resolvedIpv4s = emptyList(),
            selectedIpv4 = null,
            errorMessage = null,
            canSave = false,
        )
    }

    fun testConnection() {
        val candidate = mutableUiState.value.input
        val version = ++candidateVersion
        testJob?.cancel()
        successfulResult = null
        mutableUiState.value = mutableUiState.value.copy(
            isTesting = true,
            resolvedIpv4s = emptyList(),
            selectedIpv4 = null,
            errorMessage = null,
            canSave = false,
        )
        testJob = viewModelScope.launch {
            val result = session.testCandidate(candidate)
            if (candidateVersion != version) return@launch

            when (result) {
                is AppResult.Success -> {
                    successfulResult = result.value
                    mutableUiState.value = mutableUiState.value.copy(
                        input = result.value.server.logicalBaseUrl,
                        isTesting = false,
                        resolvedIpv4s = result.value.resolvedIpv4s,
                        selectedIpv4 = result.value.endpoint.ipv4,
                        errorMessage = null,
                        canSave = true,
                    )
                }

                is AppResult.Failure -> {
                    successfulResult = null
                    mutableUiState.value = mutableUiState.value.copy(
                        isTesting = false,
                        errorMessage = result.error.userMessage,
                        canSave = false,
                    )
                }
            }
        }
    }

    fun save() {
        val result = successfulResult ?: return
        successfulResult = null
        mutableUiState.value = mutableUiState.value.copy(canSave = false)
        viewModelScope.launch {
            session.saveCandidate(result)
            mutableSaved.emit(Unit)
        }
    }
}
