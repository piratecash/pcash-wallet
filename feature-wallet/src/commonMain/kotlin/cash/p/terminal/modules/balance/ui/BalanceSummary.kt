package cash.p.terminal.modules.balance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.balance.TotalUIState
import cash.p.terminal.resources.Button_Hide
import cash.p.terminal.resources.Button_Show
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_balance_triangle_down_12
import cash.p.terminal.resources.ic_eye_20
import cash.p.terminal.resources.ic_eye_off_20
import cash.p.terminal.resources.total_balance
import cash.p.terminal.ui_compose.components.BalanceActionsRow
import cash.p.terminal.ui_compose.components.MicroSBSecondary
import cash.p.terminal.ui_compose.components.Title1Primary
import cash.p.terminal.ui_compose.components.body_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import java.util.Locale
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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
    val locale = Locale.getDefault()
    val cornerRadius = 12.dp
    val shape = RoundedCornerShape(cornerRadius)
    val borderColor = ComposeAppTheme.colors.borderAccentSubtle
    val toggleVisibilityInteractionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ComposeAppTheme.colors.surfacePrimary)
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
        MicroSBSecondary(
            text = stringResource(Res.string.total_balance).uppercase(locale),
            maxLines = 1,
        )
        Spacer(Modifier.size(4.dp))
        Icon(
            modifier = Modifier.size(16.dp),
            painter = painterResource(
                if (visible) Res.drawable.ic_eye_20 else Res.drawable.ic_eye_off_20
            ),
            contentDescription = stringResource(
                if (visible) Res.string.Button_Hide else Res.string.Button_Show
            ),
            tint = ComposeAppTheme.colors.iconSecondary,
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
        Title1Primary(
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
                painter = painterResource(Res.drawable.ic_balance_triangle_down_12),
                contentDescription = null,
                tint = ComposeAppTheme.colors.brandDefault,
            )
        }
    }
}

internal fun Modifier.toggleBalanceVisibility(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier = clickable(
    role = Role.Button,
    interactionSource = interactionSource,
    indication = null,
    onClick = onClick,
)

internal fun Modifier.balanceSummaryBorder(cornerRadius: Dp, borderColor: Color): Modifier =
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
