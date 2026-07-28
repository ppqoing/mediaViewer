package com.local.mediaviewer.network

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.DirectoryEntry
import java.time.DateTimeException
import java.time.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl

interface DirectoryJsonParser {
    fun parse(
        json: String,
        logicalDirectoryUrl: String,
        requestDirectoryUrl: String,
    ): AppResult<List<DirectoryEntry>>
}

class DefaultDirectoryJsonParser(
    private val jsonCodec: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
        explicitNulls = true
    },
) : DirectoryJsonParser {
    override fun parse(
        json: String,
        logicalDirectoryUrl: String,
        requestDirectoryUrl: String,
    ): AppResult<List<DirectoryEntry>> = try {
        val logicalBase = logicalDirectoryUrl.toHttpUrl()
        val requestBase = requestDirectoryUrl.toHttpUrl()
        val entries = jsonCodec.decodeFromString<List<CaddyEntryDto>>(json)
            .map { dto ->
                val logical = logicalBase.resolve(dto.url)
                    ?: throw IllegalArgumentException("invalid logical relative URL")
                val request = requestBase.resolve(dto.url)
                    ?: throw IllegalArgumentException("invalid request relative URL")
                DirectoryEntry(
                    name = dto.name,
                    size = dto.size,
                    modifiedAt = Instant.parse(dto.modifiedAt),
                    mode = dto.mode,
                    isDirectory = dto.isDirectory,
                    isSymlink = dto.isSymlink,
                    logicalUrl = logical.toString(),
                    requestUrl = request.toString(),
                    kind = MediaClassifier.classify(dto.name, dto.isDirectory),
                )
            }
            .sortedWith(
                compareByDescending<DirectoryEntry> { it.isDirectory }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )
        AppResult.Success(entries)
    } catch (_: SerializationException) {
        AppResult.Failure(AppError.InvalidDirectoryResponse)
    } catch (_: IllegalArgumentException) {
        AppResult.Failure(AppError.InvalidDirectoryResponse)
    } catch (_: DateTimeException) {
        AppResult.Failure(AppError.InvalidDirectoryResponse)
    }
}
