package cash.p.terminal.ui.compose.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.coin_placeholder
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource

private const val LOGO_SCALE = 28f / 32f

/** Logo at 28/32 on a neutral circle, so dark and light logos stay visible in both themes. Keep it last in the chain. */
@Composable
fun Modifier.coinIconPlate(enabled: Boolean = true): Modifier = if (!enabled) this else this
    .clip(CircleShape)
    .background(ComposeAppTheme.colors.surfacePlaceholder)
    .graphicsLayer {
        scaleX = LOGO_SCALE
        scaleY = LOGO_SCALE
        shape = CircleShape
        clip = true
    }

@Composable
private fun CoinIconPlateSamples() {
    Row(
        modifier = Modifier
            .background(ComposeAppTheme.colors.backgroundBase)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(24, 32, 56).forEach { size ->
            Image(
                painter = painterResource(Res.drawable.coin_placeholder),
                contentDescription = null,
                modifier = Modifier.size(size.dp).coinIconPlate()
            )
        }
    }
}

@Preview
@Composable
private fun CoinIconPlateDarkPreview() {
    ComposeAppTheme(darkTheme = true) { CoinIconPlateSamples() }
}

@Preview
@Composable
private fun CoinIconPlateLightPreview() {
    ComposeAppTheme(darkTheme = false) { CoinIconPlateSamples() }
}
