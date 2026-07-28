package com.local.mediaviewer.app

import android.content.Context
import com.local.mediaviewer.browser.BrowserRepository
import com.local.mediaviewer.browser.DefaultBrowserRepository
import com.local.mediaviewer.network.DefaultCaddyDirectoryClient
import com.local.mediaviewer.network.DefaultConnectionProbe
import com.local.mediaviewer.network.DefaultDirectoryJsonParser
import com.local.mediaviewer.network.OkHttpDirectoryProbeTransport
import com.local.mediaviewer.network.SystemIpv4Resolver
import com.local.mediaviewer.session.DefaultServerSessionManager
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.settings.DataStoreServerSettingsRepository
import com.local.mediaviewer.settings.ServerSettingsRepository
import com.local.mediaviewer.settings.serverSettingsDataStore

interface AppContainer {
    val settingsRepository: ServerSettingsRepository
    val sessionManager: ServerSessionManager
    val browserRepository: BrowserRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
    override val settingsRepository: ServerSettingsRepository =
        DataStoreServerSettingsRepository(context.serverSettingsDataStore)

    private val directoryParser = DefaultDirectoryJsonParser()
    private val directoryClient = DefaultCaddyDirectoryClient(
        parser = directoryParser,
    )
    private val resolver = SystemIpv4Resolver()
    private val probe = DefaultConnectionProbe(
        transport = OkHttpDirectoryProbeTransport(),
        parser = directoryParser,
    )

    override val sessionManager: ServerSessionManager =
        DefaultServerSessionManager(
            settings = settingsRepository,
            resolver = resolver,
            probe = probe,
        )

    override val browserRepository: BrowserRepository =
        DefaultBrowserRepository(
            directoryClient = directoryClient,
            session = sessionManager,
        )
}
