package cash.p.terminal.shared.desktop.menu

import cash.p.terminal.shared.main.MainDestination
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopMenuCommandTest {

    private val mainPage = DesktopMenuContext(
        unlocked = true,
        selectedTab = MainDestination.Balance,
        marketsTabEnabled = true,
        hasActiveAccount = true,
        coinManagerSupported = true,
    )

    private fun enabledIn(context: DesktopMenuContext): Set<DesktopMenuCommand> =
        DesktopMenuCommand.entries.filter { it.isEnabled(context) }.toSet()

    @Test
    fun isEnabled_locked_onlyWebsiteMinimizeQuit() {
        val locked = mainPage.copy(unlocked = false)

        assertEquals(ALWAYS, enabledIn(locked))
    }

    @Test
    fun isEnabled_pageAboveMainPage_onlyLockAndHideBalanceAdded() {
        val pageOnTop = mainPage.copy(selectedTab = null)

        assertEquals(
            ALWAYS + DesktopMenuCommand.Lock + DesktopMenuCommand.ToggleBalanceHidden,
            enabledIn(pageOnTop),
        )
    }

    @Test
    fun isEnabled_mainPageOnBalanceTab_everythingEnabled() {
        assertEquals(DesktopMenuCommand.entries.toSet(), enabledIn(mainPage))
    }

    @Test
    fun isEnabled_marketsTabHidden_showMarketDisabled() {
        val context = mainPage.copy(marketsTabEnabled = false)

        assertEquals(DesktopMenuCommand.entries.toSet() - DesktopMenuCommand.ShowMarket, enabledIn(context))
    }

    @Test
    fun isEnabled_coinManagerUnsupported_coinManagerDisabled() {
        val context = mainPage.copy(coinManagerSupported = false)

        assertEquals(DesktopMenuCommand.entries.toSet() - DesktopMenuCommand.CoinManager, enabledIn(context))
    }

    @Test
    fun isEnabled_noActiveAccount_accountCommandsDisabled() {
        val context = mainPage.copy(hasActiveAccount = false, coinManagerSupported = false)

        assertEquals(
            DesktopMenuCommand.entries.toSet() - setOf(
                DesktopMenuCommand.SwitchWallet,
                DesktopMenuCommand.WalletSettings,
                DesktopMenuCommand.CoinManager,
                DesktopMenuCommand.Refresh,
            ),
            enabledIn(context),
        )
    }

    @Test
    fun isEnabled_otherTabSelected_refreshDisabled() {
        val context = mainPage.copy(selectedTab = MainDestination.Transactions)

        assertEquals(DesktopMenuCommand.entries.toSet() - DesktopMenuCommand.Refresh, enabledIn(context))
    }

    private companion object {
        val ALWAYS = setOf(DesktopMenuCommand.Website, DesktopMenuCommand.Minimize, DesktopMenuCommand.Quit)
    }
}
