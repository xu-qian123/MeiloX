package com.ljyh.mei.ui.component.player.component

import android.animation.ValueAnimator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ljyh.mei.constants.MusicQuality
import com.ljyh.mei.constants.MusicQualityKey
import com.ljyh.mei.ui.local.LocalPlayerConnection
import com.ljyh.mei.utils.TimeUtils.makeTimeString
import com.ljyh.mei.utils.rememberEnumPreference
import kotlin.math.PI
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Apple Music 风格播放进度条（交互引擎移植自 NeriPlayer 的 AppleMusicSlider）。
 *
 * 体感：纯轨道无圆点、按下/拖动弹簧膨胀、点击跳转、帧级进度推演、
 * 缓冲呼吸脉冲、拖动预览回调；颜色沿用 MeiloX 播放器的白色描白体系，
 * 与 NeriPlayer 的主题取色无关。
 */
enum class AppleMusicSliderTrackStyle {
    /** 正常样式：胶囊实心轨道。 */
    Solid,

    /** 动态波浪：保留 MeiloX 的正弦波轨道，但共用同一套交互引擎。 */
    Wave,
}

private val SliderTouchHeight = 48.dp
private val SliderRestTrackInset = 6.dp
internal val AppleMusicSliderBaseTrackHeight = 5.dp

internal const val APPLE_SLIDER_REST_TRACK_SCALE = 1f
internal const val APPLE_SLIDER_ACTIVE_TRACK_SCALE = 2f
internal const val APPLE_SLIDER_REST_TRACK_INSET_FRACTION = 1f
internal const val APPLE_SLIDER_ACTIVE_TRACK_INSET_FRACTION = 0f
internal const val APPLE_SLIDER_INACTIVE_TRACK_ALPHA = 0.3f

private const val SliderTrackSpringDamping = 0.72f
private const val SliderTrackSpringStiffness = 320f
private const val SliderWaitingPulseDurationMs = 900
private const val SliderWavePeriodMs = 2000

internal fun resolveAppleSliderTrackScale(isActive: Boolean): Float =
    if (isActive) APPLE_SLIDER_ACTIVE_TRACK_SCALE else APPLE_SLIDER_REST_TRACK_SCALE

internal fun resolveAppleSliderTrackInsetFraction(isActive: Boolean): Float =
    if (isActive) APPLE_SLIDER_ACTIVE_TRACK_INSET_FRACTION else APPLE_SLIDER_REST_TRACK_INSET_FRACTION

internal fun normalizeSliderProgress(value: Float): Float =
    value.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f

internal fun resolveSliderValueForTouchPosition(touchX: Float, widthPx: Float): Float {
    if (!touchX.isFinite() || !widthPx.isFinite() || widthPx <= 0f) return 0f
    return (touchX / widthPx).coerceIn(0f, 1f)
}

internal fun shouldPredictSliderProgress(
    isPlaybackAnimating: Boolean,
    isProgressStalled: Boolean,
    isProgressPreviewing: Boolean,
): Boolean = isPlaybackAnimating && !isProgressStalled && !isProgressPreviewing

private const val NANOS_PER_MILLISECOND = 1_000_000L

internal fun resolveSliderProgress(
    anchorValue: Float,
    durationMs: Long,
    playbackSpeed: Float,
    elapsedNs: Long,
): Float {
    val anchor = normalizeSliderProgress(anchorValue)
    if (durationMs <= 0L) return anchor
    val speed = normalizeSliderPlaybackSpeed(playbackSpeed)
    if (speed <= 0f) return anchor
    val elapsedMs = elapsedNs.coerceAtLeast(0L).toDouble() / NANOS_PER_MILLISECOND
    val progressAdvance = elapsedMs / durationMs.toDouble() * speed
    return (anchor + progressAdvance.toFloat()).coerceIn(0f, 1f)
}

private fun normalizeSliderPlaybackSpeed(value: Float): Float =
    value.takeIf { it.isFinite() && it > 0f } ?: 0f

/**
 * 在播放器两次真实进度回调之间按帧推演进度，保证轨道平滑前进；
 * 暂停/拖动/seek 时立即对齐锚点。
 */
internal class SliderProgressPredictor(initialValue: Float) {
    private var anchorValue = normalizeSliderProgress(initialValue)
    private var targetValue = anchorValue
    private var durationMs = 0L
    private var playbackSpeed = 0f
    private var anchorFrameNs = 0L
    private var hasPendingAnchor = true
    private var wasAnimating = false

    var currentValue = anchorValue
        private set

    fun updateTarget(
        targetValue: Float,
        durationMs: Long,
        playbackSpeed: Float,
        animate: Boolean,
    ) {
        val normalizedTarget = normalizeSliderProgress(targetValue)
        val normalizedDurationMs = durationMs.coerceAtLeast(0L)
        val normalizedPlaybackSpeed = normalizeSliderPlaybackSpeed(playbackSpeed)
        val inputChanged = normalizedTarget != this.targetValue ||
            normalizedDurationMs != this.durationMs ||
            normalizedPlaybackSpeed != this.playbackSpeed
        if (inputChanged || !animate || !wasAnimating) {
            anchorValue = normalizedTarget
            currentValue = normalizedTarget
            hasPendingAnchor = true
        }
        this.targetValue = normalizedTarget
        this.durationMs = normalizedDurationMs
        this.playbackSpeed = normalizedPlaybackSpeed
        wasAnimating = animate
    }

    fun onFrame(frameNs: Long) {
        if (frameNs <= 0L) return
        if (hasPendingAnchor || anchorFrameNs == 0L || frameNs < anchorFrameNs) {
            anchorFrameNs = frameNs
            currentValue = anchorValue
            hasPendingAnchor = false
            return
        }
        currentValue = resolveSliderProgress(
            anchorValue = anchorValue,
            durationMs = durationMs,
            playbackSpeed = playbackSpeed,
            elapsedNs = frameNs - anchorFrameNs,
        )
    }

    fun resetFrameAnchor() {
        anchorValue = currentValue
        anchorFrameNs = 0L
        hasPendingAnchor = true
    }
}

@Composable
fun AppleMusicProgressSlider(
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    onPositionChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    trackStyle: AppleMusicSliderTrackStyle = AppleMusicSliderTrackStyle.Solid,
    enabled: Boolean = true,
    isPlaybackWaiting: Boolean = false,
    playbackSpeed: Float = 1f,
    playbackSessionKey: String? = null,
    previewPositionMs: Long? = null,
    onSeekPreviewStart: ((Long) -> Unit)? = null,
    onSeekPreviewMove: ((Long) -> Unit)? = null,
    onSeekPreviewEnd: ((Long?) -> Unit)? = null,
) {
    val musicQuality by rememberEnumPreference(MusicQualityKey, MusicQuality.EXHIGH)
    val playerConnection = LocalPlayerConnection.current
    val hapticFeedback = LocalHapticFeedback.current

    val isDurationValid = duration > 0
    val sliderEnabled = enabled && isDurationValid
    val durationF = duration.coerceAtLeast(1L).toFloat()
    val latestDuration by rememberUpdatedState(duration)
    val latestOnPositionChange by rememberUpdatedState(onPositionChange)
    val latestOnSeekPreviewStart by rememberUpdatedState(onSeekPreviewStart)
    val latestOnSeekPreviewMove by rememberUpdatedState(onSeekPreviewMove)
    val latestOnSeekPreviewEnd by rememberUpdatedState(onSeekPreviewEnd)

    val externalFraction = normalizeSliderProgress(position / durationF)
    val previewFraction = previewPositionMs?.let { normalizeSliderProgress(it / durationF) }

    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var isPressed by remember { mutableStateOf(false) }
    val isDragging = dragFraction != null
    val isActive = isPressed || isDragging
    val baseFraction = previewFraction ?: externalFraction

    LaunchedEffect(sliderEnabled, isDragging) {
        if (!sliderEnabled && isDragging) dragFraction = null
    }
    LaunchedEffect(sliderEnabled, isPressed) {
        if (!sliderEnabled && isPressed) isPressed = false
    }

    // 短缓冲不闪等待脉冲，超过 1 秒再显示（与 NeriPlayer 的视觉延迟一致）。
    val delayedPlaybackWaiting = rememberDelayedPlaybackWaiting(isPlaybackWaiting)

    val animationsEnabled = ValueAnimator.areAnimatorsEnabled()
    val trackScale by animateFloatAsState(
        targetValue = resolveAppleSliderTrackScale(isActive),
        animationSpec = spring(
            dampingRatio = SliderTrackSpringDamping,
            stiffness = SliderTrackSpringStiffness,
        ),
        label = "apple_slider_track_scale",
    )
    val trackInsetFraction by animateFloatAsState(
        targetValue = resolveAppleSliderTrackInsetFraction(isActive),
        animationSpec = spring(
            dampingRatio = SliderTrackSpringDamping,
            stiffness = SliderTrackSpringStiffness,
        ),
        label = "apple_slider_track_inset",
    )

    val waitingTransition = rememberInfiniteTransition(label = "apple_slider_waiting")
    val waitingPulse by waitingTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = SliderWaitingPulseDurationMs,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "apple_slider_waiting_pulse",
    )
    val pulseAlpha = if (animationsEnabled && delayedPlaybackWaiting && !isDragging) waitingPulse else 1f

    val waveTransition = rememberInfiniteTransition(label = "apple_slider_wave")
    val wavePhase by waveTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying) (2 * PI).toFloat() else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(SliderWavePeriodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "apple_slider_wave_phase",
    )
    val waveAmplitude by animateFloatAsState(
        targetValue = when {
            isActive -> 0f
            isPlaying -> 6f
            else -> 3f
        },
        animationSpec = tween(300),
        label = "apple_slider_wave_amplitude",
    )

    val isProgressAnimating = animationsEnabled && sliderEnabled && isPlaying &&
        !delayedPlaybackWaiting && !isDragging
    val isProgressPredicting = shouldPredictSliderProgress(
        isPlaybackAnimating = isProgressAnimating,
        isProgressStalled = isPlaybackWaiting,
        isProgressPreviewing = previewFraction != null,
    )
    val predictedFraction = remember { mutableFloatStateOf(baseFraction) }
    val sliderProgress = remember(playbackSessionKey) { SliderProgressPredictor(baseFraction) }
    LaunchedEffect(
        playbackSessionKey,
        baseFraction,
        duration,
        playbackSpeed,
        isProgressPredicting,
    ) {
        sliderProgress.updateTarget(
            targetValue = baseFraction,
            durationMs = duration,
            playbackSpeed = playbackSpeed,
            animate = isProgressPredicting,
        )
        // 兜底：每次真实进度更新时同步一次绘制值，即使帧推演暂停也不会显示旧值。
        predictedFraction.floatValue = sliderProgress.currentValue
    }
    // 逐帧推演进度：直接挂 Choreographer 渲染帧，并用固定 key 的 DisposableEffect，
    // 避免依赖组合帧时钟或受 effect 键变化影响（此前帧循环因 effect 反复重启从未执行）。
    val latestPredicting by rememberUpdatedState(isProgressPredicting)
    val latestSliderProgress by rememberUpdatedState(sliderProgress)
    DisposableEffect(Unit) {
        val choreographer = android.view.Choreographer.getInstance()
        var wasPredicting = false
        val callback = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (latestPredicting) {
                    val predictor = latestSliderProgress
                    if (!wasPredicting) {
                        predictor.resetFrameAnchor()
                        wasPredicting = true
                    }
                    predictor.onFrame(frameTimeNanos)
                    predictedFraction.floatValue = predictor.currentValue
                } else {
                    wasPredicting = false
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
        onDispose {
            choreographer.removeFrameCallback(callback)
        }
    }

    fun fractionAt(touchX: Float, widthPx: Float): Float =
        resolveSliderValueForTouchPosition(touchX, widthPx)

    fun msOf(fraction: Float): Long = (fraction * latestDuration).roundToLong()

    // 按压监听只观察事件不消费，按下即给膨胀反馈。
    val pressModifier = if (sliderEnabled) {
        Modifier.pointerInput(sliderEnabled) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                isPressed = true
                try {
                    waitForUpOrCancellation()
                } finally {
                    isPressed = false
                }
            }
        }
    } else {
        Modifier
    }

    // 回调通过 rememberUpdatedState 读取：手势 key 保持稳定，避免拖动中被重组取消。
    val dragModifier = if (sliderEnabled) {
        Modifier.pointerInput(sliderEnabled) {
            detectDragGestures(
                onDragStart = { offset ->
                    val width = size.width.toFloat()
                    val fraction = fractionAt(offset.x, width)
                    dragFraction = fraction
                    val positionMs = msOf(fraction)
                    latestOnSeekPreviewStart?.invoke(positionMs)
                    latestOnSeekPreviewMove?.invoke(positionMs)
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                },
                onDragEnd = {
                    val fraction = dragFraction
                    dragFraction = null
                    if (fraction != null) {
                        val positionMs = msOf(fraction)
                        latestOnPositionChange(positionMs)
                        latestOnSeekPreviewEnd?.invoke(positionMs)
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                    }
                },
                onDragCancel = {
                    dragFraction = null
                    latestOnSeekPreviewEnd?.invoke(null)
                },
                onDrag = { change, _ ->
                    val width = size.width.toFloat()
                    val fraction = fractionAt(change.position.x, width)
                    dragFraction = fraction
                    latestOnSeekPreviewMove?.invoke(msOf(fraction))
                },
            )
        }
    } else {
        Modifier
    }

    val tapModifier = if (sliderEnabled) {
        Modifier.pointerInput(sliderEnabled) {
            detectTapGestures { offset ->
                val width = size.width.toFloat()
                val fraction = fractionAt(offset.x, width)
                val positionMs = msOf(fraction)
                latestOnSeekPreviewStart?.invoke(positionMs)
                latestOnSeekPreviewMove?.invoke(positionMs)
                latestOnPositionChange(positionMs)
                latestOnSeekPreviewEnd?.invoke(positionMs)
                hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
            }
        }
    } else {
        Modifier
    }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(SliderTouchHeight)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(baseFraction, 0f..1f)
                    if (sliderEnabled) {
                        setProgress { targetValue ->
                            val coerced = normalizeSliderProgress(targetValue)
                            val positionMs = (coerced * latestDuration).roundToLong()
                            latestOnSeekPreviewStart?.invoke(positionMs)
                            latestOnSeekPreviewMove?.invoke(positionMs)
                            latestOnPositionChange(positionMs)
                            latestOnSeekPreviewEnd?.invoke(positionMs)
                            true
                        }
                    } else {
                        disabled()
                    }
                }
                .then(pressModifier)
                .then(tapModifier)
                .then(dragModifier),
        ) {
            val barFraction = dragFraction
                ?: if (isProgressPredicting) predictedFraction.floatValue else baseFraction
            if (trackStyle == AppleMusicSliderTrackStyle.Solid) {
                drawSolidTrack(
                    progressFraction = barFraction,
                    trackScale = trackScale,
                    trackInsetFraction = trackInsetFraction,
                    pulseAlpha = pulseAlpha,
                )
            } else {
                drawWaveTrack(
                    progressFraction = barFraction,
                    trackScale = trackScale,
                    pulseAlpha = pulseAlpha,
                    amplitude = waveAmplitude,
                    phase = if (animationsEnabled) wavePhase else 0f,
                )
            }
        }

        val displayedPositionMs = ((dragFraction ?: baseFraction) * durationF).roundToLong()
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        ) {
            val timeTextStyle = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.2f),
                    offset = Offset(0f, 1f),
                    blurRadius = 2f,
                ),
            )

            Text(
                text = makeTimeString(displayedPositionMs),
                style = timeTextStyle,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            PlayerQualityDropdown(
                quality = musicQuality,
                onQualitySelected = { playerConnection?.changeQuality(it) },
                style = timeTextStyle.copy(fontSize = 10.sp),
                color = Color.White.copy(alpha = 0.6f),
            )

            Text(
                text = if (duration > 0) makeTimeString(duration) else "-:--",
                style = timeTextStyle,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun DrawScope.drawSolidTrack(
    progressFraction: Float,
    trackScale: Float,
    trackInsetFraction: Float,
    pulseAlpha: Float,
) {
    val centerY = size.height / 2f
    val trackHeightPx = (AppleMusicSliderBaseTrackHeight.toPx() * trackScale).coerceAtLeast(1f)
    val trackInsetPx = SliderRestTrackInset.toPx() * trackInsetFraction
    val trackLeft = trackInsetPx
    val trackRight = (size.width - trackInsetPx).coerceAtLeast(trackLeft + 1f)
    val trackWidthPx = trackRight - trackLeft
    val progressPx = trackLeft + trackWidthPx * normalizeSliderProgress(progressFraction)
    val topLeft = Offset(trackLeft, centerY - trackHeightPx / 2f)
    val trackSize = Size(trackWidthPx, trackHeightPx)
    val cornerRadiusPx = trackHeightPx / 2f
    val corner = CornerRadius(cornerRadiusPx, cornerRadiusPx)

    drawRoundRect(
        color = Color.White.copy(alpha = APPLE_SLIDER_INACTIVE_TRACK_ALPHA * pulseAlpha),
        topLeft = topLeft,
        size = trackSize,
        cornerRadius = corner,
    )
    if (progressPx > trackLeft) {
        // 平切右端：外轮廓保持胶囊，进展段只按宽度裁剪，圆头不会跟着走。
        clipRect(right = progressPx) {
            drawRoundRect(
                color = Color.White.copy(alpha = pulseAlpha),
                topLeft = topLeft,
                size = trackSize,
                cornerRadius = corner,
            )
        }
    }
}

private fun DrawScope.drawWaveTrack(
    progressFraction: Float,
    trackScale: Float,
    pulseAlpha: Float,
    amplitude: Float,
    phase: Float,
) {
    val width = size.width
    val height = size.height
    val centerY = height / 2f
    val inactiveAlpha = APPLE_SLIDER_INACTIVE_TRACK_ALPHA * pulseAlpha
    val activeAlpha = pulseAlpha
    val activeWidth = width * normalizeSliderProgress(progressFraction)

    drawLine(
        color = Color.White.copy(alpha = inactiveAlpha),
        start = Offset(0f, centerY),
        end = Offset(width, centerY),
        strokeWidth = 2.dp.toPx() * trackScale,
        cap = StrokeCap.Round,
    )

    if (activeWidth <= 0f) return

    val path = Path()
    path.moveTo(0f, centerY)
    val step = 5f
    val frequency = 0.05f
    var x = 0f
    while (x < activeWidth) {
        path.lineTo(x, centerY + amplitude * sin(x * frequency - phase))
        x += step
    }
    val finalY = centerY + amplitude * sin(activeWidth * frequency - phase)
    path.lineTo(activeWidth, finalY)

    drawPath(
        path = path,
        color = Color.White.copy(alpha = activeAlpha),
        style = Stroke(
            width = 3.dp.toPx() * trackScale,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
    drawCircle(
        color = Color.White.copy(alpha = activeAlpha),
        radius = 6.dp.toPx() * trackScale,
        center = Offset(activeWidth, finalY),
    )
}
