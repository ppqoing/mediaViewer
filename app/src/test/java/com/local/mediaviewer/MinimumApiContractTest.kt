package com.local.mediaviewer

import android.content.Context
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
}
