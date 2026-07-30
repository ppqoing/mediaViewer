package com.local.mediaviewer.network

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.ShareAuthenticationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证共享发现协议的版本、Unicode、顺序和外部输入约束。
 */
class ShareDiscoveryParserTest {
    private val parser = DefaultShareDiscoveryParser()

    @Test
    fun `按服务器顺序解析 Unicode 与认证状态`() {
        val result = parser.parse(
            """
            {
              "schemaVersion": 1,
              "shares": [
                {
                  "id": "4F01061D-9B75-4F7D-96DB-49C801E96188",
                  "displayName": "家庭 相册",
                  "urlPrefix": "家庭 相册",
                  "directoryBrowsing": true,
                  "authenticationMode": "anonymous"
                },
                {
                  "id": "0447a975-eccb-4802-a8f5-5f574971876c",
                  "displayName": "私有",
                  "urlPrefix": "私有",
                  "directoryBrowsing": true,
                  "authenticationMode": "basic"
                }
              ]
            }
            """.trimIndent(),
        )

        val shares = (result as AppResult.Success).value
        assertEquals(listOf("家庭 相册", "私有"), shares.map { it.displayName })
        assertEquals(
            "4f01061d-9b75-4f7d-96db-49c801e96188",
            shares.first().id,
        )
        assertTrue(shares.first().canBrowse)
        assertEquals(ShareAuthenticationMode.BASIC, shares.last().authenticationMode)
        assertFalse(shares.last().canBrowse)
    }

    @Test
    fun `未知协议版本返回兼容性错误`() {
        val result = parser.parse("""{"schemaVersion":2,"shares":[]}""")

        assertEquals(
            AppError.UnsupportedDiscoveryVersion(2),
            (result as AppResult.Failure).error,
        )
    }

    @Test
    fun `重复标识或非法路径前缀被拒绝`() {
        val duplicateId = "4f01061d-9b75-4f7d-96db-49c801e96188"
        val result = parser.parse(
            """
            {
              "schemaVersion": 1,
              "shares": [
                {
                  "id": "$duplicateId",
                  "displayName": "一",
                  "urlPrefix": "one",
                  "directoryBrowsing": true,
                  "authenticationMode": "anonymous"
                },
                {
                  "id": "$duplicateId",
                  "displayName": "二",
                  "urlPrefix": "bad/path",
                  "directoryBrowsing": true,
                  "authenticationMode": "anonymous"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(
            AppError.InvalidDiscoveryResponse,
            (result as AppResult.Failure).error,
        )
    }
}
