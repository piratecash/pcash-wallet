package cash.p.terminal.modules.balance.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.modules.displayoptions.DisplayDiffOptionType
import cash.p.terminal.modules.displayoptions.DisplayPricePeriod
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.BalanceSortType

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO, widthDp = 360)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360)
@Composable
private fun BalanceFiltersPreview() {
    ComposeAppTheme {
        val sort = Select(
            BalanceSortType.Value,
            listOf(BalanceSortType.Value, BalanceSortType.Name, BalanceSortType.PercentGrowth),
        )
        Column(modifier = Modifier.background(ComposeAppTheme.colors.backgroundBase)) {
            listOf(
                DisplayDiffOptionType.BOTH to false,
                DisplayDiffOptionType.NONE to false,
                DisplayDiffOptionType.BOTH to true,
            ).forEach { (displayDiffOptionType, isWatchAccount) ->
                BalanceFilters(
                    sort = sort,
                    displayDiffOptionType = displayDiffOptionType,
                    displayPricePeriod = DisplayPricePeriod.ONE_DAY,
                    isWatchAccount = isWatchAccount,
                    onSelectSortType = {},
                    onDisplayPricePeriod = {},
                    onSettingsClick = {},
                )
            }
        }
    }
}
