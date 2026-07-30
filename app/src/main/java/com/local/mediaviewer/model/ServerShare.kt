package com.local.mediaviewer.model

/**
 * 描述 RangeShelf 共享支持的认证方式。
 */
enum class ShareAuthenticationMode {
    ANONYMOUS,
    BASIC,
}

/**
 * 表示由 RangeShelf 发现接口返回的单个共享入口。
 *
 * 本类型只保存远程浏览需要的公开元数据，不包含服务器本地路径或认证凭据。
 */
data class ServerShare(
    val id: String,
    val displayName: String,
    val urlPrefix: String,
    val directoryBrowsing: Boolean,
    val authenticationMode: ShareAuthenticationMode,
) {
    /**
     * 表示当前版本客户端能否直接浏览此共享。
     */
    val canBrowse: Boolean
        get() = directoryBrowsing &&
            authenticationMode == ShareAuthenticationMode.ANONYMOUS
}
