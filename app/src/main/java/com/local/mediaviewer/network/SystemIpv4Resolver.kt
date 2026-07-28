package com.local.mediaviewer.network

import com.local.mediaviewer.core.AppError
import com.local.mediaviewer.core.AppResult
import com.local.mediaviewer.settings.ServerUrlValidator
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SystemIpv4Resolver(
    private val lookup: (String) -> Array<InetAddress> = InetAddress::getAllByName,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Ipv4Resolver {
    override suspend fun resolve(host: String): AppResult<List<String>> {
        ServerUrlValidator.parseIpv4(host)?.let { ipv4 ->
            return AppResult.Success(listOf(ipv4))
        }

        return withContext(ioDispatcher) {
            try {
                val addresses = lookup(host)
                    .filterIsInstance<Inet4Address>()
                    .map { address -> requireNotNull(address.hostAddress) }
                if (addresses.isEmpty()) {
                    AppResult.Failure(AppError.NoIpv4Address)
                } else {
                    AppResult.Success(addresses)
                }
            } catch (error: UnknownHostException) {
                AppResult.Failure(AppError.DnsFailure(error.javaClass.simpleName))
            } catch (error: SecurityException) {
                AppResult.Failure(AppError.DnsFailure(error.javaClass.simpleName))
            }
        }
    }
}
