package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_selection_check_24
import org.jetbrains.compose.resources.painterResource

internal const val DisabledControlAlpha = 0.4f

private val IndicatorSize = 24.dp
private val IndicatorBorderWidth = 1.5.dp

@Composable
internal fun SelectionIndicator(
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(IndicatorSize)
            .let { if (enabled) it else it.alpha(DisabledControlAlpha) }
            .clip(CircleShape)
            .let {
                if (selected) {
                    it.background(ComposeAppTheme.colors.brandDefault)
                } else {
                    it.border(IndicatorBorderWidth, ComposeAppTheme.colors.iconSecondary, CircleShape)
                }
            },
    ) {
        if (selected) {
            Icon(
                painter = painterResource(Res.drawable.ic_selection_check_24),
                contentDescription = null,
                tint = ComposeAppTheme.colors.buttonPrimaryBrandContent,
            )
        }
    }
}
