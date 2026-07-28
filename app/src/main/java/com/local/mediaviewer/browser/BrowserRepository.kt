package com.local.mediaviewer.browser

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.RootShare
import com.local.mediaviewer.model.SessionEndpoint
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
    private val contentRepository: DirectoryContentRepository,
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
        )
    }

    override suspend fun openDirectory(
        root: RootShare,
        logicalUrl: String,
        breadcrumbs: List<Breadcrumb>,
    ): AppResult<BrowserPage> {
        return load(
            root = root,
            logicalUrl = logicalUrl,
            breadcrumbs = breadcrumbs,
        )
    }

    private suspend fun load(
        root: RootShare,
        logicalUrl: String,
        breadcrumbs: List<Breadcrumb>,
    ): AppResult<BrowserPage> {
        return when (val result = contentRepository.load(logicalUrl)) {
            is AppResult.Success -> AppResult.Success(
                BrowserPage(
                    root = root,
                    logicalDirectoryUrl =
                        result.value.logicalDirectoryUrl,
                    requestDirectoryUrl =
                        result.value.requestDirectoryUrl,
                    breadcrumbs = breadcrumbs,
                    entries = result.value.entries,
                ),
            )

            is AppResult.Failure -> result
        }
    }

    private fun currentEndpoint(): SessionEndpoint? =
        (session.state.value as? ServerSessionState.Connected)?.endpoint

    private fun unavailable(): AppResult.Failure =
        AppResult.Failure(
            AppError.NetworkFailure("服务器尚未连接"),
        )
}
