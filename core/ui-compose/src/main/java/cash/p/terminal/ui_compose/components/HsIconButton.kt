package cash.p.terminal.ui_compose.components

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.R
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import androidx.compose.material.ripple as legacyRipple

@Composable
fun HsIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    rippleColor: Color = ComposeAppTheme.colors.iconPrimary,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    minWidth: Dp = 48.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth)
            .clickable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = legacyRipple(bounded = false, radius = RippleRadius, color = rippleColor)
            ),
        contentAlignment = Alignment.Center
    ) {
        val contentColor = if (enabled) LocalContentColor.current else ComposeAppTheme.colors.iconDisabled
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
    }
}

@Composable
fun HsBackButton(onClick: () -> Unit) {
    HsIconButton(onClick = onClick) {
        Icon(
            painter = painterResource(id = R.drawable.ic_back),
            contentDescription = stringResource(R.string.Button_Back),
            tint = ComposeAppTheme.colors.brand
        )
    }
}

// Default radius of an unbounded ripple in an IconButton
private val RippleRadius = 24.dp

@Composable
fun BalanceActionsRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
        content = content,
    )
}

@Composable
fun BalanceActionButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconRotation: Float = 0f,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .width(46.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(shape)
                .background(ComposeAppTheme.colors.actionBackground)
                .border(1.dp, ComposeAppTheme.colors.actionBorder, shape)
                .balanceSurfaceIndication(interactionSource),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier
                    .size(24.dp)
                    .rotate(iconRotation),
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (enabled) ComposeAppTheme.colors.brand else ComposeAppTheme.colors.iconDisabled,
            )
        }
        Spacer(Modifier.height(7.dp))
        Caption(
            text = label,
            color = if (enabled) ComposeAppTheme.colors.textSecondary else ComposeAppTheme.colors.textDisabled,
            modifier = Modifier.wrapContentWidth(unbounded = true),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun Modifier.balanceSurfaceIndication(
    interactionSource: MutableInteractionSource,
): Modifier = indication(
    interactionSource = interactionSource,
    indication = ripple(
        bounded = true,
        color = ComposeAppTheme.colors.textPrimary,
    ),
)

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO, widthDp = 360)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360)
@Composable
private fun BalanceActionsPreview() {
    ComposeAppTheme {
        BalanceActionsRow(
            modifier = Modifier
                .background(ComposeAppTheme.colors.tyler)
                .padding(16.dp),
        ) {
            listOf(
                R.drawable.ic_arrow_down_left_24 to "Send",
                R.drawable.ic_arrow_down_left_24 to "Receive",
                R.drawable.ic_swap_24 to "Swap",
                R.drawable.ic_coins_stacking to "Staking",
            ).forEachIndexed { index, (icon, label) ->
                BalanceActionButton(
                    icon = icon,
                    label = label,
                    enabled = index != 3,
                    iconRotation = if (index == 0) 90f else 0f,
                    onClick = {},
                )
            }
        }
    }
}
