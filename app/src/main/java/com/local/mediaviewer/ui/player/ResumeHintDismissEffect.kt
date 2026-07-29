package com.local.mediaviewer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

internal fun resumeHintDismissDelayMs(
    resumedFromMs: Long?,
): Long? = resumedFromMs?.let { RESUME_HINT_VISIBLE_DURATION_MS }

@Composable
internal fun ResumeHintDismissEffect(
    resumedFromMs: Long?,
    onResumeHintShown: () -> Unit,
) {
    LaunchedEffect(resumedFromMs) {
        resumeHintDismissDelayMs(resumedFromMs)?.let { delayMs ->
            delay(delayMs)
            onResumeHintShown()
        }
    }
}

private const val RESUME_HINT_VISIBLE_DURATION_MS = 1_000L
