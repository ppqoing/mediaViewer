package com.local.mediaviewer.browser

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.RootShare
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.network.CaddyDirectoryClient
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import okhttp3.HttpUrl.Companion.toHttpUrl

interface BrowserRepository {
    suspend fun openRoot(root: RootShare): AppResult<BrowserPage>

    suspend fun openDirectory(
        root: RootShare,
        logicalUrl: String,
        breadcrumbs: List<Breadcrumb>,
    ): AppResult<BrowserPage>
}

class DefaultBrowserRepository(
    private val directoryClient: CaddyDirectoryClient,
    private val session: ServerSessionManager,
) : BrowserRepository {
    override suspend fun openRoot(root: RootShare): AppResult<BrowserPage> {
        val endpoint = currentEndpoint() ?: return unavailable()
        val logicalUrl = requireNotNull(
            endpoint.logicalBaseUrl.toHttpUrl().resolve(root.path),
        ).toString()
        return load(
            root = root,
            logicalUrl = logicalUrl,
            breadcrumbs = listOf(Breadcrumb(root.displayName, logicalUrl)),
            endpoint = endpoint,
            allowRefresh = true,
        )
    }

    override suspend fun openDirectory(
        root: RootShare,
        logicalUrl: String,
        breadcrumbs: List<Breadcrumb>,
    ): AppResult<BrowserPage> {
        val endpoint = currentEndpoint() ?: return unavailable()
        return load(
            root = root,
            logicalUrl = logicalUrl,
            breadcrumbs = breadcrumbs,
            endpoint = endpoint,
            allowRefresh = true,
        )
    }

    private suspend fun load(
        root: RootShare,
        logicalUrl: String,
        breadcrumbs: List<Breadcrumb>,
        endpoint: SessionEndpoint,
        allowRefresh: Boolean,
    ): AppResult<BrowserPage> {
        val requestUrl = endpoint.requestUrlFor(logicalUrl)
        return when (
            val result = directoryClient.listDirectory(
                logicalDirectoryUrl = logicalUrl,
                requestDirectoryUrl = requestUrl,
            )
        ) {
            is AppResult.Success -> AppResult.Success(
                BrowserPage(
                    root = root,
                    logicalDirectoryUrl = logicalUrl,
                    requestDirectoryUrl = requestUrl,
                    breadcrumbs = breadcrumbs,
                    entries = result.value,
                ),
            )

            is AppResult.Failure -> {
                if (allowRefresh && result.error is AppError.NetworkFailure) {
                    when (val refreshed = session.refreshAfterRequestFailure()) {
                        is AppResult.Success -> load(
                            root = root,
                            logicalUrl = logicalUrl,
                            breadcrumbs = breadcrumbs,
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
        (session.state.value as? ServerSessionState.Connected)?.endpoint

    private fun unavailable(): AppResult.Failure =
        AppResult.Failure(
            AppError.NetworkFailure("服务器尚未连接"),
        )
}
