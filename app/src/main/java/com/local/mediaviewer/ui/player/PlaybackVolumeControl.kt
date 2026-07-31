package com.local.mediaviewer.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import com.local.mediaviewer.ui.components.MediaVerticalLevelControl
import com.local.mediaviewer.ui.components.PlayerIconButton
import com.local.mediaviewer.ui.theme.MediaTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

const val VOLUME_POPUP_IDLE_TIMEOUT_MS = 3_000L

internal data class VolumePopupIdlePolicy(
    val lastInteractionMs: Long,
) {
    fun interacted(nowMs: Long) = copy(lastInteractionMs = nowMs)

    fun shouldClose(nowMs: Long): Boolean =
        nowMs - lastInteractionMs >= VOLUME_POPUP_IDLE_TIMEOUT_MS
}

@Composable
fun PlaybackVolumeControl(
    state: VolumeState,
    expanded: Boolean,
    onExpandedChanged: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onToggleMute: () -> Unit,
    onVolumeChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = state.accessibilityDescription()
    var interactionEpoch by remember { mutableLongStateOf(0L) }

    LaunchedEffect(
        expanded,
        interactionEpoch,
        state.current,
        state.maximum,
        state.muted,
    ) {
        if (!expanded) return@LaunchedEffect
        coroutineScope {
            val polling = launch {
                while (isActive) {
                    onRefresh()
                    delay(250L)
                }
            }
            delay(VOLUME_POPUP_IDLE_TIMEOUT_MS)
            polling.cancel()
            onExpandedChanged(false)
        }
    }

    Box(modifier = modifier) {
        PlayerIconButton(
            icon = if (state.muted) PlayerIcons.Muted else PlayerIcons.Volume,
            contentDescription = description,
            stateDescription = description,
            onClick = { onExpandedChanged(!expanded) },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChanged(false) },
            modifier = Modifier
                .testTag("volume_popup")
                .width(MediaTheme.sizing.volumePopupWidth),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "${state.percent}%")
                MediaVerticalLevelControl(
                    value = state.fraction,
                    label = "音量",
                    onValueChanged = { value ->
                        interactionEpoch += 1L
                        onVolumeChanged(value)
                    },
                    modifier = Modifier
                        .testTag("volume_slider_vertical"),
                )
                PlayerIconButton(
                    icon = if (state.muted) PlayerIcons.Volume else PlayerIcons.Muted,
                    contentDescription = if (state.muted) "取消静音" else "静音",
                    stateDescription = if (state.muted) {
                        "已静音"
                    } else {
                        "未静音，音量 ${state.percent}%"
                    },
                    onClick = {
                        interactionEpoch += 1L
                        onToggleMute()
                    },
                    modifier = Modifier.semantics {
                        toggleableState = ToggleableState(state.muted)
                    },
                )
            }
        }
    }
}

internal fun VolumeState.accessibilityDescription(): String =
    "音量，当前 $percent%，${if (muted) "已静音" else "未静音"}"
