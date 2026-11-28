package cash.p.terminal.modules.nft.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

private const val SHIMMER_DURATION_MS = 1200

/** A highlight sweeping left to right over the placeholder color. */
@Composable
internal fun Modifier.nftShimmer(): Modifier {
    val base = ComposeAppTheme.colors.surfacePlaceholder
    val highlight = ComposeAppTheme.colors.surfaceElevated
    val progress by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SHIMMER_DURATION_MS, easing = LinearEasing), RepeatMode.Restart),
    )
    return drawBehind {
        val bandEnd = size.width * 2 * progress
        drawRect(
            Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(bandEnd - size.width, 0f),
                end = Offset(bandEnd, 0f),
            )
        )
    }
}

/** Mirrors the layout of [NftCard], so the content replaces it without a jump. */
@Composable
internal fun NftCardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.surfacePrimary)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).nftShimmer())
        Column(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SkeletonLine(widthFraction = 0.7f, barHeight = 14.dp, lineHeight = 18.dp)
            SkeletonLine(widthFraction = 0.5f, barHeight = 12.dp, lineHeight = 16.dp)
        }
    }
}

/** The bar sits in a box as tall as the text line it stands for. */
@Composable
private fun SkeletonLine(widthFraction: Float, barHeight: Dp, lineHeight: Dp) {
    Box(Modifier.fillMaxWidth().height(lineHeight), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .fillMaxWidth(widthFraction)
                .height(barHeight)
                .clip(RoundedCornerShape(4.dp))
                .nftShimmer()
        )
    }
}

@Preview
@Composable
private fun NftCardSkeletonPreview() {
    ComposeAppTheme {
        NftCardSkeleton(Modifier.padding(16.dp).fillMaxWidth(0.5f))
    }
}
