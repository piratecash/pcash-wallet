package cash.p.terminal.modules.balance.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.balance.TotalUIState
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_arrow_down_left_24
import cash.p.terminal.resources.ic_arrow_up_right_24
import cash.p.terminal.resources.ic_swap_24
import cash.p.terminal.ui_compose.components.BalanceActionButton
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO, widthDp = 360)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360)
@Composable
private fun BalanceSummaryPreview() {
    ComposeAppTheme {
        Column(
            modifier = Modifier
                .background(ComposeAppTheme.colors.backgroundBase)
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
                            Triple(Res.drawable.ic_arrow_up_right_24, "Send", -90f),
                            Triple(Res.drawable.ic_arrow_down_left_24, "Receive", 0f),
                            Triple(Res.drawable.ic_swap_24, "Swap", 0f),
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
