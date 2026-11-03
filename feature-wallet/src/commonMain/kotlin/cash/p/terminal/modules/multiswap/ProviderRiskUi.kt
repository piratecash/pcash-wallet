package cash.p.terminal.modules.multiswap

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.utils.formatDurationShort
import cash.p.terminal.modules.multiswap.providers.ProviderRiskType
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.clock_filled_24
import org.jetbrains.compose.resources.DrawableResource

@Composable
internal fun ProviderRiskType.color(): Color = when (this) {
    ProviderRiskType.Auto -> ComposeAppTheme.colors.statusSuccess
    ProviderRiskType.Flexible -> ComposeAppTheme.colors.brandDefault
    ProviderRiskType.Controlled -> ComposeAppTheme.colors.statusWarning
    ProviderRiskType.PreCheck -> ComposeAppTheme.colors.textPrimary
}

@Composable
internal fun BadgePill(
    iconRes: DrawableResource,
    text: String,
    contentColor: Color,
    modifier: Modifier = Modifier,
    backgroundColor: Color = contentColor.copy(alpha = 0.1f),
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = ComposeAppTheme.typography.captionSB,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Tappable provider risk-type badge that opens the explanatory bottom sheet on click.
 * Self-contained so both the swap screen and the provider list can drop it in without
 * duplicating the sheet wiring.
 */
@Composable
fun ProviderRiskBadge(
    riskType: ProviderRiskType,
    modifier: Modifier = Modifier,
) {
    var showInfo by remember { mutableStateOf(false) }

    BadgePill(
        iconRes = riskType.iconRes,
        text = stringResource(riskType.titleRes),
        contentColor = riskType.color(),
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { showInfo = true },
        ),
    )

    if (showInfo) {
        ProviderRiskInfoBottomSheet(onDismiss = { showInfo = false })
    }
}

@Composable
fun EstimationTimeBadge(
    seconds: Long,
    modifier: Modifier = Modifier,
) {
    BadgePill(
        iconRes = Res.drawable.clock_filled_24,
        text = formatDurationShort(seconds),
        contentColor = ComposeAppTheme.colors.textSecondary,
        modifier = modifier,
        backgroundColor = ComposeAppTheme.colors.surfacePlaceholder,
    )
}
