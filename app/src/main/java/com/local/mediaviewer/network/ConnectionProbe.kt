package com.local.mediaviewer.network

import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.ServerShare
import com.local.mediaviewer.model.SessionEndpoint
import com.local.mediaviewer.model.ValidatedServerUrl

/**
 * 保存一次成功连接所确定的端点和动态共享列表。
 */
data class ConnectionTestResult(
    val server: ValidatedServerUrl,
    val resolvedIpv4s: List<String>,
    val endpoint: SessionEndpoint,
    val shares: List<ServerShare> = emptyList(),
)

/**
 * 在解析出的 IPv4 候选中选择可用的 RangeShelf 服务。
 */
interface ConnectionProbe {
    /**
     * 依次探测 IPv4 地址并获取共享发现文档。
     *
     * @param server 已校验的逻辑服务器地址。
     * @param ipv4Candidates DNS 或字面地址生成的 IPv4 候选。
     * @return 首个可用服务器的连接信息及共享列表。
     */
    suspend fun probe(
        server: ValidatedServerUrl,
        ipv4Candidates: List<String>,
    ): AppResult<ConnectionTestResult>
}

/**
 * 提供共享发现接口所需的最小 HTTP GET 能力。
 */
fun interface ShareDiscoveryTransport {
    /**
     * 获取指定共享发现 URL 的响应体。
     *
     * @param url 使用具体 IPv4 构造的发现接口地址。
     * @return 成功响应体或网络、HTTP 协议错误。
     */
    suspend fun get(url: String): AppResult<String>
}
