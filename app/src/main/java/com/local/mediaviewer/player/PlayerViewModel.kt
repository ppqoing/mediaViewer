package com.local.mediaviewer.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.playback.PlaybackPositionStore
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.playback.VideoScaleMode
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
    val controller: PlaybackController,
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
            controller.prepare(currentRequest.requestUrl)
            controller.play()
        }
        viewModelScope.launch {
            controller.state.collect { state ->
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

    fun play() = controller.play()

    fun pause() {
        controller.pause()
        viewModelScope.launch {
            saveSnapshot(ended = false)
        }
    }

    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)

    fun seekBack() = seekBy(-SEEK_INCREMENT_MS)

    fun seekForward() = seekBy(SEEK_INCREMENT_MS)

    fun replay() {
        controller.seekTo(0L)
        controller.play()
    }

    fun beginScrub() = updateInteraction(
        PlayerInteractionReducer::beginScrub,
    )

    fun previewScrub(positionMs: Long) {
        mutableUiState.value = PlayerInteractionReducer.updateScrub(
            mutableUiState.value,
            positionMs,
        )
    }

    fun commitScrub() {
        val (next, target) =
            PlayerInteractionReducer.finishScrub(mutableUiState.value)
        mutableUiState.value = next
        target?.let(controller::seekTo)
    }

    fun setPlaybackSpeed(speed: Float) {
        controller.setPlaybackSpeed(speed)
    }

    fun setVideoScaleMode(mode: VideoScaleMode) {
        if (
            mutableUiState.value.videoScaleMode == mode
        ) {
            return
        }
        controller.setVideoScaleMode(mode)
        mutableUiState.value = mutableUiState.value.copy(
            videoScaleMode = mode,
        )
    }

    fun onBackgrounded() {
        controller.pause()
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
                controller.close()
                onSaved()
            }
        }
    }

    private fun applyResumeIfReady() {
        val resume = pendingResumeMs ?: return
        val state = controller.state.value
        if (
            !resumeApplied &&
            state.isSeekable &&
            state.durationMs > resume
        ) {
            controller.seekTo(resume)
            resumeApplied = true
            mutableUiState.value = mutableUiState.value.copy(
                resumedFromMs = resume,
            )
        }
    }

    private suspend fun recoverEndpointOnce() {
        if (endpointRetryUsed) return
        endpointRetryUsed = true
        val recoveryPositionMs =
            controller.state.value.positionMs
        when (val refreshed = session.refreshAfterRequestFailure()) {
            is AppResult.Success -> {
                currentRequest = currentRequest.copy(
                    requestUrl = refreshed.value.requestUrlFor(
                        currentRequest.logicalUrl,
                    ),
                )
                pendingResumeMs = recoveryPositionMs
                resumeApplied = false
                lastStatus = PlaybackStatus.IDLE
                mutableUiState.value = mutableUiState.value.copy(
                    errorMessage = null,
                )
                controller.prepare(currentRequest.requestUrl)
                controller.play()
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
        val state = controller.state.value
        positionStore.record(
            mediaKey = currentRequest.mediaKey,
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            updatedAtEpochMs = clock(),
            ended = ended || state.status == PlaybackStatus.ENDED,
        )
    }

    override fun onCleared() {
        controller.close()
    }

    private fun seekBy(deltaMs: Long) {
        val state = mutableUiState.value
        if (!state.isSeekable || state.durationMs <= 0L) return
        controller.seekTo(
            PlayerInteractionReducer.seekTarget(
                state.positionMs,
                state.durationMs,
                deltaMs,
            ),
        )
    }

    private fun updateInteraction(
        transform: (PlayerUiState) -> PlayerUiState,
    ) {
        mutableUiState.value = transform(mutableUiState.value)
    }

    private companion object {
        const val SAVE_INTERVAL_MS = 5_000L
        const val SEEK_INCREMENT_MS = 10_000L
    }
}
