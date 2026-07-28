package com.local.mediaviewer.network

import com.local.mediaviewer.core.AppResult

interface Ipv4Resolver {
    suspend fun resolve(host: String): AppResult<List<String>>
}
