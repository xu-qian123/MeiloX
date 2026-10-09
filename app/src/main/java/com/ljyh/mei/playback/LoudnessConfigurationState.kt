package com.ljyh.mei.playback

import android.content.Context
import com.ljyh.mei.constants.AutoMixEnabledKey
import com.ljyh.mei.constants.LoudnessNormalizationEnabledKey
import com.ljyh.mei.constants.LoudnessNormalizationTargetKey
import com.ljyh.mei.utils.dataStore
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LoudnessConfigurationState(context: Context, scope: CoroutineScope) {
    private val current = AtomicReference(LoudnessConfiguration.Disabled)
    private val observation = scope.launch {
        context.dataStore.data.collectLatest { preferences ->
            current.set(LoudnessConfiguration(
                enabled = preferences[LoudnessNormalizationEnabledKey] ?: false,
                targetLufs = (preferences[LoudnessNormalizationTargetKey] ?: -16f)
                    .takeIf { it.isFinite() }?.coerceIn(-20f, -10f) ?: -16f,
                reserveMixHeadroom = preferences[AutoMixEnabledKey] ?: false,
            ))
        }
    }
    fun snapshot(): LoudnessConfiguration = current.get()
    fun release() = observation.cancel()
}
