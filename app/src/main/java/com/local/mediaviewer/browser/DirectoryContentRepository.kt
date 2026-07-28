package com.local.mediaviewer.browser

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.DirectoryEntry
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.network.CaddyDirectoryClient
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState

data class DirectoryContent(
    val logicalDirectoryUrl: String,
    val requestDirectoryUrl: String,
    val entries: List<DirectoryEntry>,
)

interface DirectoryContentRepository {
    suspend fun load(
        logicalDirectoryUrl: String,
    ): AppResult<DirectoryContent>
}

class DefaultDirectoryContentRepository(
    private val directoryClient: CaddyDirectoryClient,
    private val session: ServerSessionManager,
) : DirectoryContentRepository {
    override suspend fun load(
        logicalDirectoryUrl: String,
    ): AppResult<DirectoryContent> {
        val endpoint = currentEndpoint() ?: return unavailable()
        return loadWith(
            logicalDirectoryUrl = logicalDirectoryUrl,
            endpoint = endpoint,
            allowRefresh = true,
        )
    }

    private suspend fun loadWith(
        logicalDirectoryUrl: String,
        endpoint: SessionEndpoint,
        allowRefresh: Boolean,
    ): AppResult<DirectoryContent> {
        val requestUrl =
            endpoint.requestUrlFor(logicalDirectoryUrl)
        return when (
            val result = directoryClient.listDirectory(
                logicalDirectoryUrl = logicalDirectoryUrl,
                requestDirectoryUrl = requestUrl,
            )
        ) {
            is AppResult.Success -> AppResult.Success(
                DirectoryContent(
                    logicalDirectoryUrl = logicalDirectoryUrl,
                    requestDirectoryUrl = requestUrl,
                    entries = result.value,
                ),
            )

            is AppResult.Failure -> {
                if (
                    allowRefresh &&
                    result.error is AppError.NetworkFailure
                ) {
                    when (
                        val refreshed =
                            session.refreshAfterRequestFailure()
                    ) {
                        is AppResult.Success -> loadWith(
                            logicalDirectoryUrl = logicalDirectoryUrl,
                            endpoint = refreshed.value,
                            allowRefresh = false,
                        )

                        is AppResult.Failure -> refreshed
                    }
                } else {
                    result
                }
            }
        }
    }

    private fun currentEndpoint(): SessionEndpoint? =
        (session.state.value as? ServerSessionState.Connected)
            ?.endpoint

    private fun unavailable(): AppResult.Failure =
        AppResult.Failure(
            AppError.NetworkFailure("服务器尚未连接"),
        )
}
