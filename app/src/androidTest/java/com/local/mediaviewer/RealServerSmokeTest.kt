package com.local.mediaviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.model.RootShare
import com.local.mediaviewer.network.DefaultCaddyDirectoryClient
import com.local.mediaviewer.network.DefaultConnectionProbe
import com.local.mediaviewer.network.DefaultDirectoryJsonParser
import com.local.mediaviewer.network.OkHttpDirectoryProbeTransport
import com.local.mediaviewer.network.SystemIpv4Resolver
import com.local.mediaviewer.settings.ServerUrlValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealServerSmokeTest {
    @Test
    fun bothConfiguredRootsReturnCaddyDirectories() =
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
                transport = OkHttpDirectoryProbeTransport(),
                parser = DefaultDirectoryJsonParser(),
            ).probe(server, addresses)
            val endpoint =
                (result as AppResult.Success).value.endpoint
            val client = DefaultCaddyDirectoryClient()

            for (root in RootShare.entries) {
                val logicalUrl =
                    endpoint.logicalBaseUrl + root.path
                val requestUrl =
                    endpoint.requestBaseUrl + root.path
                val listing = client.listDirectory(
                    logicalDirectoryUrl = logicalUrl,
                    requestDirectoryUrl = requestUrl,
                )
                assertTrue(
                    "${root.path} 必须返回合法 Caddy JSON",
                    listing is AppResult.Success,
                )
            }
        }
}
