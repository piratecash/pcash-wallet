package cash.p.terminal.modules.balance.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.R
import cash.p.terminal.modules.balance.TotalUIState
import cash.p.terminal.ui_compose.components.BalanceActionButton
import cash.p.terminal.ui_compose.components.BalanceActionsRow
import cash.p.terminal.ui_compose.components.body_grey
import cash.p.terminal.ui_compose.components.MicroSBGrey
import cash.p.terminal.ui_compose.components.Title1Leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import java.util.Locale

@Composable
internal fun BalanceSummary(
    totalState: TotalUIState,
    onToggleVisibility: () -> Unit,
    onToggleTotalType: () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val visibleState = totalState as? TotalUIState.Visible
    val visible = visibleState != null
    val primary = visibleState?.primaryAmountStr ?: "*****"
    val secondary = visibleState?.secondaryAmountStr?.let { value ->
        if (value.startsWith("~")) "≈${value.drop(1)}" else value
    } ?: "*****"
    val dimmed = visibleState?.dimmed == true
    val locale = LocalConfiguration.current.locales[0]
    val cornerRadius = 12.dp
    val shape = RoundedCornerShape(cornerRadius)
    val borderColor = ComposeAppTheme.colors.borderAccentSubtle
    val toggleVisibilityInteractionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ComposeAppTheme.colors.lawrence)
            .balanceSummaryBorder(cornerRadius, borderColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        BalanceSummaryTitle(
            visible,
            locale,
            toggleVisibilityInteractionSource,
            onToggleVisibility,
        )
        BalanceSummaryAmounts(
            primary,
            secondary,
            dimmed,
            toggleVisibilityInteractionSource,
            onToggleVisibility,
            onToggleTotalType,
        )
        actions?.let {
            Spacer(Modifier.height(12.dp))
            BalanceActionsRow(content = it)
        }
    }
}

@Composable
private fun BalanceSummaryTitle(
    visible: Boolean,
    locale: Locale,
    interactionSource: MutableInteractionSource,
    onToggleVisibility: () -> Unit,
) {
    Row(
        modifier = Modifier.toggleBalanceVisibility(interactionSource, onToggleVisibility),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MicroSBGrey(
            text = stringResource(R.string.total_balance).uppercase(locale),
            maxLines = 1,
        )
        Spacer(Modifier.size(4.dp))
        Icon(
            modifier = Modifier.size(16.dp),
            painter = painterResource(
                if (visible) R.drawable.ic_eye_20 else R.drawable.ic_eye_off_20
            ),
            contentDescription = stringResource(
                if (visible) R.string.Button_Hide else R.string.Button_Show
            ),
            tint = ComposeAppTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun BalanceSummaryAmounts(
    primary: String,
    secondary: String,
    dimmed: Boolean,
    visibilityInteractionSource: MutableInteractionSource,
    onToggleVisibility: () -> Unit,
    onToggleTotalType: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Title1Leah(
            modifier = Modifier
                .fillMaxWidth()
                .toggleBalanceVisibility(visibilityInteractionSource, onToggleVisibility),
            text = primary,
            dimmed = dimmed,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.clickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleTotalType,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            body_grey(
                modifier = Modifier.weight(1f, fill = false),
                text = secondary,
                dimmed = dimmed,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(4.dp))
            Icon(
                modifier = Modifier.size(12.dp),
                painter = painterResource(R.drawable.ic_balance_triangle_down_12),
                contentDescription = null,
                tint = ComposeAppTheme.colors.jacob,
            )
        }
    }
}

private fun Modifier.toggleBalanceVisibility(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier = clickable(
    role = Role.Button,
    interactionSource = interactionSource,
    indication = null,
    onClick = onClick,
)

private fun Modifier.balanceSummaryBorder(cornerRadius: Dp, borderColor: Color): Modifier =
    drawWithCache {
        val stroke = 1.dp.toPx()
        val radius = cornerRadius.toPx()
        val border = Path().apply {
            fillType = PathFillType.EvenOdd
            addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(radius)))
            addRoundRect(
                RoundRect(
                    Rect(0f, stroke, size.width, size.height - stroke),
                    CornerRadius(radius, radius - stroke),
                )
            )
        }
        onDrawWithContent {
            drawContent()
            drawPath(border, borderColor)
        }
    }

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO, widthDp = 360)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360)
@Composable
private fun BalanceSummaryPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier
                .background(ComposeAppTheme.colors.tyler)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(
                TotalUIState.Visible("\$12,345.67", "~0.1842 BTC", false),
                TotalUIState.Hidden,
                TotalUIState.Visible("\$12,345.67", "~0.1842 BTC", true),
            ).forEachIndexed { index, totalState ->
                val actions: (@Composable RowScope.() -> Unit)? = if (index == 0) {
                    {
                        listOf(
                            Triple(R.drawable.ic_arrow_up_right_24, "Send", -90f),
                            Triple(R.drawable.ic_arrow_down_left_24, "Receive", 0f),
                            Triple(R.drawable.ic_swap_24, "Swap", 0f),
                        ).forEach { (icon, label, rotation) ->
                            BalanceActionButton(
                                icon = icon,
                                label = label,
                                iconRotation = rotation,
                                onClick = {},
                            )
                        }
                    }
                } else {
                    null
                }
                BalanceSummary(
                    totalState = totalState,
                    onToggleVisibility = {},
                    onToggleTotalType = {},
                    actions = actions,
                )
            }
        }
    }
}
