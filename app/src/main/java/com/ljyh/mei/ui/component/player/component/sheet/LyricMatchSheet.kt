package com.ljyh.mei.ui.component.player.component.sheet

import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import coil3.compose.AsyncImage
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule
import com.ljyh.mei.R
import com.ljyh.mei.data.model.MediaMetadata
import com.ljyh.mei.ui.component.player.PlayerViewModel
import com.ljyh.mei.ui.glass.IosModalSheetShape
import com.ljyh.mei.ui.glass.IosSheetSurface
import com.ljyh.mei.ui.glass.IosSheetTopToolbar
import com.ljyh.mei.ui.glass.IosSheetTopToolbarButton
import com.ljyh.mei.ui.glass.IosTypography
import com.ljyh.mei.ui.glass.LocalGlassColors
import com.ljyh.mei.ui.glass.SfIcon
import com.ljyh.mei.ui.glass.applyGlassDragScale
import com.ljyh.mei.ui.liquidglass.InteractiveHighlight
import com.ljyh.mei.utils.TimeUtils.formatDuration
import com.ljyh.mei.utils.lyric.edit.sanitizeMatchedLyrics
import com.ljyh.mei.utils.lyric.match.RankedLyricMatch

/**
 * Standalone multi-source lyric search sheet, opened from the lyrics page
 * source badge. Presentation follows the old QQ sheet (reference header +
 * plain list) with the matcher's source/confidence/word-timing tags; tapping a
 * candidate applies it immediately as the custom lyric.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricMatchSheet(
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

    val dismissSheet: () -> Unit = {
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
        viewModel.saveCustomLyric(
            metadata = metadata,
            lyric = sanitized.lyrics.takeIf { it.isNotBlank() },
            translatedLyric = sanitized.translatedLyrics,
            matchedSource = match.candidate.source.name,
            matchedSongId = match.candidate.id,
        )
        Toast.makeText(
            context,
            context.getString(R.string.lyrics_match_applied),
            Toast.LENGTH_SHORT,
        ).show()
        dismissSheet()
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
                IosSheetTopToolbar(
                    title = stringResource(R.string.lyrics_match_title),
                    actions = {
                        IosSheetTopToolbarButton(onClick = dismissSheet) {
                            SfIcon("xmark", stringResource(R.string.cancel), size = 20.dp)
                        }
                    },
                )
                LyricReferenceHeader(metadata)
                LyricMatchContent(
                    viewModel = viewModel,
                    metadata = metadata,
                    onApply = applyMatch,
                )
            }
        }
    }
}

@Composable
private fun LyricReferenceHeader(metadata: MediaMetadata) {
    val colors = LocalGlassColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = metadata.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(ContinuousRoundedRectangle(9.dp)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = metadata.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${metadata.artists.joinToString(", ") { it.name }} · ${formatDuration(metadata.duration)}",
                style = IosTypography.caption,
                color = colors.secondaryContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
