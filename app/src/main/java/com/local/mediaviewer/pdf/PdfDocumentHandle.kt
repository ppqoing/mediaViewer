package com.local.mediaviewer.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.core.DefaultDispatcherProvider
import com.local.mediaviewer.core.DispatcherProvider
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class PdfPageSize(
    val pageIndex: Int,
    val widthPoints: Int,
    val heightPoints: Int,
)

interface PdfDocumentHandle : AutoCloseable {
    val pageCount: Int
    val pageSizes: List<PdfPageSize>

    suspend fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int,
    ): AppResult<Bitmap>
}

fun interface PdfDocumentFactory {
    suspend fun open(file: File): AppResult<PdfDocumentHandle>
}

class AndroidPdfDocumentFactory(
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) : PdfDocumentFactory {
    override suspend fun open(file: File): AppResult<PdfDocumentHandle> =
        withContext(dispatchers.io) {
            var descriptor: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                descriptor = ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_READ_ONLY,
                )
                renderer = PdfRenderer(descriptor)
                val pageCount = renderer.pageCount
                if (pageCount <= 0) {
                    return@withContext AppResult.Failure(AppError.InvalidPdfDocument)
                }

                val pageSizes = (0 until pageCount).map { pageIndex ->
                    renderer.openPage(pageIndex).use { page ->
                        PdfPageSize(
                            pageIndex = pageIndex,
                            widthPoints = page.width,
                            heightPoints = page.height,
                        )
                    }
                }
                val handle = AndroidPdfDocumentHandle(
                    renderer = renderer,
                    pageCount = pageCount,
                    pageSizes = pageSizes,
                    dispatchers = dispatchers,
                )
                renderer = null
                descriptor = null
                AppResult.Success(handle)
            } catch (error: SecurityException) {
                AppResult.Failure(AppError.EncryptedPdfDocument)
            } catch (error: IOException) {
                AppResult.Failure(AppError.InvalidPdfDocument)
            } catch (error: IllegalArgumentException) {
                AppResult.Failure(AppError.InvalidPdfDocument)
            } finally {
                if (renderer != null) {
                    renderer.close()
                } else {
                    descriptor?.close()
                }
            }
        }
}

private class AndroidPdfDocumentHandle(
    private val renderer: PdfRenderer,
    override val pageCount: Int,
    override val pageSizes: List<PdfPageSize>,
    private val dispatchers: DispatcherProvider,
) : PdfDocumentHandle {
    private val mutex = Mutex()
    private var closed = false

    override suspend fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int,
    ): AppResult<Bitmap> = withContext(dispatchers.io) {
        mutex.withLock {
            if (closed || pageIndex !in 0 until pageCount || targetWidthPx <= 0) {
                return@withLock AppResult.Failure(
                    AppError.PdfPageRenderFailure(pageIndex + 1),
                )
            }

            try {
                renderer.openPage(pageIndex).use { page ->
                    if (page.width <= 0 || page.height <= 0) {
                        return@withLock AppResult.Failure(
                            AppError.PdfPageRenderFailure(pageIndex + 1),
                        )
                    }
                    val targetHeightPx = (
                        page.height.toDouble() * targetWidthPx / page.width
                        ).roundToInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(
                        targetWidthPx,
                        targetHeightPx,
                        Bitmap.Config.ARGB_8888,
                    )
                    page.render(
                        bitmap,
                        null,
                        null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                    )
                    AppResult.Success(bitmap)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppResult.Failure(AppError.PdfPageRenderFailure(pageIndex + 1))
            }
        }
    }

    override fun close() {
        runBlocking {
            mutex.withLock {
                if (closed) return@withLock
                closed = true
                renderer.close()
            }
        }
    }
}
