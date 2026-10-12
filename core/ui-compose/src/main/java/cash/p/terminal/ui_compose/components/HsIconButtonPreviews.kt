package cash.p.terminal.ui_compose.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_swap_24

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO, widthDp = 360)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360)
@Composable
private fun BalanceActionsPreview() {
    ComposeAppTheme {
        BalanceActionsRow(
            modifier = Modifier
                .background(ComposeAppTheme.colors.backgroundBase)
                .padding(16.dp),
        ) {
            listOf(
                Res.drawable.ic_swap_24 to "Send",
                Res.drawable.ic_swap_24 to "Receive",
                Res.drawable.ic_swap_24 to "Swap",
                Res.drawable.ic_swap_24 to "Staking",
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

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO, widthDp = 360)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360)
@Composable
private fun BalanceActionsThreeButtonsPreview() {
    ComposeAppTheme {
        BalanceActionsRow(
            modifier = Modifier
                .background(ComposeAppTheme.colors.backgroundBase)
                .padding(16.dp),
        ) {
            listOf(
                Res.drawable.ic_swap_24 to "Send",
                Res.drawable.ic_swap_24 to "Receive",
                Res.drawable.ic_swap_24 to "Swap",
            ).forEach { (icon, label) ->
                BalanceActionButton(icon = icon, label = label, onClick = {})
            }
        }
    }
}
