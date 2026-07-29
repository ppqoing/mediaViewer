package com.local.mediaviewer.ui.player

import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.local.mediaviewer.player.PlaybackController
import com.local.mediaviewer.player.VideoOutputConnectionState

@Composable
fun VlcSurface(
    controller: PlaybackController,
    keepScreenOn: Boolean,
    modifier: Modifier = Modifier,
) {
    val outputState by controller.videoOutputState.collectAsStateWithLifecycle()
    var host by remember(controller) { mutableStateOf<FrameLayout?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        host?.let(controller::attachVideoOutput)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        controller.detachVideoOutput()
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize().testTag("vlc_surface"),
            factory = { context ->
                FrameLayout(context).also { created ->
                    host = created
                    created.keepScreenOn = keepScreenOn
                    controller.attachVideoOutput(created)
                }
            },
            update = { current ->
                host = current
                current.keepScreenOn = keepScreenOn
            },
            onRelease = { released ->
                released.keepScreenOn = false
                if (host === released) host = null
                controller.detachVideoOutput()
            },
        )
        val failure = outputState as? VideoOutputConnectionState.Failed
        if (failure != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center),
            ) {
                Text(failure.message)
                TextButton(onClick = controller::retryVideoOutput) {
                    Text("重试画面")
                }
            }
        }
    }
}
