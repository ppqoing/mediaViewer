package com.local.mediaviewer

import android.app.Application
import com.local.mediaviewer.app.AppContainer
import com.local.mediaviewer.app.DefaultAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MediaViewerApplication : Application() {
    private val playbackPersistenceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO,
    )
    lateinit var container: AppContainer
        internal set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }

    internal fun persistPlaybackSnapshot(
        block: suspend () -> Unit,
    ) {
        playbackPersistenceScope.launch {
            runCatching { block() }
        }
    }
}
