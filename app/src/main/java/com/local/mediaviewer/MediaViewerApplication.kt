package com.local.mediaviewer

import android.app.Application
import com.local.mediaviewer.app.AppContainer
import com.local.mediaviewer.app.DefaultAppContainer

class MediaViewerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
