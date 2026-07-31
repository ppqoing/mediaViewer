package com.local.mediaviewer.ui.player

data class QueueDragUpdate(
    val session: QueueDragSession,
    val crossedIndices: List<Int>,
)

data class QueueDrop(
    val mediaKey: String,
    val toIndex: Int,
)

data class QueueDragSession(
    val mediaKey: String,
    val startIndex: Int,
    val currentIndex: Int = startIndex,
    val totalDisplacementPx: Float = 0f,
    val residualPx: Float = 0f,
) {
    fun advance(
        deltaPx: Float,
        rowExtentPx: Float,
        lastIndex: Int,
    ): QueueDragUpdate {
        require(rowExtentPx > 0f)
        require(lastIndex >= 0)
        val minimumDisplacement = -startIndex * rowExtentPx
        val maximumDisplacement = (lastIndex - startIndex) * rowExtentPx
        val total = (totalDisplacementPx + deltaPx)
            .coerceIn(minimumDisplacement, maximumDisplacement)
        val offsetFromStart = (total / rowExtentPx).toInt()
        val index = (startIndex + offsetFromStart)
            .coerceIn(0, lastIndex)
        val residual = total - (index - startIndex) * rowExtentPx
        val crossedIndices = when {
            index > currentIndex -> (currentIndex + 1..index).toList()
            index < currentIndex -> (currentIndex - 1 downTo index).toList()
            else -> emptyList()
        }
        return QueueDragUpdate(
            copy(
                currentIndex = index,
                totalDisplacementPx = total,
                residualPx = residual,
            ),
            crossedIndices,
        )
    }

    fun finish(): QueueDrop? =
        if (currentIndex == startIndex) {
            null
        } else {
            QueueDrop(mediaKey, currentIndex)
        }
}
