package com.ljyh.mei.playback

import android.os.Handler
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import java.lang.reflect.Modifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegExtensionCompatibilityTest {
    @Test fun bundledRendererStillMatchesMedia3ReflectionContract() {
        // Check Java linkage without loading native code or constructing an Android player.
        val renderer = Class.forName(
            "androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer",
            false,
            Renderer::class.java.classLoader,
        )
        assertTrue(Renderer::class.java.isAssignableFrom(renderer))
        assertNotNull(renderer.getConstructor(
            Handler::class.java, AudioRendererEventListener::class.java, AudioSink::class.java,
        ))
        assertFalse(renderer.methods.any { Modifier.isAbstract(it.modifiers) })
    }
}
