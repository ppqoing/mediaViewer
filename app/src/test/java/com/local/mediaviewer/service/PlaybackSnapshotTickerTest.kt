package com.local.mediaviewer.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSnapshotTickerTest {
    @Test
    fun `服务存活时周期保存且关闭后停止`() = runTest {
        var saves = 0
        val ticker = PlaybackSnapshotTicker(
            scope = this,
            intervalMs = 5_000L,
            save = { saves += 1 },
        )

        advanceTimeBy(5_001L)
        runCurrent()
        assertEquals(1, saves)

        ticker.close()
        advanceTimeBy(5_001L)
        runCurrent()
        assertEquals(1, saves)
    }
}
