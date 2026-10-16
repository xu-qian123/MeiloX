package com.ljyh.mei.ui.component.player.component


import android.os.SystemClock
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.ljyh.mei.ui.glass.SfIcon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.kyant.capsule.ContinuousRoundedRectangle
import com.ljyh.mei.R
import com.ljyh.mei.constants.AccompanimentLyricTextBoldKey
import com.ljyh.mei.constants.AccompanimentLyricTextSizeKey
import com.ljyh.mei.constants.LyricRomanizationEnabledKey
import com.ljyh.mei.constants.LyricTextSize
import com.ljyh.mei.constants.LyricTranslationEnabledKey
import com.ljyh.mei.constants.NormalLyricTextBoldKey
import com.ljyh.mei.constants.NormalLyricTextSizeKey
import com.ljyh.mei.playback.PlayerConnection
import com.ljyh.mei.ui.component.sheet.LocalPlayerSheet
import com.ljyh.mei.ui.component.utils.LifecycleStartedEffect
import com.ljyh.mei.ui.model.LyricData
import com.ljyh.mei.ui.model.LyricSource
import com.ljyh.mei.ui.model.LyricsSecondaryLineMode
import com.ljyh.mei.ui.model.hasDisplayablePhonetic
import com.ljyh.mei.ui.model.hasDisplayableTranslation
import com.ljyh.mei.ui.model.nextLyricsSecondaryLineMode
import com.ljyh.mei.ui.model.resolveLyricsSecondaryLineMode
import com.ljyh.mei.ui.model.withSecondaryLineMode
import com.ljyh.mei.utils.rememberEnumPreference
import com.ljyh.mei.utils.rememberPreference
import com.ljyh.mei.utils.setClipboard
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
@Composable
fun LyricScreen(
    lyricData: LyricData,
    modifier: Modifier = Modifier,
    playerConnection: PlayerConnection,
    lyricOffsetMs: Long = 0L,
    previewPositionMs: Long? = null,
    secondaryLineModeOverride: LyricsSecondaryLineMode? = null,
    onSecondaryLineModeOverrideChange: (LyricsSecondaryLineMode?) -> Unit = {},
    onClick: (LyricSource) -> Unit,
    controlsVisible: Boolean,
    onToggleControls: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val sheet = LocalPlayerSheet.current
    val expanded by remember(sheet) { derivedStateOf { sheet == null || sheet.state.isExpanded } }
    val (normalLyricTextSize, _) = rememberEnumPreference(
        NormalLyricTextSizeKey,
        LyricTextSize.Size28
    )
    val (normalLyricTextBold, _) = rememberPreference(NormalLyricTextBoldKey, true)
    val (accompanimentLyricTextSize, _) = rememberEnumPreference(
        AccompanimentLyricTextSizeKey,
        LyricTextSize.Size18
    )
    val (accompanimentLyricTextBold, _) = rememberPreference(AccompanimentLyricTextBoldKey, true)
    val manualScroll = remember(lyricData.lyricLine, playerConnection.player) {
        LyricManualScrollState()
    }

    // 副行（翻译 / 音译）三态循环：状态只作用于当前歌曲（换歌由容器重置），
    // 不再写全局的翻译/音译开关，避免"开了音译导致别的歌翻译消失"。
    val (translationEnabled) = rememberPreference(LyricTranslationEnabledKey, true)
    val (romanizationEnabled) = rememberPreference(LyricRomanizationEnabledKey, true)
    val hasTranslation = remember(lyricData.lyricLine) { lyricData.lyricLine.hasDisplayableTranslation() }
    val hasPhonetic = remember(lyricData.lyricLine) { lyricData.lyricLine.hasDisplayablePhonetic() }
    val secondaryLineMode = resolveLyricsSecondaryLineMode(
        requestedMode = secondaryLineModeOverride,
        translationEnabled = translationEnabled,
        romanizationEnabled = romanizationEnabled,
        hasTranslation = hasTranslation,
        hasPhonetic = hasPhonetic,
    )
    val displayLyricLine = remember(lyricData.lyricLine, secondaryLineMode) {
        lyricData.lyricLine.withSecondaryLineMode(secondaryLineMode)
    }

    // Read through a state holder so offset slider changes do not restart the
    // position loop (which would replay the fade-in animation).
    val currentLyricOffset by rememberUpdatedState(lyricOffsetMs)

    // 拖动进度条时用预览位置驱动歌词，松手后由外层在进度追上时清空。
    val currentPreview by rememberUpdatedState(previewPositionMs)

    LifecycleStartedEffect(controlsVisible, expanded) {
        if (controlsVisible && expanded) {
            delay(5000)
            onToggleControls(false)
        }
    }


    val nestedScrollConnection = remember(manualScroll, onToggleControls) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    val delta = available.y
                    if (delta != 0f) {
                        manualScroll.onUserScroll(SystemClock.elapsedRealtime())
                    }
                    if (delta < -10) {
                        onToggleControls(false)
                    } else if (delta > 10) {
                        onToggleControls(true)
                    }
                }
                return Offset.Zero
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onToggleControls(true) }
    ) {
        // Bounds of the bottom-right source badge, in this box's coordinates. The
        // lower-half tap-to-toggle gesture runs on the Initial pass and consumes taps
        // before children see them, so the badge region must be excluded explicitly.
        var badgeBounds by remember { mutableStateOf<Rect?>(null) }
        // 副行切换胶囊同样需要排除，否则点击会被下半屏"切换控制栏"手势吃掉。
        var secondaryPillBounds by remember { mutableStateOf<Rect?>(null) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(controlsVisible, onToggleControls) {
                    val badgeSlop = 12.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        val lowerHalf = down.position.y >= size.height / 2f
                        val inBadge = badgeBounds?.inflate(badgeSlop)?.contains(down.position) == true
                        val inSecondaryPill =
                            secondaryPillBounds?.inflate(badgeSlop)?.contains(down.position) == true
                        var moved = false
                        var released = false
                        var pressed = true
                        while (pressed && !released) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                moved = true
                            }
                            if (change.changedToUpIgnoreConsumed()) {
                                released = true
                                if (lowerHalf && !moved && !inBadge && !inSecondaryPill) {
                                    change.consume()
                                    onToggleControls(!controlsVisible)
                                }
                            }
                            pressed = event.changes.any { it.pressed }
                        }
                    }
                }
        ) {
            if (lyricData.lyricLine.lines.isNotEmpty()) {
                key(System.identityHashCode(lyricData.lyricLine)) {
                    val player = playerConnection.player
                    val lines = lyricData.lyricLine.lines
                    // Restore from playback after measuring, not from a saved scroll offset.
                    val listState = remember(player) { LazyListState() }
                    // Rows revealed by a within-screen jump need an existing placement
                    // to animate from. Keep a full window laid out beyond each edge.
                    val keepAliveZone = LocalConfiguration.current.screenHeightDp.dp
                    val keepAliveZonePx = with(LocalDensity.current) { keepAliveZone.roundToPx() }
                    val lyricOffsetPx = with(LocalDensity.current) { 48.dp.roundToPx() }
                    var animatedPosition by remember(player) { mutableLongStateOf(0L) }
                    var placementGeneration by remember(player) { mutableIntStateOf(0) }
                    val lyricAlpha = remember(player) { Animatable(0f) }
                    val focusLineVisible by remember(listState, lines, keepAliveZonePx) {
                        derivedStateOf {
                            val focusIndex = lyricFocusLineIndex(lines, animatedPosition.toInt())
                            val layout = listState.layoutInfo
                            layout.visibleItemsInfo.any { item ->
                                item.index == focusIndex && isLyricLineVisible(
                                    item.offset, item.size, layout.viewportStartOffset,
                                    layout.viewportEndOffset, keepAliveZonePx,
                                )
                            }
                        }
                    }
                    LaunchedEffect(listState, manualScroll) {
                        listState.interactionSource.interactions.collect { interaction ->
                            when (interaction) {
                                is DragInteraction.Start -> manualScroll.onDragStarted(SystemClock.elapsedRealtime())
                                is DragInteraction.Stop, is DragInteraction.Cancel ->
                                    manualScroll.onDragStopped(SystemClock.elapsedRealtime())
                            }
                        }
                    }
                    LifecycleStartedEffect(player, keepAliveZonePx, lyricOffsetPx, manualScroll, expanded) {
                        if (!expanded) return@LifecycleStartedEffect
                        try {
                            // Measure the actual viewport before deciding whether entry may animate.
                            snapshotFlow { listState.layoutInfo.visibleItemsInfo.isNotEmpty() }
                                .first { it }
                            var lastFocusIndex: Int? = null
                            var initialPositionPending = true
                            while (true) {
                                manualScroll.resumeIfIdle(SystemClock.elapsedRealtime(), listState.isScrollInProgress)
                                var position = ((currentPreview ?: player.currentPosition) + currentLyricOffset).coerceAtLeast(0L)
                                var focusIndex = lyricFocusLineIndex(lines, position.toInt())
                                var jumped = false
                                if (!manualScroll.isBrowsing && !listState.isScrollInProgress &&
                                    (focusIndex != lastFocusIndex || manualScroll.returnToPlaybackPending)
                                ) {
                                    val layout = listState.layoutInfo
                                    val viewportStart = layout.viewportStartOffset + keepAliveZonePx
                                    val viewportEnd = layout.viewportEndOffset - keepAliveZonePx
                                    val visibleLines = layout.visibleItemsInfo.filter { item ->
                                        item.index < lines.size && isLyricLineVisible(
                                            item.offset, item.size, layout.viewportStartOffset,
                                            layout.viewportEndOffset, keepAliveZonePx,
                                        )
                                    }
                                    // Clipped rows and the renderer's keep-alive area do not
                                    // increase the number of lyric lines that fit on screen.
                                    val fullLines = visibleLines.filter { item ->
                                        item.offset >= viewportStart && item.offset + item.size <= viewportEnd
                                    }
                                    val firstVisibleIndex = (fullLines.firstOrNull() ?: visibleLines.firstOrNull())?.index
                                    val snap = firstVisibleIndex == null || shouldSnapLyricScroll(
                                        lines, firstVisibleIndex, focusIndex, fullLines.size.coerceAtLeast(1),
                                    )
                                    val positioned = runInterruptibleLyricJump(
                                        restoreVisibility = { lyricAlpha.snapTo(1f) },
                                    ) {
                                        if (snap && lyricAlpha.value > 0f) {
                                            lyricAlpha.animateTo(0f, tween(120))
                                        }
                                        // Seeking may continue while the old lyrics fade out.
                                        position = ((currentPreview ?: player.currentPosition) + currentLyricOffset).coerceAtLeast(0L)
                                        focusIndex = lyricFocusLineIndex(lines, position.toInt())
                                        // A gesture may start during the fade. Leave it in control.
                                        if (!manualScroll.isBrowsing && !listState.isScrollInProgress) {
                                            if (snap) {
                                                // Reset cached per-line springs while transparent.
                                                listState.scrollToItem(focusIndex)
                                                animatedPosition = position
                                                placementGeneration++
                                                jumped = true
                                            } else {
                                                val currentLayout = listState.layoutInfo
                                                val target = currentLayout.visibleItemsInfo.firstOrNull {
                                                    it.index == focusIndex
                                                }
                                                if (target != null) {
                                                    val anchor = currentLayout.viewportStartOffset +
                                                        lyricOffsetPx + keepAliveZonePx
                                                    listState.scrollBy((target.offset - anchor).toFloat())
                                                } else {
                                                    listState.scrollToItem(focusIndex)
                                                }
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    if (positioned) {
                                        lastFocusIndex = focusIndex
                                        manualScroll.onPlaybackPositioned()
                                    }
                                }
                                animatedPosition = position
                                if (initialPositionPending || jumped) {
                                    initialPositionPending = false
                                    // Keep the positioning frame hidden, then fade in while time
                                    // updates continue so karaoke highlighting stays responsive.
                                    withFrameNanos { }
                                    launch { lyricAlpha.animateTo(1f, tween(180)) }
                                }
                                delay(50)
                            }
                        } finally {
                            // STOP or collapse can interrupt an entry or seek fade while
                            // the frame clock is paused. Restore the layer before resuming.
                            withContext(NonCancellable) { lyricAlpha.snapTo(1f) }
                        }
                    }
                    key(placementGeneration, keepAliveZonePx) {
                        PlayerLyricsView(
                            listState = listState,
                            isManualScrolling = manualScroll.isBrowsing && listState.isScrollInProgress,
                            useBlurEffect = !manualScroll.isBrowsing || focusLineVisible,
                            lyrics = displayLyricLine,
                            currentPosition = { animatedPosition.toInt() },
                            onLineClicked = { line ->
                                manualScroll.onLineSelected()
                                playerConnection.player.seekTo((line.start.toLong() - currentLyricOffset).coerceAtLeast(0L))
                                onToggleControls(true)
                            },
                            onLinePressed = { line ->
                                val result = when (line) {
                                    is KaraokeLine -> {
                                        "${line.syllables.joinToString("") { it.content }}\n${line.translation}"
                                    }
                                    is SyncedLine -> {
                                        "${line.content}\n${line.translation}"
                                    }
                                    else -> {
                                        Toast.makeText(context, "未知的歌词类型", Toast.LENGTH_SHORT).show()
                                        null
                                    }
                                }

                                result?.let {
                                    try {
                                        setClipboard(context, it, "lyric")
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .graphicsLayer {
                                    alpha = lyricAlpha.value
                                    blendMode = BlendMode.Plus
                                    compositingStrategy = CompositingStrategy.Offscreen
                                },
                            normalLineTextStyle = LocalTextStyle.current.copy(
                                fontSize = normalLyricTextSize.text.sp,
                                fontWeight = if (normalLyricTextBold) FontWeight.Bold else FontWeight.Normal,
                                textMotion = TextMotion.Animated,
                            ),
                            accompanimentLineTextStyle = LocalTextStyle.current.copy(
                                fontSize = accompanimentLyricTextSize.text.sp,
                                fontWeight = if (accompanimentLyricTextBold) FontWeight.Bold else FontWeight.Normal,
                                textMotion = TextMotion.Animated,
                            ),
                            offset = 48.dp,
                            keepAliveZone = keepAliveZone,
                        )
                    }
                }

                LyricSourceBadge(
                    source = lyricData.source,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding( bottom = 8.dp)
                        .onGloballyPositioned { badgeBounds = it.boundsInParent() },
                    onClick = onClick,
                )

                if (hasTranslation || hasPhonetic) {
                    LyricSecondaryLinePill(
                        mode = secondaryLineMode,
                        hasTranslation = hasTranslation,
                        hasPhonetic = hasPhonetic,
                        onSelect = onSecondaryLineModeOverrideChange,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = 8.dp)
                            .onGloballyPositioned { secondaryPillBounds = it.boundsInParent() },
                    )
                }
            }
        }
    }
}

/** One icon cycles only through the secondary lines available for this song. */
@Composable
private fun LyricSecondaryLinePill(
    mode: LyricsSecondaryLineMode,
    hasTranslation: Boolean,
    hasPhonetic: Boolean,
    onSelect: (LyricsSecondaryLineMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = mode != LyricsSecondaryLineMode.NONE
    val description = stringResource(R.string.lyrics_secondary_control)
    val modeDescription = stringResource(when (mode) {
        LyricsSecondaryLineMode.TRANSLATION -> R.string.lyrics_mode_translation
        LyricsSecondaryLineMode.PHONETIC -> R.string.lyrics_mode_phonetic
        LyricsSecondaryLineMode.NONE -> R.string.lyrics_secondary_off
    })
    val shape = ContinuousRoundedRectangle(24.dp)
    val tint = if (active) Color.Black.copy(alpha = .85f) else Color.White.copy(alpha = .65f)
    Box(
        modifier.size(48.dp).clip(shape)
            .background(if (active) Color.White else Color.White.copy(alpha = .10f))
            .border(.5.dp, Color.White.copy(alpha = .14f), shape)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                stateDescription = modeDescription
            }
            .clickable(role = Role.Button) {
                onSelect(nextLyricsSecondaryLineMode(mode, hasTranslation, hasPhonetic))
            },
        contentAlignment = Alignment.Center,
    ) {
        if (mode == LyricsSecondaryLineMode.PHONETIC) {
            Text("ā", color = tint, fontSize = 27.sp, fontWeight = FontWeight.Medium)
        } else {
            SfIcon("translate", contentDescription = null, size = 23.dp, tint = tint)
        }
    }
}

internal suspend fun runInterruptibleLyricJump(
    restoreVisibility: suspend () -> Unit,
    jump: suspend () -> Boolean,
): Boolean = coroutineScope {
    var completed = false
    // A scroll mutation cancels its calling Job when a gesture wins. Isolate
    // that cancellation so playback updates and subsequent fades keep running.
    launch {
        try {
            completed = jump()
        } finally {
            if (!completed) {
                withContext(NonCancellable) { restoreVisibility() }
            }
        }
    }.join()
    completed
}

internal fun lyricFocusLineIndex(lines: List<ISyncedLine>, positionMs: Int): Int {
    // Match lyrics-ui's focus rules, including overlapping vocals and interludes.
    val activeIndex = lines.indexOfFirst { line ->
        val effectiveEnd = if (line is KaraokeLine.MainKaraokeLine) {
            maxOf(line.end, line.accompanimentLines?.maxOfOrNull { it.end } ?: line.end)
        } else {
            line.end
        }
        positionMs >= line.start && positionMs < effectiveEnd
    }
    if (activeIndex >= 0) return activeIndex
    val nextIndex = lines.indexOfFirst { it.start > positionMs }
    return if (nextIndex >= 0) nextIndex else lines.lastIndex.coerceAtLeast(0)
}

internal fun shouldSnapLyricScroll(
    lines: List<ISyncedLine>,
    fromIndex: Int,
    toIndex: Int,
    visibleLineCount: Int,
): Boolean {
    val distance = (minOf(fromIndex, toIndex) until maxOf(fromIndex, toIndex)).count {
        lines[it] !is KaraokeLine.AccompanimentKaraokeLine
    }
    // A screen containing N rows from index I ends at I + N - 1.
    return visibleLineCount > 0 && distance >= visibleLineCount
}

internal fun isLyricLineVisible(
    itemOffset: Int,
    itemSize: Int,
    viewportStart: Int,
    viewportEnd: Int,
    keepAliveZonePx: Int,
): Boolean = itemSize > 0 &&
    itemOffset + itemSize > viewportStart + keepAliveZonePx &&
    itemOffset < viewportEnd - keepAliveZonePx

@Composable
private fun LyricSourceBadge(
    source: LyricSource,
    modifier: Modifier = Modifier,
    onClick: (LyricSource) -> Unit,
) {
    val sourceName = stringResource(when (source) {
        LyricSource.NetEaseCloudMusic -> R.string.lyrics_match_source_netease
        LyricSource.QQMusic -> R.string.lyrics_match_source_qq
        LyricSource.AM -> R.string.lyrics_match_source_amll
        LyricSource.Kugou -> R.string.lyrics_match_source_kugou
        LyricSource.Custom -> R.string.lyrics_source_custom
        LyricSource.Loading -> R.string.lyrics_source_loading
        LyricSource.Empty -> R.string.lyrics_source_unknown
    })
    val description = stringResource(R.string.lyrics_source_control, sourceName)
    val shape = ContinuousRoundedRectangle(24.dp)
    Box(
        modifier.size(48.dp).clip(shape)
            .background(Color.White.copy(alpha = .12f))
            .border(.5.dp, Color.White.copy(alpha = .14f), shape)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button) { onClick(source) },
        contentAlignment = Alignment.Center,
    ) {
        when (source) {
            LyricSource.NetEaseCloudMusic, LyricSource.QQMusic, LyricSource.AM -> Icon(
                painter = painterResource(when (source) {
                    LyricSource.NetEaseCloudMusic -> R.drawable.netease
                    LyricSource.QQMusic -> R.drawable.qq
                    else -> R.drawable.am
                }),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.White.copy(alpha = .9f),
            )
            LyricSource.Kugou -> Text("K", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            else -> SfIcon(
                when (source) {
                    LyricSource.Custom -> "square.and.pencil"
                    LyricSource.Loading -> "ellipsis"
                    else -> "quote.bubble"
                },
                contentDescription = null, size = 22.dp, tint = Color.White.copy(alpha = .9f),
            )
        }
    }
}
