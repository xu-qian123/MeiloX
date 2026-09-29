package com.ljyh.mei.ui.component.player.component.sheet

import android.content.ClipboardManager
import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import com.kyant.shapes.Capsule
import com.ljyh.mei.R
import com.ljyh.mei.data.model.MediaMetadata
import com.ljyh.mei.ui.component.player.PlayerViewModel
import com.ljyh.mei.ui.glass.GlassButton
import com.ljyh.mei.ui.glass.GlassSegmentedControl
import com.ljyh.mei.ui.glass.GlassSlider
import com.ljyh.mei.ui.glass.IosModalSheetShape
import com.ljyh.mei.ui.glass.IosSheetSurface
import com.ljyh.mei.ui.glass.IosSheetTopToolbar
import com.ljyh.mei.ui.glass.IosSheetTopToolbarButton
import com.ljyh.mei.ui.glass.IosTopToolbar
import com.ljyh.mei.ui.glass.LocalGlassColors
import com.ljyh.mei.ui.glass.SfIcon
import com.ljyh.mei.ui.glass.applyGlassDragScale
import com.ljyh.mei.ui.liquidglass.InteractiveHighlight
import com.ljyh.mei.ui.model.LyricSource
import com.ljyh.mei.utils.lyric.edit.MAX_LYRIC_OFFSET_MS
import com.ljyh.mei.utils.lyric.edit.MIN_LYRIC_OFFSET_MS
import com.ljyh.mei.utils.lyric.edit.sanitizeMatchedLyrics
import com.ljyh.mei.utils.lyric.edit.toEditableLyricsText
import com.ljyh.mei.utils.lyric.match.RankedLyricMatch
import java.util.Locale
import kotlin.math.roundToLong

private enum class LyricsEditorPage { Edit, Match }

private data class LyricsEditorSeed(
    val lyrics: String,
    val translation: String,
    val wasCustom: Boolean,
)

/**
 * Custom lyrics editor, ported from NeriPlayer's `LyricsEditorSheet`.
 *
 * Two pages: raw text editing (original / translation + per-song offset, plus
 * a manual "restore" action) and a multi-source search page (AMLL first).
 * The sheet shell mirrors [PlaylistBottomSheet]; the surface height is pinned
 * to the screen so IME insets only shift the inner content instead of resizing
 * the glass surface on every keyboard frame.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsEditorSheet(
    viewModel: PlayerViewModel,
    metadata: MediaMetadata,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalGlassColors.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    // Keep the dialog window static while the IME animates: only the inner content is
    // re-padded, so the glass surface never resizes or re-records its backdrop.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        val previousMode = dialogWindow?.attributes?.softInputMode
        if (dialogWindow != null) {
            dialogWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        onDispose {
            if (dialogWindow != null && previousMode != null) {
                dialogWindow.setSoftInputMode(previousMode)
            }
        }
    }
    // Keep the lyric flow collected so the editor follows manager updates while open.
    val lyricData by viewModel.lyric.collectAsState()
    val offsetMs by viewModel.lyricOffset.collectAsState()

    val seed = remember(metadata.id) {
        val data = lyricData
        LyricsEditorSeed(
            lyrics = toEditableLyricsText(data),
            translation = data.rawTranslation.orEmpty(),
            wasCustom = data.source == LyricSource.Custom,
        )
    }
    var page by remember(metadata.id) { mutableStateOf(LyricsEditorPage.Edit) }
    var selectedTab by remember(metadata.id) { mutableIntStateOf(0) }
    var lyricText by remember(metadata.id) { mutableStateOf(seed.lyrics) }
    var translationText by remember(metadata.id) { mutableStateOf(seed.translation) }
    var matchedSource by remember(metadata.id) { mutableStateOf<String?>(null) }
    var matchedSongId by remember(metadata.id) { mutableStateOf<String?>(null) }

    val dismissSheet: () -> Unit = {
        viewModel.clearLyricMatches()
        onDismiss()
    }
    val saveAndDismiss: () -> Unit = {
        val trimmedLyrics = lyricText.trim()
        val trimmedTranslation = translationText.trim()
        val unchanged = trimmedLyrics == seed.lyrics.trim() &&
            trimmedTranslation == seed.translation.trim()
        when {
            trimmedLyrics.isEmpty() && trimmedTranslation.isEmpty() ->
                viewModel.clearCustomLyric(metadata)

            unchanged && seed.wasCustom ->
                viewModel.clearCustomLyric(metadata)

            unchanged -> Unit

            else -> viewModel.saveCustomLyric(
                metadata = metadata,
                lyric = trimmedLyrics,
                translatedLyric = trimmedTranslation,
                matchedSource = matchedSource,
                matchedSongId = matchedSongId,
            )
        }
        viewModel.clearLyricMatches()
        onDismiss()
    }
    val applyMatch: (RankedLyricMatch) -> Unit = { match ->
        val sanitized = sanitizeMatchedLyrics(
            lyrics = match.candidate.lyrics,
            translatedLyrics = match.candidate.translatedLyrics,
            title = metadata.title,
            artist = metadata.artists.joinToString(" ") { it.name },
            album = metadata.album.title,
        )
        lyricText = sanitized.lyrics
        translationText = sanitized.translatedLyrics.orEmpty()
        matchedSource = match.candidate.source.name
        matchedSongId = match.candidate.id
        selectedTab = 0
        page = LyricsEditorPage.Edit
        viewModel.clearLyricMatches()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }

    ModalBottomSheet(
        onDismissRequest = dismissSheet,
        sheetState = sheetState,
        modifier = Modifier.graphicsLayer {
            clip = false
            applyGlassDragScale(
                pressProgress = interactiveHighlight.pressProgress,
                offset = interactiveHighlight.offset,
            )
        },
        containerColor = Color.Transparent,
        contentColor = colors.content,
        shape = RectangleShape,
        dragHandle = null,
        contentWindowInsets = { WindowInsets.statusBars },
    ) {
        IosSheetSurface(
            modifier = Modifier.fillMaxWidth().height(screenHeight * 0.82f),
            shape = IosModalSheetShape,
            interactiveHighlight = interactiveHighlight,
            applyDragScale = false,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
            ) {
                Box(
                    Modifier.fillMaxWidth().height(16.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        Modifier
                            .padding(top = 5.dp)
                            .size(width = 58.dp, height = 4.dp)
                            .background(colors.tertiaryContent.copy(alpha = 0.55f), Capsule()),
                    )
                }
                when (page) {
                    LyricsEditorPage.Edit -> {
                        IosSheetTopToolbar(
                            title = stringResource(R.string.lyrics_editor_title),
                            actions = {
                                IosSheetTopToolbarButton(
                                    onClick = {
                                        page = LyricsEditorPage.Match
                                        viewModel.clearLyricMatches()
                                    },
                                ) {
                                    SfIcon("magnifyingglass", stringResource(R.string.lyrics_match_button), size = 20.dp)
                                }
                                IosSheetTopToolbarButton(onClick = dismissSheet) {
                                    SfIcon("xmark", stringResource(R.string.cancel), size = 20.dp)
                                }
                                IosSheetTopToolbarButton(onClick = saveAndDismiss) {
                                    SfIcon("checkmark", stringResource(R.string.lyrics_editor_save), size = 20.dp)
                                }
                            },
                        )
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            GlassSegmentedControl(
                                items = listOf(
                                    0 to stringResource(R.string.lyrics_editor_tab_original),
                                    1 to stringResource(R.string.lyrics_editor_tab_translation),
                                ),
                                selected = selectedTab,
                                onSelected = { selectedTab = it },
                                sampleBackdrop = false,
                            )
                        }
                        EditorTextArea(
                            value = if (selectedTab == 0) lyricText else translationText,
                            onValueChange = { newValue ->
                                if (selectedTab == 0) lyricText = newValue else translationText = newValue
                            },
                            placeholder = stringResource(R.string.lyrics_editor_placeholder),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(16.dp),
                        )
                        OffsetControl(
                            offsetMs = offsetMs,
                            onOffsetChange = { viewModel.setLyricOffset(metadata, it) },
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (seed.wasCustom) {
                                GlassButton(
                                    onClick = {
                                        viewModel.clearCustomLyric(metadata)
                                        dismissSheet()
                                    },
                                ) {
                                    SfIcon("arrow.counterclockwise", null, size = 18.dp, tint = colors.content)
                                    Spacer(Modifier.size(6.dp))
                                    Text(stringResource(R.string.lyrics_editor_restore))
                                }
                            }
                            GlassButton(
                                onClick = {
                                    if (selectedTab == 0) lyricText = "" else translationText = ""
                                },
                            ) {
                                SfIcon("trash", null, size = 18.dp, tint = colors.content)
                                Spacer(Modifier.size(6.dp))
                                Text(stringResource(R.string.lyrics_editor_clear))
                            }
                            GlassButton(
                                onClick = {
                                    val pasted = readClipboardText(context)
                                    if (pasted.isNotBlank()) {
                                        if (selectedTab == 0) lyricText = pasted else translationText = pasted
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.lyrics_editor_paste))
                            }
                        }
                    }

                    LyricsEditorPage.Match -> {
                        IosTopToolbar(
                            title = stringResource(R.string.lyrics_match_title),
                            navigation = {
                                IosSheetTopToolbarButton(
                                    onClick = {
                                        page = LyricsEditorPage.Edit
                                        viewModel.clearLyricMatches()
                                    },
                                ) {
                                    SfIcon("chevron.left", stringResource(R.string.cancel), size = 20.dp)
                                }
                            },
                        )
                        LyricMatchContent(
                            viewModel = viewModel,
                            metadata = metadata,
                            onApply = applyMatch,
                        )
                    }
                }
            }
        }
    }
}

/** iOS-green for "lyrics are early", red for "lyrics are late". */
@Composable
private fun OffsetControl(
    offsetMs: Long,
    onOffsetChange: (Long) -> Unit,
) {
    val colors = LocalGlassColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.lyrics_editor_offset))
            Spacer(Modifier.weight(1f))
            Text(
                text = formatLyricOffset(offsetMs),
                color = when {
                    offsetMs > 0L -> Color(0xFF34C759)
                    offsetMs < 0L -> colors.destructive
                    else -> colors.secondaryContent
                },
            )
        }
        GlassSlider(
            value = offsetMs.toFloat(),
            onValueChange = { onOffsetChange(it.roundToLong()) },
            valueRange = MIN_LYRIC_OFFSET_MS.toFloat()..MAX_LYRIC_OFFSET_MS.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun formatLyricOffset(offsetMs: Long): String {
    val sign = if (offsetMs > 0L) "+" else ""
    return String.format(Locale.ROOT, "%s%.2fs", sign, offsetMs / 1000.0)
}

@Composable
private fun EditorTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGlassColors.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.verticalScroll(rememberScrollState()),
        textStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = colors.content,
        ),
        cursorBrush = SolidColor(colors.accent),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = colors.tertiaryContent,
                    ),
                )
            }
            Box(Modifier.fillMaxSize()) { inner() }
        },
    )
}

private fun readClipboardText(context: Context): String = runCatching {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
}.getOrDefault("")
