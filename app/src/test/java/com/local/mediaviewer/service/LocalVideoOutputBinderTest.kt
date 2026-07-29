package com.local.mediaviewer.service

import android.content.Context
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.local.mediaviewer.playback.VideoScaleMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LocalVideoOutputBinderTest {
    @Test
    fun `same uid attach and detach are idempotent`() = runTest {
        val engine = ServiceTestEngine()
        val coordinator = serviceTestCoordinator(this, engine)
        val binder = LocalVideoOutputBinder(
            coordinator = coordinator,
            callingUid = { 42 },
            processUid = { 42 },
        )
        val host = FrameLayout(
            ApplicationProvider.getApplicationContext<Context>(),
        )

        binder.attach(host)
        binder.attach(host)
        binder.setScaleMode(VideoScaleMode.FILL_CROP)
        binder.detach()
        binder.detach()

        assertEquals(listOf(host), engine.attachedHosts)
        assertEquals(listOf(VideoScaleMode.FILL_CROP), engine.scaleModes)
        assertEquals(1, engine.detachCalls)
        coordinator.close()
    }

    @Test
    fun `different uid cannot call any video output operation`() = runTest {
        val coordinator = serviceTestCoordinator(this)
        val binder = LocalVideoOutputBinder(
            coordinator = coordinator,
            callingUid = { 7 },
            processUid = { 42 },
        )
        val host = FrameLayout(
            ApplicationProvider.getApplicationContext<Context>(),
        )

        listOf<() -> Unit>(
            { binder.attach(host) },
            { binder.detach() },
            { binder.setScaleMode(VideoScaleMode.BEST_FIT) },
        ).forEach { operation ->
            assertThrows(SecurityException::class.java, operation)
        }
        coordinator.close()
    }

    @Test
    fun `released binder rejects old references and local channel cannot rebind`() = runTest {
        val engine = ServiceTestEngine()
        val coordinator = serviceTestCoordinator(this, engine)
        val binder = LocalVideoOutputBinder(
            coordinator = coordinator,
            callingUid = { 42 },
            processUid = { 42 },
        )
        val channel = LocalVideoOutputBindingChannel(binder)
        val host = FrameLayout(
            ApplicationProvider.getApplicationContext<Context>(),
        )
        binder.attach(host)

        channel.invalidate()
        val attachCalls = engine.attachedHosts.size
        val detachCalls = engine.detachCalls
        val scaleCalls = engine.scaleModes.size

        assertEquals(null, channel.bind())
        listOf<() -> Unit>(
            { binder.attach(host) },
            { binder.detach() },
            { binder.setScaleMode(VideoScaleMode.BEST_FIT) },
        ).forEach { operation ->
            assertThrows(IllegalStateException::class.java, operation)
        }
        assertEquals(attachCalls, engine.attachedHosts.size)
        assertEquals(detachCalls, engine.detachCalls)
        assertEquals(scaleCalls, engine.scaleModes.size)
        coordinator.close()
    }
}
