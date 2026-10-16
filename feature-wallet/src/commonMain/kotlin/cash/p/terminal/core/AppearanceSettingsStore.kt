package cash.p.terminal.core

import cash.p.terminal.entities.LaunchPage
import cash.p.terminal.modules.settings.appearance.PriceChangeInterval
import cash.p.terminal.modules.theme.ThemeType
import cash.p.terminal.wallet.balance.BalanceViewType
import kotlinx.coroutines.flow.StateFlow

interface AppearanceSettingsStore {
    var currentTheme: ThemeType
    var balanceViewType: BalanceViewType?
    var launchPage: LaunchPage?
    var marketsTabEnabled: Boolean
    val marketsTabEnabledFlow: StateFlow<Boolean>
    var balanceTabButtonsEnabled: Boolean
    val balanceTabButtonsEnabledFlow: StateFlow<Boolean>
    var priceChangeInterval: PriceChangeInterval
    val priceChangeIntervalFlow: StateFlow<PriceChangeInterval>
    var customDashPeers: String
}
