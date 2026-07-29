package com.local.mediaviewer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.local.mediaviewer.app.MediaViewerApp
import com.local.mediaviewer.navigation.CurrentPlayerNavigationRequests
import com.local.mediaviewer.navigation.EXTRA_OPEN_CURRENT_PLAYER
import com.local.mediaviewer.navigation.isCurrentPlayerNotificationRequest
import com.local.mediaviewer.ui.theme.MediaViewerTheme

class MainActivity : ComponentActivity() {
    private val currentPlayerNavigationRequests =
        CurrentPlayerNavigationRequests()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNavigationIntent(intent)
        val container = (application as MediaViewerApplication).container
        setContent {
            MediaViewerTheme {
                MediaViewerApp(
                    container = container,
                    currentPlayerNavigationRequests =
                        currentPlayerNavigationRequests,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        if (
            isCurrentPlayerNotificationRequest(
                action = intent?.action,
                requested = intent?.getBooleanExtra(
                    EXTRA_OPEN_CURRENT_PLAYER,
                    false,
                ) == true,
            )
        ) {
            currentPlayerNavigationRequests.requestOpenCurrentPlayer()
        }
    }
}
