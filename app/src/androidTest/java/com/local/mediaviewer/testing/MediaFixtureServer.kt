package com.local.mediaviewer.testing

import java.io.Closeable
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer

class MediaFixtureServer(
    private val fixtures: MediaFixtures,
    imageCount: Int = 1,
) : Closeable {
    private val server = MockWebServer()
    private val rangeCounts =
        ConcurrentHashMap<String, AtomicInteger>()
    private val rangeHeaders =
        ConcurrentHashMap<String, ConcurrentLinkedQueue<String>>()
    private val mediaRequests = AtomicInteger()
    private val mediaPaths =
        ConcurrentHashMap.newKeySet<String>()
    private val imageFiles =
        (1..imageCount).associate { index ->
            val name =
                "page-" +
                    index.toString()
                        .padStart(3, '0') +
                    ".png"
            name to fixtures.png
        }
    private val files = linkedMapOf(
        "sample.mp4" to fixtures.mp4,
        "sample.wav" to fixtures.wav,
        "sample.png" to fixtures.png,
    ).apply {
        putAll(imageFiles)
    }

    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(
                request: RecordedRequest,
            ): MockResponse = responseFor(request)
        }
        server.start()
    }

    fun url(path: String): String =
        server.url(path).toString()

    fun rangeRequestCount(path: String): Int =
        rangeCounts[path]?.get() ?: 0

    fun rangeRequests(path: String): List<String> =
        rangeHeaders[path]?.toList().orEmpty()

    fun mediaRequestCount(): Int =
        mediaRequests.get()

    fun requestedMediaPaths(): Set<String> =
        mediaPaths.toSet()

    override fun close() {
        server.close()
    }

    private fun responseFor(
        request: RecordedRequest,
    ): MockResponse {
        val path = request.url.encodedPath
        if (path == "/middle/" || path == "/pik/") {
            return MockResponse.Builder()
                .code(200)
                .setHeader(
                    "Content-Type",
                    "application/json; charset=utf-8",
                )
                .body(directoryJson())
                .build()
        }

        val prefix = when {
            path.startsWith("/middle/") -> "/middle/"
            path.startsWith("/pik/") -> "/pik/"
            else -> return MockResponse(code = 404)
        }
        val file = files[path.removePrefix(prefix)]
            ?: return MockResponse(code = 404)
        if (request.method != "HEAD") {
            mediaRequests.incrementAndGet()
            mediaPaths.add(path)
        }
        return mediaResponse(
            request = request,
            path = path,
            file = file,
        )
    }

    private fun directoryJson(): String =
        files.entries.joinToString(
            prefix = "[",
            postfix = "]",
        ) { (name, file) ->
            """{"name":"$name","size":${file.length()},"url":"$name","mod_time":"2026-07-28T00:00:00Z","mode":420,"is_dir":false,"is_symlink":false}"""
        }

    private fun mediaResponse(
        request: RecordedRequest,
        path: String,
        file: File,
    ): MockResponse {
        val bytes = file.readBytes()
        val contentType = when (file.extension) {
            "mp4" -> "video/mp4"
            "wav" -> "audio/wav"
            "png" -> "image/png"
            else -> "application/octet-stream"
        }
        if (request.method == "HEAD") {
            return MockResponse.Builder()
                .code(200)
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("Content-Type", contentType)
                .setHeader("Content-Length", bytes.size)
                .build()
        }

        val rangeHeader = request.headers["Range"]
        if (rangeHeader == null) {
            return MockResponse.Builder()
                .code(200)
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("Content-Type", contentType)
                .body(Buffer().write(bytes))
                .build()
        }

        rangeCounts.computeIfAbsent(path) {
            AtomicInteger()
        }.incrementAndGet()
        rangeHeaders.computeIfAbsent(path) {
            ConcurrentLinkedQueue()
        }.add(rangeHeader)
        val match = RANGE_PATTERN.matchEntire(rangeHeader)
            ?: return rangeNotSatisfiable(bytes.size)
        val startText = match.groupValues[1]
        val endText = match.groupValues[2]
        if (startText.isEmpty() && endText.isEmpty()) {
            return rangeNotSatisfiable(bytes.size)
        }

        val start: Long
        val end: Long
        if (startText.isEmpty()) {
            val suffixLength = endText.toLongOrNull()
                ?: return rangeNotSatisfiable(bytes.size)
            if (suffixLength <= 0L) {
                return rangeNotSatisfiable(bytes.size)
            }
            start = (bytes.size - suffixLength)
                .coerceAtLeast(0L)
            end = bytes.lastIndex.toLong()
        } else {
            start = startText.toLongOrNull()
                ?: return rangeNotSatisfiable(bytes.size)
            if (start >= bytes.size) {
                return rangeNotSatisfiable(bytes.size)
            }
            val requestedEnd = endText
                .takeIf(String::isNotEmpty)
                ?.toLongOrNull()
            if (
                endText.isNotEmpty() &&
                requestedEnd == null
            ) {
                return rangeNotSatisfiable(bytes.size)
            }
            end = minOf(
                requestedEnd ?: bytes.lastIndex.toLong(),
                bytes.lastIndex.toLong(),
            )
        }
        if (end < start) {
            return rangeNotSatisfiable(bytes.size)
        }

        val length = (end - start + 1).toInt()
        return MockResponse.Builder()
            .code(206)
            .setHeader("Accept-Ranges", "bytes")
            .setHeader("Content-Type", contentType)
            .setHeader(
                "Content-Range",
                "bytes $start-$end/${bytes.size}",
            )
            .body(
                Buffer().write(
                    bytes,
                    start.toInt(),
                    length,
                ),
            )
            .build()
    }

    private fun rangeNotSatisfiable(size: Int): MockResponse =
        MockResponse.Builder()
            .code(416)
            .setHeader("Content-Range", "bytes */$size")
            .build()

    private companion object {
        val RANGE_PATTERN = Regex("""bytes=(\d*)-(\d*)""")
    }
}
