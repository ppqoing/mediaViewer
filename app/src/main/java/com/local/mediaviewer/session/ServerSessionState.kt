package com.local.mediaviewer.session

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.model.ServerShare
import com.local.mediaviewer.model.SessionEndpoint

sealed interface ServerSessionState {
    data object Connecting : ServerSessionState

    data class Connected(
        val endpoint: SessionEndpoint,
        val resolvedIpv4s: List<String>,
        val shares: List<ServerShare> = emptyList(),
    ) : ServerSessionState

    data class Failed(
        val error: AppError,
        val resolvedIpv4s: List<String>,
    ) : ServerSessionState
}
