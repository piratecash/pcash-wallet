package cash.p.terminal.shared.desktop

import cash.p.terminal.core.AppearanceSettingsStore
import cash.p.terminal.entities.LaunchPage
import cash.p.terminal.modules.settings.appearance.PriceChangeInterval
import cash.p.terminal.modules.theme.ThemeType
import cash.p.terminal.shared.main.MainDestination
import cash.p.terminal.shared.main.MainTabStorage
import cash.p.terminal.wallet.balance.BalanceViewType
import io.horizontalsystems.core.DesktopSettingsStore
import kotlinx.coroutines.flow.StateFlow

/** Same values and defaults as Android `LocalStorageManager`; [store] refreshes the flows on a wipe. */
class DesktopAppearanceSettingsStore(private val store: DesktopSettingsStore) :
    AppearanceSettingsStore, MainTabStorage {

    override var currentTheme: ThemeType
        get() = ThemeType.entries.firstOrNull { it.name == store.string(THEME) } ?: ThemeType.System
        set(value) = store.setString(THEME, value.name)

    override var balanceViewType: BalanceViewType?
        get() = BalanceViewType.entries.firstOrNull { it.name == store.string(BALANCE_VIEW_TYPE) }
        set(value) = store.setString(BALANCE_VIEW_TYPE, value?.name)

    override var launchPage: LaunchPage?
        get() = LaunchPage.fromString(store.launchPage)
        set(value) {
            store.launchPage = value?.name
        }

    override var marketsTabEnabled: Boolean
        get() = store.marketsTabEnabled
        set(value) {
            store.marketsTabEnabled = value
        }

    override val marketsTabEnabledFlow: StateFlow<Boolean> = store.marketsTabEnabledFlow

    override var balanceTabButtonsEnabled: Boolean
        get() = balanceTabButtonsEnabledFlow.value
        set(value) = store.setFlag(BALANCE_TAB_BUTTONS, value)

    override val balanceTabButtonsEnabledFlow: StateFlow<Boolean> = store.flagFlow(BALANCE_TAB_BUTTONS, true)

    override var priceChangeInterval: PriceChangeInterval
        get() = priceChangeIntervalFlow.value
        set(value) = store.setString(PRICE_CHANGE_INTERVAL, value.raw)

    override val priceChangeIntervalFlow: StateFlow<PriceChangeInterval> =
        store.stringFlow(PRICE_CHANGE_INTERVAL) { raw ->
            raw?.let(PriceChangeInterval::fromRaw) ?: PriceChangeInterval.LAST_24H
        }

    override var customDashPeers: String
        get() = store.string(DASH_PEERS).orEmpty()
        set(value) = store.setString(DASH_PEERS, value)

    override var mainTab: MainDestination?
        get() = MainDestination.fromString(store.string(MAIN_TAB))
        set(value) = store.setString(MAIN_TAB, value?.name)

    private companion object {
        const val THEME = "currentTheme"
        const val BALANCE_VIEW_TYPE = "balanceViewType"
        const val BALANCE_TAB_BUTTONS = "balanceTabButtonsEnabled"
        const val PRICE_CHANGE_INTERVAL = "priceChangeInterval"
        const val DASH_PEERS = "customDashPeers"
        const val MAIN_TAB = "mainTab"
    }
}
