package com.local.mediaviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.local.mediaviewer.app.MediaViewerApp
import com.local.mediaviewer.ui.theme.MediaViewerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MediaViewerApplication).container
        setContent {
            MediaViewerTheme {
                MediaViewerApp(container)
            }
        }
    }
}
