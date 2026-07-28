package com.local.mediaviewer.model

data class ValidatedServerUrl(
    val logicalBaseUrl: String,
    val host: String,
    val port: Int,
    val isIpv4Literal: Boolean,
)
