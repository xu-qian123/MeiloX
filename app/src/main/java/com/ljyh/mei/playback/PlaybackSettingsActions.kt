package com.ljyh.mei.playback

import androidx.datastore.preferences.core.Preferences
import com.ljyh.mei.constants.LoopPlaybackKey
import com.ljyh.mei.constants.PreviousPlaybackKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal data class PlaybackControlSettings(
    val previousPlayback: Boolean,
    val loopPlayback: Boolean,
)

/** Waits for real settings without blocking the caller; preserves button press order. */
internal class PlaybackSettingsActions(
    scope: CoroutineScope,
    private val preferences: Flow<Preferences>,
    private val onError: (Exception) -> Unit,
) {
    private val actions = Channel<(PlaybackControlSettings) -> Unit>(Channel.UNLIMITED)
    private val job = scope.launch {
        try {
            for (action in actions) {
                try {
                    val values = preferences.first()
                    currentCoroutineContext().ensureActive()
                    action(
                        PlaybackControlSettings(
                            previousPlayback = values[PreviousPlaybackKey] ?: true,
                            // Preserve the playback control's existing absent-key behavior.
                            loopPlayback = values[LoopPlaybackKey] == true,
                        )
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    onError(error)
                }
            }
        } finally {
            actions.cancel()
        }
    }

    init {
        // Also close when the owner was already cancelled before the worker started.
        job.invokeOnCompletion { actions.cancel() }
    }

    fun submit(action: (PlaybackControlSettings) -> Unit) {
        actions.trySend(action)
    }

    fun close() {
        actions.cancel()
        job.cancel()
    }
}
