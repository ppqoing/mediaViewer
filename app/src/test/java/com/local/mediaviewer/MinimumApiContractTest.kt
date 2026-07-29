package com.local.mediaviewer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.DefaultDispatcherProvider
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class MinimumApiContractTest {
    @Test
    fun `Robolectric 可在 API 29 装载应用资源`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals("mediaviewer", context.getString(R.string.app_name))
        assertEquals("com.local.mediaviewer", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun `共享错误提供简体中文信息`() {
        assertEquals("未解析到 IPv4", AppError.NoIpv4Address.userMessage)
        assertEquals("图片加载失败", AppError.ImageLoadFailure.userMessage)
    }

    @Test
    fun `默认调度器映射到对应协程调度器`() {
        assertSame(Dispatchers.IO, DefaultDispatcherProvider.io)
        assertSame(Dispatchers.Default, DefaultDispatcherProvider.default)
        assertSame(Dispatchers.Main, DefaultDispatcherProvider.main)
    }

    @Test
    fun `后台播放服务声明媒体前台服务类型和所需权限`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager
        val service = ComponentName(
            context.packageName,
            "${context.packageName}.service.PlaybackService",
        )

        val info = packageManager.getServiceInfo(service, 0)

        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            info.foregroundServiceType and
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        val requestedPermissions = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        ).requestedPermissions.orEmpty().toSet()
        assertEquals(
            setOf(
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            ),
            requestedPermissions.intersect(
                setOf(
                    "android.permission.FOREGROUND_SERVICE",
                    "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
                ),
            ),
        )
        assertEquals(
            emptyList<Any>(),
            packageManager.queryBroadcastReceivers(
                Intent(Intent.ACTION_BOOT_COMPLETED)
                    .setPackage(context.packageName),
                0,
            ),
        )
    }
}
