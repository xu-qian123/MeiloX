package com.ljyh.mei.playback

internal data class PlaybackCacheSpan(val position: Long, val length: Long, val fileLength: Long?)

/** Adapted from NeriPlayer's inspectCachedResourceSpans (GPL-3.0). */
internal fun hasCompletePlaybackSpans(contentLength: Long, spans: List<PlaybackCacheSpan>): Boolean {
    if (contentLength <= 0L || spans.isEmpty()) return false
    var end = 0L
    for (span in spans.sortedBy { it.position }) {
        if (span.position != end || span.length <= 0L || span.fileLength != span.length) return false
        // Subtraction avoids overflow when validating corrupt index entries.
        if (span.length > contentLength - end) return false
        end += span.length
    }
    return end == contentLength
}
