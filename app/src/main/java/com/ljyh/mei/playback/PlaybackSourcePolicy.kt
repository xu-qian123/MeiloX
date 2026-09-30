package com.ljyh.mei.playback

import androidx.media3.common.PlaybackException
import com.ljyh.mei.constants.MusicQuality
import java.util.Locale

private const val PLAYBACK_CACHE_KEY_VERSION = "meilox-media-v3"

/** Normalizes quality values from preferences, API responses, and enum names. */
internal fun normalizePlaybackQuality(quality: String): String =
    quality.trim().lowercase(Locale.ROOT).ifBlank { MusicQuality.EXHIGH.text }

/** Uses the server-reported level when available, otherwise the attempted level. */
internal fun effectivePlaybackQuality(sourceQuality: String?, attemptedQuality: String): String =
    sourceQuality
        ?.takeIf(String::isNotBlank)
        ?.let(::normalizePlaybackQuality)
        ?: normalizePlaybackQuality(attemptedQuality)

// Adapted from NeriPlayer's PlayerUrlResolver (GPL-3.0).
internal val playbackQualityOrder = listOf(
    "jymaster", "sky", "jyeffect", "hires", "lossless", "exhigh", "higher", "standard",
)

/** Returns the requested quality followed by progressively lower supported qualities. */
internal fun playbackQualityFallbacks(requestedQuality: String): List<String> {
    val quality = normalizePlaybackQuality(requestedQuality)
    val index = playbackQualityOrder.indexOf(quality)
    return if (index >= 0) playbackQualityOrder.drop(index)
    else listOf(quality, "exhigh", "higher", "standard").distinct()
}

/** Prefer the selected quality, then lower ones; other cached qualities are a last resort. */
internal fun selectCachedPlaybackKey(
    keys: Set<String>,
    mediaId: String,
    requestedQuality: String,
    allowOtherQualities: Boolean,
    isComplete: (String) -> Boolean,
): String? {
    val preferred = normalizePlaybackQuality(requestedQuality)
    val qualities = if (allowOtherQualities) {
        (playbackQualityFallbacks(preferred) + playbackQualityOrder).distinct()
    } else listOf(preferred)
    for (quality in qualities) {
        val prefix = playbackCacheKeyPrefix(mediaId, quality)
        keys.asSequence().filter { it.startsWith(prefix) }.sorted()
            .firstOrNull(isComplete)?.let { return it }
    }
    return null
}

/** Login/permission/unavailable-quality responses can still have a playable lower level. */
internal fun shouldTryLowerPlaybackQuality(responseCode: Int): Boolean =
    responseCode in setOf(200, 301, 401, 403, 404)

/** Identifies the exact media bytes returned for a logical quality. */
internal fun playbackSourceIdentity(sourceMd5: String?, sourceSize: Long?): String =
    sourceMd5
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotEmpty)
        ?: "size-${sourceSize?.takeIf { it > 0L } ?: 0L}"

/** Prefix shared by every source revision cached for one song and quality. */
internal fun playbackCacheKeyPrefix(mediaId: String, quality: String): String =
    "$PLAYBACK_CACHE_KEY_VERSION:${mediaId.trim()}:${normalizePlaybackQuality(quality)}:"

/** Prefix shared by every playback cache entry for one song. */
internal fun playbackCacheKeyPrefix(mediaId: String): String =
    "$PLAYBACK_CACHE_KEY_VERSION:${mediaId.trim()}:"

/**
 * Builds a disk cache key for the exact server-returned source.
 *
 * Quality alone is insufficient because NetEase may replace the underlying file while keeping
 * the same logical level. Mixing an old partial cache span with that new file can make a later
 * range request start beyond the new resource boundary.
 */
internal fun playbackCacheKey(
    mediaId: String,
    quality: String,
    sourceMd5: String?,
    sourceSize: Long?,
): String = playbackCacheKeyPrefix(mediaId, quality) +
    playbackSourceIdentity(sourceMd5, sourceSize)

/** Returns whether the current source should be invalidated and retried in place. */
internal fun shouldRefreshPlaybackSource(errorCode: Int): Boolean =
    errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
