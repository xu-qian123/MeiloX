package com.ljyh.mei.playback

import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * Reports no platform decoder for FLAC so the renderer list falls through to the bundled
 * FFmpeg extension renderer.
 *
 * Some devices (e.g. certain MIUI builds) silently shrink the MediaCodec audio input buffer to
 * 16-32 KiB regardless of the requested size. Hi-res FLAC frames can exceed that, which makes
 * ExoPlayer skip every oversized sample and buffer forever. Software decoding with FFmpeg has
 * no such limit.
 *
 * Non-FLAC formats keep the default hardware decoder selection.
 */
@OptIn(UnstableApi::class)
internal class FlacPreferFfmpegMediaCodecSelector(
    private val delegate: MediaCodecSelector,
    private val preferFfmpegForFlac: Boolean,
) : MediaCodecSelector {

    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean,
    ) = if (preferFfmpegForFlac && mimeType.equals(MimeTypes.AUDIO_FLAC, ignoreCase = true)) {
        emptyList()
    } else {
        delegate.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
    }
}
