package com.ljyh.mei.ui.component.player.component.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.ljyh.mei.R
import com.ljyh.mei.data.model.MediaMetadata
import com.ljyh.mei.ui.component.player.PlayerViewModel
import com.ljyh.mei.ui.glass.LocalGlassColors
import com.ljyh.mei.ui.glass.LocalGlassDimensions
import com.ljyh.mei.ui.glass.SfIcon
import com.ljyh.mei.ui.glass.IosTypography
import com.ljyh.mei.ui.glass.segmentedControlBackground
import com.ljyh.mei.ui.model.LyricMatchUiState
import com.ljyh.mei.utils.lyric.match.LyricMatchConfidence
import com.ljyh.mei.utils.lyric.match.LyricMatchSource
import com.ljyh.mei.utils.lyric.match.RankedLyricMatch
import java.util.Locale

/**
 * Shared multi-source search content, used by the lyrics editor's match page
 * and the standalone lyrics-search sheet. AMLL is the highest weighted source.
 */
@Composable
internal fun ColumnScope.LyricMatchContent(
    viewModel: PlayerViewModel,
    metadata: MediaMetadata,
    onApply: (RankedLyricMatch) -> Unit,
) {
    val colors = LocalGlassColors.current
    var keyword by remember(metadata.id) {
        mutableStateOf("${metadata.title} ${metadata.artists.firstOrNull()?.name.orEmpty()}".trim())
    }
    var selectedSources by remember(metadata.id) {
        mutableStateOf(LyricMatchSource.entries.toSet())
    }
    val matchState by viewModel.lyricMatchState.collectAsState()

    LaunchedEffect(metadata.id) {
        viewModel.searchLyricMatches(metadata, keyword, selectedSources)
    }

    LyricKeywordField(
        value = keyword,
        onValueChange = { keyword = it },
        onSearch = { viewModel.searchLyricMatches(metadata, keyword, selectedSources) },
        placeholder = stringResource(R.string.lyrics_match_hint),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LyricMatchSource.entries.forEach { source ->
            val selected = source in selectedSources
            Text(
                text = lyricMatchSourceLabel(source),
                color = if (selected) androidx.compose.ui.graphics.Color.White else colors.content,
                style = IosTypography.subheadline,
                modifier = Modifier
                    .background(
                        if (selected) colors.prominentContainer.copy(alpha = 1f)
                        else colors.segmentedControlBackground,
                        Capsule(),
                    )
                    .clickable(interactionSource = null, indication = null) {
                        selectedSources = if (selected) {
                            if (selectedSources.size > 1) selectedSources - source else selectedSources
                        } else {
                            selectedSources + source
                        }
                        viewModel.searchLyricMatches(metadata, keyword, selectedSources)
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }

    val state = matchState
    when (state) {
        LyricMatchUiState.Idle -> LyricMatchHint(stringResource(R.string.lyrics_match_hint))
        LyricMatchUiState.Loading -> Box(
            Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        is LyricMatchUiState.Error -> LyricMatchHint(stringResource(R.string.lyrics_match_failed))

        is LyricMatchUiState.Success -> {
            val results = state.results
            if (results.isEmpty()) {
                LyricMatchHint(stringResource(R.string.lyrics_match_empty))
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(
                        items = results,
                        key = { match -> "${match.candidate.source}-${match.candidate.id}" },
                    ) { match ->
                        LyricMatchResultRow(
                            match = match,
                            expectedDurationMs = metadata.duration,
                            onClick = { onApply(match) },
                        )
                    }
                }
            }
        }
    }
}

/** Capsule search field matching [com.ljyh.mei.ui.glass.GlassSearchBar]'s resting look. */
@Composable
internal fun LyricKeywordField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGlassColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(LocalGlassDimensions.current.controlHeight)
            .background(colors.segmentedControlBackground, Capsule())
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SfIcon("magnifyingglass", null, size = 19.dp, tint = colors.content)
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = IosTypography.body.copy(color = colors.content),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = IosTypography.body, color = colors.tertiaryContent)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun ColumnScope.LyricMatchHint(text: String) {
    Box(
        Modifier.fillMaxWidth().weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = LocalGlassColors.current.secondaryContent)
    }
}

@Composable
private fun LyricMatchResultRow(
    match: RankedLyricMatch,
    expectedDurationMs: Long,
    onClick: () -> Unit,
) {
    val colors = LocalGlassColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = match.candidate.title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = lyricMatchSourceLabel(match.candidate.source),
                style = IosTypography.caption,
                color = colors.secondaryContent,
            )
        }
        Text(
            text = listOfNotNull(
                match.candidate.artist.takeIf { it.isNotBlank() },
                match.candidate.album?.takeIf { it.isNotBlank() },
            ).joinToString(" · "),
            style = IosTypography.caption,
            color = colors.secondaryContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LyricMatchTag(
                text = lyricMatchConfidenceLabel(match.confidence),
                tint = if (match.confidence == LyricMatchConfidence.HIGH) {
                    LyricMatchHighConfidenceColor
                } else {
                    null
                },
            )
            if (match.hasWordTiming) {
                LyricMatchTag(stringResource(R.string.lyrics_match_word_timed))
            }
            if (match.candidate.durationMs > 0L && expectedDurationMs > 0L) {
                val signedDeltaMs = match.candidate.durationMs - expectedDurationMs
                // "+3.2s" = candidate is longer than the song, "-2.5s" = shorter.
                LyricMatchTag(String.format(Locale.ROOT, "%+.1fs", signedDeltaMs / 1000.0))
            }
        }
    }
}

private val LyricMatchHighConfidenceColor = Color(0xFF34C759)

@Composable
private fun LyricMatchTag(
    text: String,
    tint: Color? = null,
) {
    val colors = LocalGlassColors.current
    Text(
        text = text,
        style = IosTypography.caption,
        color = tint ?: colors.secondaryContent,
        modifier = Modifier
            .background(
                tint?.copy(alpha = 0.16f) ?: colors.segmentedControlBackground,
                Capsule(),
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
internal fun lyricMatchSourceLabel(source: LyricMatchSource): String = stringResource(
    when (source) {
        LyricMatchSource.AMLL_TTML -> R.string.lyrics_match_source_amll
        LyricMatchSource.KUGOU -> R.string.lyrics_match_source_kugou
        LyricMatchSource.CLOUD_MUSIC -> R.string.lyrics_match_source_netease
        LyricMatchSource.QQ_MUSIC -> R.string.lyrics_match_source_qq
    },
)

@Composable
private fun lyricMatchConfidenceLabel(confidence: LyricMatchConfidence): String = stringResource(
    when (confidence) {
        LyricMatchConfidence.HIGH -> R.string.lyrics_match_confidence_high
        LyricMatchConfidence.MEDIUM -> R.string.lyrics_match_confidence_medium
        LyricMatchConfidence.LOW -> R.string.lyrics_match_confidence_low
    },
)
