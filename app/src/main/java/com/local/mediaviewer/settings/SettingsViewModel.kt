package com.local.mediaviewer.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.image.ImageReaderMode
import com.local.mediaviewer.image.ReaderPreferencesRepository
import com.local.mediaviewer.network.ConnectionTestResult
import com.local.mediaviewer.session.ServerSessionManager
import kotlinx.coroutines.CancellationException
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
    val defaultImageMode:
        ImageReaderMode = ImageReaderMode.COMIC,
    val isSavingImageMode: Boolean = false,
    val imageModeError: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val hasUnsavedServerChange: Boolean = false,
)

enum class SettingsBackDecision {
    LEAVE,
    CONFIRM_DISCARD,
}

class SettingsViewModel(
    private val settings: ServerSettingsRepository,
    private val readerPreferences: ReaderPreferencesRepository,
    private val session: ServerSessionManager,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()

    private val mutableSaved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = mutableSaved.asSharedFlow()

    private var savedServerInput = ""
    private var successfulResult: ConnectionTestResult? = null
    private var candidateVersion = 0L
    private var testJob: Job? = null

    // 只有“已验证但未保存”的输入才需要放弃确认（规格 §8.3/§10）；
    // 普通未验证输入不拦截返回。
    private fun hasValidatedUnsavedChange(input: String): Boolean =
        successfulResult?.server?.logicalBaseUrl == input &&
            input != savedServerInput

    init {
        val initialVersion = candidateVersion
        viewModelScope.launch {
            val logicalBaseUrl = settings.current().logicalBaseUrl
            savedServerInput = logicalBaseUrl
            val currentState = mutableUiState.value
            if (candidateVersion == initialVersion) {
                mutableUiState.value = currentState.copy(
                    input = logicalBaseUrl,
                    hasUnsavedServerChange = false,
                )
            } else {
                mutableUiState.value = currentState.copy(
                    hasUnsavedServerChange =
                        hasValidatedUnsavedChange(currentState.input),
                )
            }
        }
        viewModelScope.launch {
            val mode = readerPreferences.currentDefaultMode()
            mutableUiState.value = mutableUiState.value.copy(
                defaultImageMode = mode,
            )
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
            saveError = null,
            hasUnsavedServerChange =
                hasValidatedUnsavedChange(value),
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
                    val currentState = mutableUiState.value
                    mutableUiState.value = currentState.copy(
                        input = result.value.server.logicalBaseUrl,
                        isTesting = false,
                        resolvedIpv4s = result.value.resolvedIpv4s,
                        selectedIpv4 = result.value.endpoint.ipv4,
                        errorMessage = null,
                        canSave = !currentState.isSaving,
                        hasUnsavedServerChange =
                            hasValidatedUnsavedChange(
                                result.value.server.logicalBaseUrl,
                            ),
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
        if (mutableUiState.value.isSaving) return
        val result = successfulResult ?: return
        val persistedInput = result.server.logicalBaseUrl
        mutableUiState.value = mutableUiState.value.copy(
            isSaving = true,
            saveError = null,
            canSave = false,
        )
        viewModelScope.launch {
            var saveSucceeded = false
            var saveFailure: Exception? = null
            var shouldEmitSaved = false
            try {
                session.saveCandidate(result)
                saveSucceeded = true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                saveFailure = error
            } finally {
                if (saveSucceeded) {
                    savedServerInput = persistedInput
                    val currentState = mutableUiState.value
                    val inputStillPersisted =
                        currentState.input == persistedInput
                    if (successfulResult === result) {
                        successfulResult = null
                    }
                    val canSaveCurrentResult =
                        successfulResult?.server?.logicalBaseUrl ==
                            currentState.input
                    mutableUiState.value = currentState.copy(
                        isSaving = false,
                        saveError = null,
                        canSave = if (inputStillPersisted) {
                            false
                        } else {
                            canSaveCurrentResult
                        },
                        hasUnsavedServerChange =
                            hasValidatedUnsavedChange(currentState.input),
                    )
                    shouldEmitSaved = inputStillPersisted
                } else if (saveFailure != null) {
                    val currentState = mutableUiState.value
                    val canRetrySameResult =
                        successfulResult === result &&
                            currentState.input == persistedInput
                    val canSaveCurrentResult =
                        successfulResult?.server?.logicalBaseUrl ==
                            currentState.input
                    mutableUiState.value = currentState.copy(
                        isSaving = false,
                        saveError = "保存失败，请重试",
                        canSave = if (canRetrySameResult) {
                            true
                        } else {
                            canSaveCurrentResult
                        },
                        hasUnsavedServerChange =
                            hasValidatedUnsavedChange(currentState.input),
                    )
                } else {
                    mutableUiState.value =
                        mutableUiState.value.copy(
                            isSaving = false,
                        )
                }
            }
            if (shouldEmitSaved) {
                mutableSaved.emit(Unit)
            }
        }
    }

    fun requestBack(): SettingsBackDecision =
        if (mutableUiState.value.hasUnsavedServerChange) {
            SettingsBackDecision.CONFIRM_DISCARD
        } else {
            SettingsBackDecision.LEAVE
        }

    fun onDefaultImageModeChanged(mode: ImageReaderMode) {
        if (
            mode == mutableUiState.value.defaultImageMode ||
            mutableUiState.value.isSavingImageMode
        ) {
            return
        }
        val previous = mutableUiState.value.defaultImageMode
        mutableUiState.value = mutableUiState.value.copy(
            defaultImageMode = mode,
            isSavingImageMode = true,
            imageModeError = null,
        )
        viewModelScope.launch {
            runCatching {
                readerPreferences.setDefaultMode(mode)
            }.onSuccess {
                mutableUiState.value = mutableUiState.value.copy(
                    isSavingImageMode = false,
                )
            }.onFailure {
                mutableUiState.value = mutableUiState.value.copy(
                    defaultImageMode = previous,
                    isSavingImageMode = false,
                    imageModeError = "默认看图方式保存失败",
                )
            }
        }
    }
}
