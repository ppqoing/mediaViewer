package com.local.mediaviewer.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.playback.PlaybackEngine
import com.local.mediaviewer.playback.PlaybackPositionStore
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.session.ServerSessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val initialRequest: PlayerRequest,
    val engine: PlaybackEngine,
    private val positionStore: PlaybackPositionStore,
    private val session: ServerSessionManager,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private var currentRequest = initialRequest
    private var pendingResumeMs: Long? = null
    private var resumeApplied = false
    private var endpointRetryUsed = false
    private var lastStatus = PlaybackStatus.IDLE
    private var leaving = false
    private val periodicSaveJob: Job
    private val mutableUiState = MutableStateFlow(
        PlayerUiState(
            name = initialRequest.name,
            kind = initialRequest.kind,
        ),
    )
    val uiState: StateFlow<PlayerUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            pendingResumeMs = positionStore.resumePosition(
                initialRequest.mediaKey,
            )
            engine.prepare(currentRequest.requestUrl)
            engine.play()
        }
        viewModelScope.launch {
            engine.state.collect { state ->
                mutableUiState.value = mutableUiState.value.withEngine(state)
                applyResumeIfReady()
                if (
                    state.status == PlaybackStatus.ENDED &&
                    lastStatus != PlaybackStatus.ENDED
                ) {
                    saveSnapshot(ended = true)
                }
                if (
                    state.status == PlaybackStatus.ERROR &&
                    lastStatus != PlaybackStatus.ERROR
                ) {
                    recoverEndpointOnce()
                }
                lastStatus = state.status
            }
        }
        periodicSaveJob = viewModelScope.launch {
            while (isActive) {
                delay(SAVE_INTERVAL_MS)
                saveSnapshot(ended = false)
            }
        }
    }

    fun play() = engine.play()

    fun pause() {
        engine.pause()
        viewModelScope.launch {
            saveSnapshot(ended = false)
        }
    }

    fun seekTo(positionMs: Long) = engine.seekTo(positionMs)

    fun onBackgrounded() {
        engine.pause()
        viewModelScope.launch {
            saveSnapshot(ended = false)
        }
    }

    fun leave(onSaved: () -> Unit) {
        if (leaving) return
        leaving = true
        periodicSaveJob.cancel()
        viewModelScope.launch {
            try {
                saveSnapshot(ended = false)
            } finally {
                engine.close()
                onSaved()
            }
        }
    }

    private fun applyResumeIfReady() {
        val resume = pendingResumeMs ?: return
        val state = engine.state.value
        if (
            !resumeApplied &&
            state.isSeekable &&
            state.durationMs > resume
        ) {
            engine.seekTo(resume)
            resumeApplied = true
            mutableUiState.value = mutableUiState.value.copy(
                resumedFromMs = resume,
            )
        }
    }

    private suspend fun recoverEndpointOnce() {
        if (endpointRetryUsed) return
        endpointRetryUsed = true
        when (val refreshed = session.refreshAfterRequestFailure()) {
            is AppResult.Success -> {
                currentRequest = currentRequest.copy(
                    requestUrl = refreshed.value.requestUrlFor(
                        currentRequest.logicalUrl,
                    ),
                )
                lastStatus = PlaybackStatus.IDLE
                mutableUiState.value = mutableUiState.value.copy(
                    errorMessage = null,
                )
                engine.prepare(currentRequest.requestUrl)
                engine.play()
            }

            is AppResult.Failure -> {
                mutableUiState.value = mutableUiState.value.copy(
                    status = PlaybackStatus.ERROR,
                    errorMessage = refreshed.error.userMessage,
                )
            }
        }
    }

    private suspend fun saveSnapshot(ended: Boolean) {
        val state = engine.state.value
        positionStore.record(
            mediaKey = currentRequest.mediaKey,
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            updatedAtEpochMs = clock(),
            ended = ended || state.status == PlaybackStatus.ENDED,
        )
    }

    override fun onCleared() {
        engine.close()
    }

    private companion object {
        const val SAVE_INTERVAL_MS = 5_000L
    }
}
