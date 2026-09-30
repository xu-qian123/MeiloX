package com.ljyh.mei.ui.component.player.component.sheet

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
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
@Composable
fun LyricMatchSheet(
    viewModel: PlayerViewModel,
    metadata: MediaMetadata,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalGlassColors.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    val saveScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    val dismissSheet: () -> Unit = {
        if (!isSaving) {
            viewModel.clearLyricMatches()
            onDismiss()
        }
    }
    val applyMatch: (RankedLyricMatch) -> Unit = apply@{ match ->
        if (isSaving) return@apply
        val sanitized = sanitizeMatchedLyrics(
            lyrics = match.candidate.lyrics,
            translatedLyrics = match.candidate.translatedLyrics,
            title = metadata.title,
            artist = metadata.artists.joinToString(" ") { it.name },
            album = metadata.album.title,
        )
        isSaving = true
        saveScope.launch {
            try {
                viewModel.saveCustomLyric(
                    metadata = metadata,
                    lyric = sanitized.lyrics.takeIf { it.isNotBlank() },
                    translatedLyric = sanitized.translatedLyrics,
                    matchedSource = match.candidate.source.name,
                    matchedSongId = match.candidate.id,
                )
                Toast.makeText(context, R.string.lyrics_match_applied, Toast.LENGTH_SHORT).show()
                viewModel.clearLyricMatches()
                onDismiss()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Toast.makeText(context, R.string.lyrics_editor_save_failed, Toast.LENGTH_LONG).show()
            } finally {
                isSaving = false
            }
        }
    }
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }

    LyricInputDialog(
        onDismissRequest = dismissSheet,
        modifier = Modifier.graphicsLayer {
            clip = false
            applyGlassDragScale(
                pressProgress = interactiveHighlight.pressProgress,
                offset = interactiveHighlight.offset,
            )
        },
    ) { dragHandleModifier ->
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
                    Modifier.fillMaxWidth().height(24.dp).then(dragHandleModifier),
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
