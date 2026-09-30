package com.ljyh.mei.ui.component.player.component.sheet

import android.view.WindowManager
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Material3 1.4's ModalBottomSheet applies IME padding outside the entire sheet,
 * changing its constraints and drag anchors on every keyboard animation frame.
 * Input sheets instead keep their glass shell stationary and handle IME insets
 * inside the surface. Resolve the window here, inside the dialog's composition.
 */
@Composable
internal fun LyricInputDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (dragHandleModifier: Modifier) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        var dragOffset by remember { mutableFloatStateOf(0f) }
        val dismissDistance = with(LocalDensity.current) { 96.dp.toPx() }
        val dismissVelocity = with(LocalDensity.current) { 1200.dp.toPx() }
        val dragHandleModifier = Modifier.draggable(
            state = rememberDraggableState { delta ->
                dragOffset = (dragOffset + delta).coerceAtLeast(0f)
            },
            orientation = Orientation.Vertical,
            onDragStopped = { velocity ->
                if (dragOffset >= dismissDistance || velocity >= dismissVelocity) {
                    onDismissRequest()
                } else {
                    animate(dragOffset, 0f) { value, _ -> dragOffset = value }
                }
            },
        )
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            val previousMode = window?.attributes?.softInputMode
            // The Compose scrim below owns dimming, including taps outside the sheet.
            window?.setDimAmount(0f)
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            onDispose {
                if (previousMode != null) window.setSoftInputMode(previousMode)
            }
        }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(interactionSource = null, indication = null, onClick = onDismissRequest),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .graphicsLayer { translationY = dragOffset }
                    // Make the sheet a hit target so empty areas don't hit the scrim.
                    // Don't consume events: text selection and scrolling handle them.
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent()
                        }
                    }
                    .then(modifier),
            ) {
                content(dragHandleModifier)
            }
        }
    }
}
