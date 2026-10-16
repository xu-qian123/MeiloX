package com.ljyh.mei.utils.lyric

import com.ljyh.mei.data.network.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Main-thread ownership of search results, independent of the chosen lyric source. */
internal class LyricSearchController<T>(
    private val scope: CoroutineScope,
    private val publishResult: (Resource<T>) -> Unit,
) {
    private var generation = 0L
    private var searchJob: Job? = null

    fun current(): Long = generation

    fun cancel() {
        generation++
        searchJob?.cancel()
        searchJob = null
    }

    fun publish(token: Long, result: Resource<T>, isSessionCurrent: () -> Boolean) {
        if (token == generation && isSessionCurrent()) publishResult(result)
    }

    fun search(isSessionCurrent: () -> Boolean, request: suspend () -> Resource<T>) {
        cancel()
        val token = current()
        publish(token, Resource.Loading, isSessionCurrent)
        searchJob = scope.launch {
            if (!isSessionCurrent()) return@launch
            val result = request()
            currentCoroutineContext().ensureActive()
            publish(token, result, isSessionCurrent)
        }
    }
}
