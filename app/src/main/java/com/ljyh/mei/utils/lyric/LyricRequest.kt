package com.ljyh.mei.utils.lyric

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The lyric deadline owns its request; optional selection search runs alongside it. */
internal suspend fun runLyricRequest(
    timeoutMillis: Long,
    isCurrent: () -> Boolean,
    onTimeout: () -> Unit,
    selectionSearch: (suspend () -> Unit)? = null,
    request: suspend () -> Unit,
): Unit = coroutineScope {
    currentCoroutineContext().ensureActive()
    if (!isCurrent()) return@coroutineScope
    // Selection results must not wait for a slow lyric source or share its deadline.
    if (selectionSearch != null) launch { selectionSearch() }
    val completed = withTimeoutOrNull(timeoutMillis) {
        request()
        true
    }
    currentCoroutineContext().ensureActive()
    if (completed == null && isCurrent()) onTimeout()
}

/** Configuration failure may use a default; cancellation must stop the lyric load. */
internal suspend fun <T> readLyricSetting(default: T, read: suspend () -> T): T = try {
    currentCoroutineContext().ensureActive()
    val value = read()
    currentCoroutineContext().ensureActive()
    value
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    currentCoroutineContext().ensureActive()
    default
}
