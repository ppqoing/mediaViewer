package com.local.mediaviewer.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证动态共享在当前客户端能力范围内的可浏览规则。
 */
class ServerShareTest {
    @Test
    fun `仅匿名且启用目录浏览的共享可进入`() {
        assertTrue(share(directoryBrowsing = true).canBrowse)
        assertFalse(share(directoryBrowsing = false).canBrowse)
        assertFalse(
            share(
                directoryBrowsing = true,
                authenticationMode = ShareAuthenticationMode.BASIC,
            ).canBrowse,
        )
    }

    /**
     * 创建具有固定公开元数据的测试共享。
     *
     * @param directoryBrowsing 是否启用目录浏览。
     * @param authenticationMode 共享认证方式。
     * @return 用于能力判断的共享。
     */
    private fun share(
        directoryBrowsing: Boolean,
        authenticationMode: ShareAuthenticationMode =
            ShareAuthenticationMode.ANONYMOUS,
    ) = ServerShare(
        id = "4f01061d-9b75-4f7d-96db-49c801e96188",
        displayName = "测试",
        urlPrefix = "test",
        directoryBrowsing = directoryBrowsing,
        authenticationMode = authenticationMode,
    )
}
