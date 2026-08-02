package com.local.mediaviewer.pdf

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.network.ConnectionTestResult
import com.local.mediaviewer.session.ServerSessionManager
import com.local.mediaviewer.session.ServerSessionState
import java.io.File
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfTemporaryFileRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val initialEndpoint = endpoint("192.0.2.1")
    private val refreshedEndpoint = endpoint("192.0.2.2")

    @Test
    fun `网络失败刷新一次并使用新端点重新下载`() = runTest {
        val session = PdfSession(initialEndpoint, refreshedEndpoint)
        val calls = mutableListOf<String>()
        val repository = DefaultPdfTemporaryFileRepository(
            cacheRoot = temporaryFolder.root,
            client = PdfFileClient { url, destination ->
                calls += url
                if (calls.size == 1) {
                    AppResult.Failure(AppError.NetworkFailure("timeout"))
                } else {
                    destination.writeBytes("%PDF-1.4".encodeToByteArray())
                    AppResult.Success(destination.length())
                }
            },
            session = session,
        )

        val result = repository.acquire(LOGICAL_URL)

        assertTrue(result is AppResult.Success)
        assertEquals(listOf(FIRST_URL, SECOND_URL), calls)
        assertEquals(1, session.refreshCalls)
        assertFalse(temporaryFolder.root.walk().any { it.extension == "part" })
    }

    @Test
    fun `HTTP 404 不刷新端点并删除失败 part 文件`() = runTest {
        val session = PdfSession(initialEndpoint)
        val repository = DefaultPdfTemporaryFileRepository(
            cacheRoot = temporaryFolder.root,
            client = PdfFileClient { _, destination ->
                destination.writeText("incomplete")
                AppResult.Failure(AppError.HttpFailure(404))
            },
            session = session,
        )

        val result = repository.acquire(LOGICAL_URL)

        assertEquals(AppError.HttpFailure(404), (result as AppResult.Failure).error)
        assertEquals(0, session.refreshCalls)
        assertFalse(temporaryFolder.root.walk().any { it.extension == "part" })
    }

    @Test
    fun `第二次网络失败不再刷新并删除 part 文件`() = runTest {
        val session = PdfSession(initialEndpoint, refreshedEndpoint)
        var calls = 0
        val repository = DefaultPdfTemporaryFileRepository(
            cacheRoot = temporaryFolder.root,
            client = PdfFileClient { _, destination ->
                calls += 1
                destination.writeText("incomplete-$calls")
                AppResult.Failure(AppError.NetworkFailure("timeout-$calls"))
            },
            session = session,
        )

        val result = repository.acquire(LOGICAL_URL)

        assertEquals(
            AppError.NetworkFailure("timeout-2"),
            (result as AppResult.Failure).error,
        )
        assertEquals(2, calls)
        assertEquals(1, session.refreshCalls)
        assertFalse(temporaryFolder.root.walk().any { it.extension == "part" })
    }

    @Test
    fun `取消下载删除 part 文件并传播取消`() = runTest {
        val repository = DefaultPdfTemporaryFileRepository(
            cacheRoot = temporaryFolder.root,
            client = PdfFileClient { _, destination ->
                destination.writeText("incomplete")
                throw CancellationException("test cancellation")
            },
            session = PdfSession(initialEndpoint),
        )

        try {
            repository.acquire(LOGICAL_URL)
            throw AssertionError("expected cancellation")
        } catch (_: CancellationException) {
            assertFalse(temporaryFolder.root.walk().any { it.extension == "part" })
        }
    }

    @Test
    fun `release 只删除受控 pdf 缓存文件`() = runTest {
        val repository = successfulRepository()
        val acquired = repository.acquire(LOGICAL_URL) as AppResult.Success<PdfTemporaryFile>
        val outsideFile = temporaryFolder.newFile("outside.pdf").apply { writeText("outside") }

        assertEquals(File(temporaryFolder.root, "pdf"), acquired.value.file.parentFile)

        repository.release(
            PdfTemporaryFile(
                logicalUrl = LOGICAL_URL,
                file = outsideFile,
                byteCount = outsideFile.length(),
            ),
        )
        assertTrue(outsideFile.exists())

        repository.release(acquired.value)

        assertFalse(acquired.value.file.exists())
    }

    @Test
    fun `超过 24 小时的 PDF 和 part 被清理`() = runTest {
        val repository = successfulRepository()
        val cacheDirectory = File(temporaryFolder.root, "pdf").apply { mkdirs() }
        val expiredPdf = File(cacheDirectory, "expired.pdf").apply { writeText("old") }
        val expiredPart = File(cacheDirectory, "expired.part").apply { writeText("old") }
        val nowMs = 2_000_000_000L
        expiredPdf.setLastModified(nowMs - PDF_CACHE_MAX_AGE_MS - 1)
        expiredPart.setLastModified(nowMs - PDF_CACHE_MAX_AGE_MS - 1)

        repository.cleanupExpired(nowMs)

        assertFalse(expiredPdf.exists())
        assertFalse(expiredPart.exists())
    }

    @Test
    fun `刚好 24 小时的 PDF 与更新文件都不清理`() = runTest {
        val repository = successfulRepository()
        val cacheDirectory = File(temporaryFolder.root, "pdf").apply { mkdirs() }
        val boundaryPdf = File(cacheDirectory, "boundary.pdf").apply { writeText("boundary") }
        val freshPart = File(cacheDirectory, "fresh.part").apply { writeText("fresh") }
        val nowMs = 2_000_000_000L
        boundaryPdf.setLastModified(nowMs - PDF_CACHE_MAX_AGE_MS)
        freshPart.setLastModified(nowMs - 1)

        repository.cleanupExpired(nowMs)

        assertTrue(boundaryPdf.exists())
        assertTrue(freshPart.exists())
    }

    private fun successfulRepository(): DefaultPdfTemporaryFileRepository =
        DefaultPdfTemporaryFileRepository(
            cacheRoot = temporaryFolder.root,
            client = PdfFileClient { _, destination ->
                destination.writeBytes("%PDF-1.4".encodeToByteArray())
                AppResult.Success(destination.length())
            },
            session = PdfSession(initialEndpoint),
        )
}

private const val LOGICAL_URL = "http://media.example:8080/books/example.pdf"
private const val FIRST_URL = "http://192.0.2.1:8080/books/example.pdf"
private const val SECOND_URL = "http://192.0.2.2:8080/books/example.pdf"

private fun endpoint(ipv4: String) = SessionEndpoint(
    logicalBaseUrl = "http://media.example:8080",
    requestBaseUrl = "http://$ipv4:8080",
    ipv4 = ipv4,
)

private class PdfSession(
    initial: SessionEndpoint,
    private val refreshed: SessionEndpoint = initial,
) : ServerSessionManager {
    private val mutableState = MutableStateFlow<ServerSessionState>(
        ServerSessionState.Connected(initial, listOf(initial.ipv4)),
    )
    override val state: StateFlow<ServerSessionState> = mutableState
    var refreshCalls = 0
        private set

    override suspend fun connectSaved() = Unit

    override suspend fun testCandidate(
        input: String,
    ): AppResult<ConnectionTestResult> = error("not used: $input")

    override suspend fun saveCandidate(
        result: ConnectionTestResult,
    ) = error("not used: ${result.endpoint.logicalBaseUrl}")

    override suspend fun refreshAfterRequestFailure(): AppResult<SessionEndpoint> {
        refreshCalls += 1
        mutableState.value = ServerSessionState.Connected(
            refreshed,
            listOf(refreshed.ipv4),
        )
        return AppResult.Success(refreshed)
    }
}
