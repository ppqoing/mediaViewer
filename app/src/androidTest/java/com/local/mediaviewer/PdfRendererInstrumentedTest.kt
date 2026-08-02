package com.local.mediaviewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.pdf.AndroidPdfDocumentFactory
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfRendererInstrumentedTest {
    @Test
    fun open_readsTwoPagePdfAndRendersRequestedWidth() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "pdf-renderer-instrumented-test.pdf")
        createTwoPagePdf(file)

        val handle = when (val result = AndroidPdfDocumentFactory().open(file)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> error("Expected PDF to open: ${result.error}")
        }
        try {
            assertEquals(2, handle.pageCount)
            assertTrue(handle.pageSizes.all { it.widthPoints > 0 && it.heightPoints > 0 })

            val bitmap = when (val result = handle.renderPage(0, 200)) {
                is AppResult.Success -> result.value
                is AppResult.Failure -> error("Expected page to render: ${result.error}")
            }
            assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
            assertEquals(200, bitmap.width)
        } finally {
            handle.close()
            file.delete()
        }
    }

    private fun createTwoPagePdf(file: File) {
        val document = PdfDocument()
        try {
            repeat(2) { index ->
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(160, 240, index + 1).create(),
                )
                page.canvas.drawColor(Color.WHITE)
                document.finishPage(page)
            }
            FileOutputStream(file).use(document::writeTo)
        } finally {
            document.close()
        }
    }
}
