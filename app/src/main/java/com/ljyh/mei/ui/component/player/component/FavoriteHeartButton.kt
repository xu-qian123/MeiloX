package com.ljyh.mei.ui.component.player.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ljyh.mei.R
import com.ljyh.mei.ui.glass.SfIcon
import com.ljyh.mei.ui.glass.SfSymbol

/** iOS 系统红，已收藏的心形使用。 */
private val FavoriteHeartLikedColor = Color(0xFFFF3B30)

/**
 * 收藏按钮：空心/实心心形，已收藏为红色，切换时带弹性放大回弹。
 */
@Composable
fun FavoriteHeartButton(
    isLiked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 34.dp,
    iconSize: Dp = 22.dp,
    unlikedTint: Color = Color.White.copy(alpha = 0.8f),
    likedTint: Color = FavoriteHeartLikedColor,
) {
    val pop = remember { Animatable(1f) }
    var isFirstComposition by remember { mutableStateOf(true) }
    LaunchedEffect(isLiked) {
        if (isFirstComposition) {
            isFirstComposition = false
            return@LaunchedEffect
        }
        pop.snapTo(0.72f)
        pop.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }

    IconButton(
        onClick = onClick,
        modifier = modifier.size(buttonSize),
    ) {
        AnimatedContent(
            targetState = isLiked,
            modifier = Modifier.graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
            transitionSpec = {
                (scaleIn(initialScale = 0.7f) + fadeIn()) togetherWith
                    (scaleOut(targetScale = 0.7f) + fadeOut())
            },
            label = "FavoriteHeart",
        ) { liked ->
            SfIcon(
                symbol = if (liked) SfSymbol.HeartFilled else SfSymbol.Heart,
                contentDescription = stringResource(R.string.app_tab_library_songs),
                tint = if (liked) likedTint else unlikedTint,
                size = iconSize,
                weight = FontWeight.Bold,
            )
        }
    }
}
