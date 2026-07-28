package com.local.mediaviewer.ui.player

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.local.mediaviewer.playback.PlaybackEngine

@Composable
fun VlcSurface(
    engine: PlaybackEngine,
    keepScreenOn: Boolean,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier.testTag("vlc_surface"),
        factory = { context ->
            SurfaceView(context).also { surface ->
                surface.keepScreenOn = keepScreenOn
                engine.attachVideoSurface(surface)
            }
        },
        update = { surface ->
            surface.keepScreenOn = keepScreenOn
        },
        onRelease = { surface ->
            surface.keepScreenOn = false
            engine.detachVideoSurface()
        },
    )
}
