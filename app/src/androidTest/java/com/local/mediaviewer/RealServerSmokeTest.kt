package com.local.mediaviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.network.DefaultCaddyDirectoryClient
import com.local.mediaviewer.network.DefaultConnectionProbe
import com.local.mediaviewer.network.DefaultShareDiscoveryParser
import com.local.mediaviewer.network.OkHttpShareDiscoveryTransport
import com.local.mediaviewer.network.SystemIpv4Resolver
import com.local.mediaviewer.settings.ServerUrlValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import okhttp3.HttpUrl.Companion.toHttpUrl

@RunWith(AndroidJUnit4::class)
class RealServerSmokeTest {
    @Test
    fun discoveredAnonymousSharesReturnCaddyDirectories() =
        runBlocking {
            val baseUrl =
                InstrumentationRegistry.getArguments()
                    .getString("realServerBaseUrl")
            assumeTrue(
                "仅在传入 realServerBaseUrl 时执行真实服务器烟测",
                !baseUrl.isNullOrBlank(),
            )
            val server = (
                ServerUrlValidator.validate(
                    requireNotNull(baseUrl),
                ) as AppResult.Success
            ).value
            val addresses = (
                SystemIpv4Resolver().resolve(
                    server.host,
                ) as AppResult.Success
            ).value
            val result = DefaultConnectionProbe(
                transport = OkHttpShareDiscoveryTransport(),
                parser = DefaultShareDiscoveryParser(),
            ).probe(server, addresses)
            val connection = (result as AppResult.Success).value
            val endpoint = connection.endpoint
            val client = DefaultCaddyDirectoryClient()

            for (share in connection.shares.filter { it.canBrowse }) {
                val logicalUrl = endpoint.logicalBaseUrl.toHttpUrl()
                    .newBuilder()
                    .addPathSegment(share.urlPrefix)
                    .addPathSegment("")
                    .build()
                    .toString()
                val requestUrl = endpoint.requestUrlFor(logicalUrl)
                val listing = client.listDirectory(
                    logicalDirectoryUrl = logicalUrl,
                    requestDirectoryUrl = requestUrl,
                )
                assertTrue(
                    "${share.displayName} 必须返回合法 Caddy JSON",
                    listing is AppResult.Success,
                )
            }
        }
}
