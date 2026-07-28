package com.local.mediaviewer.image

import java.io.IOException
import java.util.Collections
import java.util.IdentityHashMap

enum class ImageLoadFailureKind {
    NETWORK,
    DECODE,
}

data class ImageItemFailure(
    val message: String,
    val kind: ImageLoadFailureKind,
)

fun ImageLoadFailureKind.userMessage(): String =
    when (this) {
        ImageLoadFailureKind.NETWORK ->
            "图片网络加载失败"
        ImageLoadFailureKind.DECODE ->
            "图片解码失败"
    }

fun classifyImageLoadFailure(
    throwable: Throwable,
): ImageLoadFailureKind {
    val visited = Collections.newSetFromMap(
        IdentityHashMap<Throwable, Boolean>(),
    )
    var current: Throwable? = throwable
    var depth = 0
    while (
        current != null &&
        depth < MAX_CAUSE_DEPTH &&
        visited.add(current)
    ) {
        if (current is IOException) {
            return ImageLoadFailureKind.NETWORK
        }
        current = current.cause
        depth += 1
    }
    return ImageLoadFailureKind.DECODE
}

private const val MAX_CAUSE_DEPTH = 64
