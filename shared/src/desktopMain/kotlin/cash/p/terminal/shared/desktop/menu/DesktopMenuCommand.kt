package cash.p.terminal.shared.desktop.menu

import cash.p.terminal.shared.main.MainDestination

enum class DesktopMenuCommand(val tab: MainDestination? = null) {
    Lock,
    ShowSettings(MainDestination.Settings),
    About,
    CoinManager,
    SwitchWallet,
    ManageWallets,
    WalletSettings,
    Backups,
    ShowBalance(MainDestination.Balance),
    ShowTransactions(MainDestination.Transactions),
    ShowMarket(MainDestination.Market),
    ToggleBalanceHidden,
    Refresh,
    WhatsNew,
    Contact,
    Website,
    Minimize,
    Quit,
}

/** [selectedTab] is null unless the main page is on top with nothing above it. */
data class DesktopMenuContext(
    val unlocked: Boolean,
    val selectedTab: MainDestination?,
    val marketsTabEnabled: Boolean,
    val hasActiveAccount: Boolean,
    val coinManagerSupported: Boolean,
) {
    val mainPageOnTop: Boolean get() = selectedTab != null
}

/** Screens open only from the bare main page, where Android opens them too, so no flow is left half done. */
fun DesktopMenuCommand.isEnabled(context: DesktopMenuContext): Boolean = when (this) {
    DesktopMenuCommand.Website, DesktopMenuCommand.Minimize, DesktopMenuCommand.Quit -> true
    DesktopMenuCommand.Lock, DesktopMenuCommand.ToggleBalanceHidden -> context.unlocked
    else -> context.unlocked && context.mainPageOnTop && isEnabledOnMainPage(context)
}

private fun DesktopMenuCommand.isEnabledOnMainPage(context: DesktopMenuContext): Boolean = when (this) {
    DesktopMenuCommand.ShowMarket -> context.marketsTabEnabled
    DesktopMenuCommand.SwitchWallet, DesktopMenuCommand.WalletSettings -> context.hasActiveAccount
    DesktopMenuCommand.CoinManager -> context.coinManagerSupported
    DesktopMenuCommand.Refresh -> context.hasActiveAccount && context.selectedTab == MainDestination.Balance
    else -> true
}
