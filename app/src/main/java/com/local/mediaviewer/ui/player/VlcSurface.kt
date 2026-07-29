package com.local.mediaviewer.ui.player

import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.local.mediaviewer.player.PlaybackController

@Composable
fun VlcSurface(
    controller: PlaybackController,
    keepScreenOn: Boolean,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier.testTag("vlc_surface"),
        factory = { context ->
            FrameLayout(context).also { host ->
                host.keepScreenOn = keepScreenOn
                controller.attachVideoOutput(host)
            }
        },
        update = { host ->
            host.keepScreenOn = keepScreenOn
        },
        onRelease = { host ->
            host.keepScreenOn = false
            controller.detachVideoOutput()
        },
    )
}
