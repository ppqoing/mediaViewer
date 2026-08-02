package com.local.mediaviewer.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.MediaKind
import com.local.mediaviewer.playback.PlaybackPositionStore
import com.local.mediaviewer.playback.PlaybackStatus
import com.local.mediaviewer.playback.VideoScaleMode
import com.local.mediaviewer.session.ServerSessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val initialRequest: PlayerRequest,
    val controller: PlaybackController,
    private val positionStore: PlaybackPositionStore,
    private val session: ServerSessionManager,
    private val clock: () -> Long = System::currentTimeMillis,
    private val autoStart: Boolean = true,
) : ViewModel() {
    private var currentRequest = initialRequest
    private var pendingResumeMs: Long? = null
    private var resumeApplied = false
    private var endpointRetryUsed = false
    private var lastStatus = PlaybackStatus.IDLE
    private var leaving = false
    private var nextSeekGeneration = 0L
    private var activeSeekConfirmation: SeekConfirmation? = null
    private var playAfterSeekGeneration: Long? = null
    private var seekConfirmationTimeoutJob: Job? = null
    private val mutableUiState = MutableStateFlow(
        PlayerUiState(
            name = initialRequest.name,
            kind = initialRequest.kind,
            currentMediaKey = initialRequest.mediaKey,
        ),
    )
    val uiState: StateFlow<PlayerUiState> = mutableUiState.asStateFlow()

    init {
        if (autoStart) {
            viewModelScope.launch {
                pendingResumeMs = positionStore.resumePosition(
                    initialRequest.mediaKey,
                )
                controller.prepare(currentRequest.requestUrl)
                controller.play()
            }
        }
        viewModelScope.launch {
            controller.state.collect { state ->
                val current = mutableUiState.value
                val reconciled = current.seekSync.reconcile(
                    mediaKey = currentMediaKey(),
                    actualMs = state.positionMs,
                    status = state.status,
                )
                mutableUiState.value = current
                    .withEngine(state)
                    .copy(seekSync = reconciled)
                if (
                    state.status == PlaybackStatus.ERROR ||
                    state.status == PlaybackStatus.ENDED
                ) {
                    cancelSeekConfirmation()
                }
                completeSeekConfirmationIfResolved()
                applyResumeIfReady()
                if (
                    state.status == PlaybackStatus.ENDED &&
                    lastStatus != PlaybackStatus.ENDED &&
                    controller !is QueuePlaybackController
                ) {
                    saveSnapshot(ended = true)
                }
                if (
                    state.status == PlaybackStatus.ERROR &&
                    lastStatus != PlaybackStatus.ERROR &&
                    controller !is QueuePlaybackController
                ) {
                    recoverEndpointOnce()
                }
                lastStatus = state.status
            }
        }
        (controller as? QueuePlaybackController)?.let { queueController ->
            viewModelScope.launch {
                queueController.sessionState.collect { sessionState ->
                    val queue = sessionState.queue
                    val currentItem = sessionState.currentItem
                    val current = mutableUiState.value
                    val mediaChanged =
                        queue.currentMediaKey != current.currentMediaKey
                    val seekSync = if (mediaChanged) {
                        cancelSeekConfirmation()
                        current.seekSync.clear()
                    } else {
                        current.seekSync
                    }
                    if (currentItem != null) {
                        currentRequest = PlayerRequest(
                            name = currentItem.name,
                            logicalUrl = currentItem.logicalUrl,
                            requestUrl = currentItem.logicalUrl,
                            mediaKey = currentItem.mediaKey,
                            kind = currentItem.kind,
                        )
                    }
                    val reconciled = seekSync.reconcile(
                        mediaKey = queue.currentMediaKey,
                        actualMs = sessionState.playback.positionMs,
                        status = sessionState.playback.status,
                    )
                    mutableUiState.value = current
                        .withEngine(sessionState.playback)
                        .copy(
                            name = currentItem?.name
                                ?: mutableUiState.value.name,
                            kind = currentItem?.kind
                                ?: mutableUiState.value.kind,
                            currentMediaKey = queue.currentMediaKey,
                            queueSize = queue.items.size,
                            playbackMode = queue.mode,
                            canSkipPrevious = sessionState.canSkipPrevious,
                            canSkipNext = sessionState.canSkipNext,
                            errorMessage = sessionState.errorMessage
                                ?: sessionState.playback.errorMessage,
                            playbackSpeed = queue.playbackSpeed,
                            seekSync = reconciled,
                        )
                    if (
                        sessionState.playback.status == PlaybackStatus.ERROR ||
                        sessionState.playback.status == PlaybackStatus.ENDED
                    ) {
                        cancelSeekConfirmation()
                    }
                    completeSeekConfirmationIfResolved()
                }
            }
        }
    }

    fun play() {
        if (mutableUiState.value.seekSync.pending == null) {
            playNow()
            return
        }
        val pending = requireNotNull(
            mutableUiState.value.seekSync.pending,
        )
        val confirmation = activeSeekConfirmation
            ?.takeIf { it.pending == pending }
            ?: startSeekConfirmation(pending)
        playAfterSeekGeneration = confirmation.generation
    }

    fun pause() {
        cancelDeferredPlay()
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

    fun previous() = (controller as? QueuePlaybackController)?.skipPrevious()

    fun next() = (controller as? QueuePlaybackController)?.skipNext()

    fun retry() {
        cancelSeekConfirmation()
        if (controller is QueuePlaybackController) {
            controller.play()
            return
        }
        endpointRetryUsed = false
        mutableUiState.value = mutableUiState.value.copy(
            status = PlaybackStatus.OPENING,
            errorMessage = null,
        )
        controller.prepare(currentRequest.requestUrl)
        controller.play()
    }

    fun onResumeHintShown() {
        if (mutableUiState.value.resumedFromMs != null) {
            mutableUiState.value = mutableUiState.value.copy(
                resumedFromMs = null,
            )
        }
    }

    fun beginScrub() {
        cancelSeekConfirmation()
        updateInteraction(PlayerInteractionReducer::beginScrub)
    }

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
        val pending = next.seekSync.pending
        if (target != null && pending != null) {
            startSeekConfirmation(pending)
            controller.seekTo(target)
        }
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

    fun leave(onSaved: () -> Unit) {
        if (leaving) return
        leaving = true
        viewModelScope.launch {
            try {
                saveSnapshot(ended = false)
            } finally {
                onSaved()
            }
        }
    }

    fun stopAndClear(onFinished: () -> Unit) {
        if (leaving) return
        leaving = true
        viewModelScope.launch {
            try {
                saveSnapshot(ended = false)
            } finally {
                try {
                    (controller as? QueuePlaybackController)?.clearAll()
                        ?: controller.stop()
                } finally {
                    onFinished()
                }
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
                val queueController = controller as? QueuePlaybackController
                val currentMediaKey =
                    queueController?.sessionState?.value?.currentItem?.mediaKey
                if (queueController != null && currentMediaKey != null) {
                    queueController.reloadCurrent()
                } else {
                    controller.prepare(currentRequest.requestUrl)
                    controller.play()
                }
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
        val currentMediaKey = (controller as? QueuePlaybackController)
            ?.sessionState
            ?.value
            ?.currentItem
            ?.mediaKey
            ?: currentRequest.mediaKey
        positionStore.record(
            mediaKey = currentMediaKey,
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            updatedAtEpochMs = clock(),
            ended = ended || state.status == PlaybackStatus.ENDED,
        )
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

    private fun currentMediaKey(): String =
        (controller as? QueuePlaybackController)
            ?.sessionState
            ?.value
            ?.currentItem
            ?.mediaKey
            ?: currentRequest.mediaKey

    private fun startSeekConfirmation(
        pending: PendingSeek,
    ): SeekConfirmation {
        seekConfirmationTimeoutJob?.cancel()
        playAfterSeekGeneration = null
        val confirmation = SeekConfirmation(
            generation = ++nextSeekGeneration,
            pending = pending,
        )
        activeSeekConfirmation = confirmation
        seekConfirmationTimeoutJob = viewModelScope.launch {
            delay(SEEK_CONFIRMATION_TIMEOUT_MS)
            handleSeekConfirmationTimeout(confirmation)
        }
        return confirmation
    }

    private fun completeSeekConfirmationIfResolved() {
        val confirmation = activeSeekConfirmation ?: return
        if (!confirmationMatchesCurrentMedia(confirmation)) {
            cancelSeekConfirmation()
            return
        }
        if (mutableUiState.value.seekSync.pending != null) return
        completeSeekConfirmation(confirmation)
    }

    private fun handleSeekConfirmationTimeout(
        confirmation: SeekConfirmation,
    ) {
        if (activeSeekConfirmation != confirmation) return
        if (!confirmationMatchesCurrentMedia(confirmation)) {
            cancelSeekConfirmation()
            return
        }
        val current = mutableUiState.value
        if (current.seekSync.pending != confirmation.pending) return
        mutableUiState.value = current.copy(
            seekSync = current.seekSync.copy(pending = null),
            errorMessage = SEEK_CONFIRMATION_TIMEOUT_MESSAGE,
        )
        completeSeekConfirmation(confirmation)
    }

    private fun completeSeekConfirmation(
        confirmation: SeekConfirmation,
    ) {
        if (activeSeekConfirmation != confirmation) return
        if (!confirmationMatchesCurrentMedia(confirmation)) {
            cancelSeekConfirmation()
            return
        }
        val shouldPlay =
            playAfterSeekGeneration == confirmation.generation
        activeSeekConfirmation = null
        if (shouldPlay) {
            playAfterSeekGeneration = null
        }
        seekConfirmationTimeoutJob?.cancel()
        seekConfirmationTimeoutJob = null
        if (shouldPlay) {
            playNow()
        }
    }

    private fun confirmationMatchesCurrentMedia(
        confirmation: SeekConfirmation,
    ): Boolean {
        val expectedMediaKey =
            confirmation.pending.mediaKey ?: return true
        return expectedMediaKey == currentMediaKey()
    }

    private fun playNow() {
        controller.play()
    }

    private fun cancelDeferredPlay() {
        playAfterSeekGeneration = null
    }

    private fun cancelSeekConfirmation() {
        cancelDeferredPlay()
        activeSeekConfirmation = null
        seekConfirmationTimeoutJob?.cancel()
        seekConfirmationTimeoutJob = null
    }

    override fun onCleared() {
        cancelSeekConfirmation()
        super.onCleared()
    }

    private data class SeekConfirmation(
        val generation: Long,
        val pending: PendingSeek,
    )

    private companion object {
        const val SEEK_INCREMENT_MS = 10_000L
        const val SEEK_CONFIRMATION_TIMEOUT_MS = 1_500L
        const val SEEK_CONFIRMATION_TIMEOUT_MESSAGE =
            "跳转位置未确认，已恢复实际进度"
    }
}
