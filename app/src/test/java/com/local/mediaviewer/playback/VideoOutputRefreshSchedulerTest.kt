package com.local.mediaviewer.playback

import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoOutputRefreshSchedulerTest {
    @Test
    fun `synchronous vout is serialized after refresh and cancels fallback`() {
        val ownerTasks = ArrayDeque<Runnable>()
        val fallbackTasks = mutableListOf<Runnable>()
        val outputEvents = mutableListOf<String>()
        lateinit var scheduler: VideoOutputRefreshScheduler
        scheduler = VideoOutputRefreshScheduler(
            postToOwner = ownerTasks::addLast,
            postDelayedToOwner = { task, _ -> fallbackTasks += task },
            removeFromOwner = fallbackTasks::remove,
            isActive = { true },
            updateVideoSurfaces = {
                outputEvents += "update"
                if (outputEvents.size == 1) scheduler.onVout()
            },
        )

        scheduler.refresh {
            outputEvents += "rebind"
        }

        assertEquals(listOf("update"), outputEvents)
        assertEquals(1, ownerTasks.size)
        assertEquals(1, fallbackTasks.size)

        ownerTasks.removeFirst().run()
        fallbackTasks.toList().forEach(Runnable::run)

        assertEquals(listOf("update", "update"), outputEvents)
        assertTrue(fallbackTasks.isEmpty())
    }

    @Test
    fun `queued vout rechecks active state before updating surfaces`() {
        val ownerTasks = ArrayDeque<Runnable>()
        var active = true
        var updateCalls = 0
        val scheduler = VideoOutputRefreshScheduler(
            postToOwner = ownerTasks::addLast,
            postDelayedToOwner = { _, _ -> },
            removeFromOwner = {},
            isActive = { active },
            updateVideoSurfaces = { updateCalls += 1 },
        )

        scheduler.onVout()
        active = false
        ownerTasks.removeFirst().run()

        assertEquals(0, updateCalls)
    }
}
