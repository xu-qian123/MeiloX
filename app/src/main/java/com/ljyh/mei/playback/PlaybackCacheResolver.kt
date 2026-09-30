package com.ljyh.mei.playback

import java.io.IOException

/** Online playback keeps the selected quality; offline playback can reuse another complete file. */
internal suspend fun <T : Any> resolvePlaybackWithCache(
    networkAvailable: Boolean,
    preferredCache: () -> T?,
    fallbackCache: () -> T?,
    remote: suspend () -> T,
): T {
    preferredCache()?.let { return it }
    if (!networkAvailable) fallbackCache()?.let { return it }
    return try {
        remote()
    } catch (error: IOException) {
        // Also handles stale connectivity state, unreachable servers and exhausted qualities.
        fallbackCache() ?: throw error
    }
}
