package com.local.mediaviewer.image

import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageLoadFailureTest {
    @Test
    fun `IO 异常归类网络而解码异常归类解码`() {
        assertEquals(
            ImageLoadFailureKind.NETWORK,
            classifyImageLoadFailure(
                IOException("timeout"),
            ),
        )
        assertEquals(
            ImageLoadFailureKind.NETWORK,
            classifyImageLoadFailure(
                IllegalStateException(
                    "wrapper",
                    SocketTimeoutException(),
                ),
            ),
        )
        assertEquals(
            ImageLoadFailureKind.DECODE,
            classifyImageLoadFailure(
                IllegalArgumentException(
                    "bad bitmap",
                ),
            ),
        )
    }

    @Test
    fun `失败类型提供固定中文信息`() {
        assertEquals(
            "图片网络加载失败",
            ImageLoadFailureKind.NETWORK.userMessage(),
        )
        assertEquals(
            "图片解码失败",
            ImageLoadFailureKind.DECODE.userMessage(),
        )
    }
}
