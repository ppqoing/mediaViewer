package com.local.mediaviewer.queue

import kotlin.random.Random

object QueueNavigator {
    fun replace(
        items: List<QueueMediaItem>,
        startMediaKey: String,
        mode: PlaybackMode,
        random: Random,
    ): PlaybackQueue {
        val accepted = distinctPlayable(items)
        val current = accepted.firstOrNull { it.mediaKey == startMediaKey }?.mediaKey
            ?: accepted.firstOrNull()?.mediaKey
        val queue = PlaybackQueue(
            items = accepted,
            currentMediaKey = current,
            mode = mode,
        )
        return if (mode == PlaybackMode.SHUFFLE) {
            queue.withNewShuffleOrder(random)
        } else {
            queue
        }
    }

    fun select(queue: PlaybackQueue, mediaKey: String): PlaybackQueue {
        if (queue.items.none { it.mediaKey == mediaKey }) return queue
        if (queue.mode != PlaybackMode.SHUFFLE) {
            return queue.copy(currentMediaKey = mediaKey)
        }

        val keys = queue.items.map { it.mediaKey }
        val retained = queue.shuffleOrder.filter { it in keys }
        val order = retained + keys.filterNot { it in retained }
        return queue.copy(
            currentMediaKey = mediaKey,
            shuffleOrder = order,
            shuffleCursor = order.indexOf(mediaKey),
        )
    }

    fun addNext(queue: PlaybackQueue, item: QueueMediaItem): PlaybackQueue {
        if (!item.isPlayable()) return queue

        val withoutItem = queue.items.filterNot { it.mediaKey == item.mediaKey }
        val currentIndex = withoutItem.indexOfFirst { it.mediaKey == queue.currentMediaKey }
        val insertionIndex = if (currentIndex >= 0) currentIndex + 1 else withoutItem.size
        val updated = withoutItem.toMutableList().apply { add(insertionIndex, item) }
        return queue.withUpdatedItems(updated)
    }

    fun append(queue: PlaybackQueue, item: QueueMediaItem): PlaybackQueue {
        if (!item.isPlayable()) return queue

        val updated = queue.items.filterNot { it.mediaKey == item.mediaKey } + item
        return queue.withUpdatedItems(updated)
    }

    fun move(queue: PlaybackQueue, mediaKey: String, toIndex: Int): PlaybackQueue {
        val item = queue.items.firstOrNull { it.mediaKey == mediaKey } ?: return queue
        val updated = queue.items.filterNot { it.mediaKey == mediaKey }.toMutableList()
        updated.add(toIndex.coerceIn(0, updated.size), item)
        return queue.withUpdatedItems(updated)
    }

    fun addAll(
        queue: PlaybackQueue,
        index: Int,
        items: List<QueueMediaItem>,
    ): PlaybackQueue {
        val inserted = distinctPlayable(items)
        if (inserted.isEmpty()) return queue
        val insertedKeys = inserted.mapTo(mutableSetOf()) { it.mediaKey }
        val retained = queue.items.filterNot { it.mediaKey in insertedKeys }.toMutableList()
        retained.addAll(index.coerceIn(0, retained.size), inserted)
        return queue.withUpdatedItems(retained)
    }

    fun moveRange(
        queue: PlaybackQueue,
        fromIndex: Int,
        toIndex: Int,
        newIndex: Int,
    ): PlaybackQueue {
        val from = fromIndex.coerceIn(0, queue.items.size)
        val to = toIndex.coerceIn(from, queue.items.size)
        if (from == to) return queue
        val moved = queue.items.subList(from, to)
        val retained = queue.items.toMutableList().apply {
            subList(from, to).clear()
        }
        retained.addAll(newIndex.coerceIn(0, retained.size), moved)
        return queue.withUpdatedItems(retained)
    }

    fun removeRange(
        queue: PlaybackQueue,
        fromIndex: Int,
        toIndex: Int,
    ): PlaybackQueue {
        val from = fromIndex.coerceIn(0, queue.items.size)
        val to = toIndex.coerceIn(from, queue.items.size)
        if (from == to) return queue
        val removedKeys = queue.items.subList(from, to).mapTo(mutableSetOf()) { it.mediaKey }
        val removedCurrent = queue.currentMediaKey in removedKeys
        val retained = queue.items.filterNot { it.mediaKey in removedKeys }
        val nextCurrent = if (removedCurrent) {
            retained.getOrNull(from)?.mediaKey ?: retained.lastOrNull()?.mediaKey
        } else {
            queue.currentMediaKey
        }
        return queue.withUpdatedItems(retained, nextCurrent)
    }

    fun replaceRange(
        queue: PlaybackQueue,
        fromIndex: Int,
        toIndex: Int,
        items: List<QueueMediaItem>,
    ): PlaybackQueue {
        val from = fromIndex.coerceIn(0, queue.items.size)
        val to = toIndex.coerceIn(from, queue.items.size)
        val replacement = distinctPlayable(items)
        val replacedKeys = queue.items.subList(from, to).mapTo(mutableSetOf()) { it.mediaKey }
        val replacedCurrent = queue.currentMediaKey in replacedKeys
        val updated = queue.items.toMutableList().apply {
            subList(from, to).clear()
            addAll(from, replacement)
        }
        val nextCurrent = if (replacedCurrent) {
            replacement.firstOrNull()?.mediaKey
                ?: updated.getOrNull(from)?.mediaKey
                ?: updated.lastOrNull()?.mediaKey
        } else {
            queue.currentMediaKey
        }
        val next = queue.withUpdatedItems(updated, nextCurrent)
        if (queue.mode != PlaybackMode.SHUFFLE || !replacedCurrent || replacement.isEmpty()) {
            return next
        }

        val replacementKeys = replacement.map { it.mediaKey }.filter { key ->
            next.items.any { it.mediaKey == key }
        }
        val retainedOrder = queue.shuffleOrder.filter { key ->
            next.items.any { it.mediaKey == key } && key !in replacementKeys
        }.toMutableList()
        val insertionIndex = queue.shuffleCursor.coerceIn(0, retainedOrder.size)
        retainedOrder.addAll(insertionIndex, replacementKeys)
        return next.copy(
            shuffleOrder = retainedOrder,
            shuffleCursor = retainedOrder.indexOf(nextCurrent),
        )
    }

    fun remove(
        queue: PlaybackQueue,
        mediaKey: String,
        random: Random,
    ): PlaybackQueue {
        val removedIndex = queue.items.indexOfFirst { it.mediaKey == mediaKey }
        if (removedIndex < 0) return queue

        val updated = queue.items.filterNot { it.mediaKey == mediaKey }
        if (updated.isEmpty()) {
            return queue.copy(
                items = emptyList(),
                currentMediaKey = null,
                shuffleOrder = emptyList(),
                shuffleCursor = -1,
            )
        }
        val nextCurrent = if (queue.currentMediaKey == mediaKey) {
            updated.getOrNull(removedIndex)?.mediaKey ?: updated.last().mediaKey
        } else {
            queue.currentMediaKey
        }
        return queue.withUpdatedItems(updated, nextCurrent, random)
    }

    fun previous(queue: PlaybackQueue): String? = when (queue.mode) {
        PlaybackMode.REPEAT_ALL -> queue.itemAt(queue.currentIndex - 1, wrap = true)
        PlaybackMode.SHUFFLE -> queue.shuffleItemAt(queue.resolvedShuffleCursor() - 1)
        PlaybackMode.SEQUENTIAL,
        PlaybackMode.REPEAT_ONE,
        -> queue.itemAt(queue.currentIndex - 1)
    }

    fun next(queue: PlaybackQueue, reason: QueueAdvanceReason): String? = when (queue.mode) {
        PlaybackMode.REPEAT_ALL -> queue.itemAt(queue.currentIndex + 1, wrap = true)
        PlaybackMode.REPEAT_ONE -> if (reason == QueueAdvanceReason.ENDED) {
            queue.currentMediaKey
        } else {
            queue.itemAt(queue.currentIndex + 1)
        }
        PlaybackMode.SHUFFLE -> queue.shuffleItemAt(queue.resolvedShuffleCursor() + 1)
        PlaybackMode.SEQUENTIAL -> queue.itemAt(queue.currentIndex + 1)
    }

    fun setMode(
        queue: PlaybackQueue,
        mode: PlaybackMode,
        random: Random,
    ): PlaybackQueue {
        val current = queue.currentMediaKey ?: queue.items.firstOrNull()?.mediaKey
        val normalized = queue.copy(currentMediaKey = current, mode = mode)
        return if (mode == PlaybackMode.SHUFFLE) {
            if (queue.mode == PlaybackMode.SHUFFLE) normalized.withUpdatedItems(normalized.items, current, random)
            else normalized.withNewShuffleOrder(random)
        } else {
            normalized.copy(shuffleOrder = emptyList(), shuffleCursor = -1)
        }
    }

    private fun distinctPlayable(items: List<QueueMediaItem>): List<QueueMediaItem> {
        val accepted = mutableListOf<QueueMediaItem>()
        for (item in items) {
            if (item.isPlayable() && accepted.none { it.mediaKey == item.mediaKey }) {
                accepted += item
            }
        }
        return accepted
    }

    private fun QueueMediaItem.isPlayable(): Boolean = when (kind) {
        com.local.mediaviewer.model.MediaKind.VIDEO,
        com.local.mediaviewer.model.MediaKind.AUDIO,
        com.local.mediaviewer.model.MediaKind.UNKNOWN,
        -> true
        else -> false
    }

    private fun PlaybackQueue.withUpdatedItems(
        updatedItems: List<QueueMediaItem>,
        requestedCurrent: String? = currentMediaKey,
        random: Random? = null,
    ): PlaybackQueue {
        val items = distinctPlayable(updatedItems)
        val current = requestedCurrent?.takeIf { key -> items.any { it.mediaKey == key } }
            ?: items.firstOrNull()?.mediaKey
        if (mode != PlaybackMode.SHUFFLE) {
            return copy(
                items = items,
                currentMediaKey = current,
                shuffleOrder = emptyList(),
                shuffleCursor = -1,
            )
        }

        val keys = items.map { it.mediaKey }
        val retained = shuffleOrder.filter { it in keys }
        val missing = keys.filterNot { it in retained }
        val insertionIndex = (shuffleCursor.coerceIn(-1, retained.lastIndex) + 1).coerceIn(0, retained.size)
        val order = retained.toMutableList().apply { addAll(insertionIndex, missing) }
        val normalizedOrder = if (order.isEmpty() && current != null) {
            newShuffleOrder(keys, current, random ?: Random.Default)
        } else {
            order
        }
        return copy(
            items = items,
            currentMediaKey = current,
            shuffleOrder = normalizedOrder,
            shuffleCursor = normalizedOrder.indexOf(current),
        )
    }

    private fun PlaybackQueue.withNewShuffleOrder(random: Random): PlaybackQueue {
        val current = currentMediaKey ?: return copy(shuffleOrder = emptyList(), shuffleCursor = -1)
        val order = newShuffleOrder(items.map { it.mediaKey }, current, random)
        return copy(shuffleOrder = order, shuffleCursor = order.indexOf(current))
    }

    private fun newShuffleOrder(keys: List<String>, current: String, random: Random): List<String> =
        listOf(current) + keys.filterNot { it == current }.shuffled(random)

    private fun PlaybackQueue.itemAt(index: Int, wrap: Boolean = false): String? {
        if (items.isEmpty() || currentIndex < 0) return null
        val resolvedIndex = if (wrap) index.mod(items.size) else index
        return items.getOrNull(resolvedIndex)?.mediaKey
    }

    private fun PlaybackQueue.resolvedShuffleCursor(): Int =
        shuffleOrder.indexOf(currentMediaKey).takeIf { it >= 0 } ?: shuffleCursor

    private fun PlaybackQueue.shuffleItemAt(index: Int): String? = shuffleOrder.getOrNull(index)
}
