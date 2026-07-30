package com.local.mediaviewer.network

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.ServerShare
import com.local.mediaviewer.model.ShareAuthenticationMode
import java.text.BreakIterator
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * 解析并校验 RangeShelf 共享发现响应。
 */
interface ShareDiscoveryParser {
    /**
     * 把共享发现 JSON 转换为客户端可用的共享列表。
     *
     * @param json RangeShelf 共享发现接口返回的 JSON。
     * @return 成功时保持服务器顺序的共享列表，失败时返回明确的协议错误。
     */
    fun parse(json: String): AppResult<List<ServerShare>>
}

/**
 * 使用 kotlinx.serialization 实现版本化共享发现协议。
 */
class DefaultShareDiscoveryParser(
    private val jsonCodec: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
        explicitNulls = true
    },
) : ShareDiscoveryParser {
    /**
     * 解析版本 1 共享发现文档并校验所有外部字段。
     *
     * @param json RangeShelf 共享发现接口返回的 JSON。
     * @return 可安全用于导航的共享列表。
     */
    override fun parse(json: String): AppResult<List<ServerShare>> = try {
        val response = jsonCodec.decodeFromString<ShareDiscoveryResponseDto>(json)
        if (response.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            return AppResult.Failure(
                AppError.UnsupportedDiscoveryVersion(response.schemaVersion),
            )
        }

        val shares = response.shares.map(::toServerShare)
        if (
            shares.map(ServerShare::id).toSet().size != shares.size ||
            shares.map(ServerShare::urlPrefix).toSet().size != shares.size
        ) {
            return AppResult.Failure(AppError.InvalidDiscoveryResponse)
        }
        AppResult.Success(shares)
    } catch (_: SerializationException) {
        AppResult.Failure(AppError.InvalidDiscoveryResponse)
    } catch (_: IllegalArgumentException) {
        AppResult.Failure(AppError.InvalidDiscoveryResponse)
    }

    /**
     * 把已反序列化的外部数据映射为内部模型。
     *
     * @param dto 单个共享的协议数据。
     * @return 已完成标识符、前缀和认证方式校验的共享。
     * @throws IllegalArgumentException 当任意字段不满足协议约束时抛出。
     */
    private fun toServerShare(dto: ShareDiscoveryEntryDto): ServerShare {
        val normalizedId = UUID.fromString(dto.id).toString()
        require(dto.displayName.isNotBlank())
        require(isValidUrlPrefix(dto.urlPrefix))
        val authenticationMode = when (dto.authenticationMode) {
            "anonymous" -> ShareAuthenticationMode.ANONYMOUS
            "basic" -> ShareAuthenticationMode.BASIC
            else -> throw IllegalArgumentException("未知认证方式")
        }
        return ServerShare(
            id = normalizedId,
            displayName = dto.displayName,
            urlPrefix = dto.urlPrefix,
            directoryBrowsing = dto.directoryBrowsing,
            authenticationMode = authenticationMode,
        )
    }

    /**
     * 镜像 RangeShelf 对单段 Unicode URL 前缀的约束。
     *
     * @param value 待校验的 URL 前缀。
     * @return 前缀是否可安全作为单个 URL 路径段。
     */
    private fun isValidUrlPrefix(value: String): Boolean {
        if (
            value.isEmpty() ||
            !Normalizer.isNormalized(value, Normalizer.Form.NFC) ||
            value != value.trim() ||
            value == "." ||
            value == ".." ||
            value.any { it in "/\\?#%" || it.isISOControl() }
        ) {
            return false
        }
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(value)
        var count = 0
        var boundary = iterator.first()
        while (boundary != BreakIterator.DONE) {
            val next = iterator.next()
            if (next != BreakIterator.DONE) count++
            boundary = next
        }
        return count in 1..64
    }

    private companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
    }
}

/**
 * RangeShelf 共享发现文档的根协议结构。
 */
@Serializable
private data class ShareDiscoveryResponseDto(
    val schemaVersion: Int,
    val shares: List<ShareDiscoveryEntryDto>,
)

/**
 * RangeShelf 共享发现文档的入口协议结构。
 */
@Serializable
private data class ShareDiscoveryEntryDto(
    val id: String,
    val displayName: String,
    val urlPrefix: String,
    val directoryBrowsing: Boolean,
    val authenticationMode: String,
)
