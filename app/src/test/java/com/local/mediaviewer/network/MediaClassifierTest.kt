package com.local.mediaviewer.network

import com.local.mediaviewer.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaClassifierTest {
    @Test
    fun `扩展名不区分大小写且未知文件不隐藏`() {
        assertEquals(MediaKind.VIDEO, MediaClassifier.classify("电影.MKV", false))
        assertEquals(MediaKind.AUDIO, MediaClassifier.classify("音轨.FlAc", false))
        assertEquals(MediaKind.IMAGE, MediaClassifier.classify("海报.WeBp", false))
        assertEquals(MediaKind.UNKNOWN, MediaClassifier.classify("archive.bin", false))
        assertEquals(MediaKind.UNKNOWN, MediaClassifier.classify("README", false))
        assertEquals(MediaKind.DIRECTORY, MediaClassifier.classify("folder.mp4", true))
    }
}
