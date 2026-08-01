package com.local.mediaviewer

import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSessionService
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import com.local.mediaviewer.service.ACTION_LOCAL_VIDEO_OUTPUT
import com.local.mediaviewer.service.ACTION_STOP_AND_RELEASE
import com.local.mediaviewer.service.LocalVideoOutputBinder
import com.local.mediaviewer.service.PlaybackService
import com.local.mediaviewer.testing.FakeAppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {
    @get:Rule
    val serviceRule = ServiceTestRule()

    @Test
    fun serviceOwnsOneEngineAndKeepsLocalVideoBinderOffSessionChannel() {
        val application =
            ApplicationProvider.getApplicationContext<MediaViewerApplication>()
        val original = application.container
        val fake = FakeAppContainer(application)
        application.container = fake

        try {
            application.startService(
                Intent(application, PlaybackService::class.java),
            )
            InstrumentationRegistry.getInstrumentation()
                .waitForIdleSync()
            assertEquals(1, fake.playbackEngineCreationCount)
            assertTrue(
                serviceRule.bindService(
                    Intent(
                        ACTION_LOCAL_VIDEO_OUTPUT,
                    ).setClass(application, PlaybackService::class.java),
                ) is LocalVideoOutputBinder,
            )
            serviceRule.unbindService()
            assertFalse(
                serviceRule.bindService(
                    Intent(
                        MediaSessionService.SERVICE_INTERFACE,
                    ).setClass(application, PlaybackService::class.java),
                ) is LocalVideoOutputBinder,
            )
            serviceRule.unbindService()

            application.startService(
                Intent(application, PlaybackService::class.java)
                    .setAction(ACTION_STOP_AND_RELEASE),
            )
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(1, fake.playbackEngineCreationCount)
            assertEquals(1, fake.playbackEngineCloseCount)
        } finally {
            application.container = original
            fake.close()
        }
    }
}
