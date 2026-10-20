package cash.p.terminal.modules.settings.appearance

import cash.p.terminal.core.AppearanceSettingsStore
import cash.p.terminal.entities.LaunchPage
import cash.p.terminal.modules.balance.BalanceViewTypeManager
import cash.p.terminal.modules.theme.ThemeService
import cash.p.terminal.modules.theme.ThemeType
import cash.p.terminal.wallet.balance.BalanceViewType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {

    private val store = FakeAppearanceSettingsStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = AppearanceViewModel(
        launchScreenService = LaunchScreenService(store),
        themeService = ThemeService(store),
        balanceViewTypeManager = BalanceViewTypeManager(store),
        localStorage = store,
    )

    @Test
    fun onSetMarketTabsHidden_launchPageMarket_resetsLaunchPageToAuto() {
        store.launchPage = LaunchPage.Market
        val viewModel = createViewModel()

        viewModel.onSetMarketTabsHidden(true)

        assertEquals(LaunchPage.Auto, store.launchPage)
        assertFalse(store.marketsTabEnabled)
        assertTrue(viewModel.uiState.marketsTabHidden)
    }

    @Test
    fun onSetMarketTabsHidden_launchPageWatchlist_resetsLaunchPageToAuto() {
        store.launchPage = LaunchPage.Watchlist
        val viewModel = createViewModel()

        viewModel.onSetMarketTabsHidden(true)

        assertEquals(LaunchPage.Auto, store.launchPage)
    }

    @Test
    fun onSetMarketTabsHidden_launchPageBalance_keepsLaunchPage() {
        store.launchPage = LaunchPage.Balance
        val viewModel = createViewModel()

        viewModel.onSetMarketTabsHidden(true)

        assertEquals(LaunchPage.Balance, store.launchPage)
        assertFalse(store.marketsTabEnabled)
    }

    @Test
    fun onSetMarketTabsHidden_showAgainWithMarketLaunchPage_keepsLaunchPage() {
        store.launchPage = LaunchPage.Market
        val viewModel = createViewModel()

        viewModel.onSetMarketTabsHidden(false)

        assertEquals(LaunchPage.Market, store.launchPage)
        assertTrue(store.marketsTabEnabled)
    }

    @Test
    fun onSetPriceChangeInterval_selectedInterval_writesStore() {
        val viewModel = createViewModel()

        viewModel.onSetPriceChangeInterval(PriceChangeInterval.FROM_UTC_MIDNIGHT)

        assertEquals(PriceChangeInterval.FROM_UTC_MIDNIGHT, store.priceChangeInterval)
        assertEquals(PriceChangeInterval.FROM_UTC_MIDNIGHT, viewModel.uiState.priceChangeInterval)
    }

    @Test
    fun onSetBalanceTabButtonsHidden_hidden_writesStore() {
        val viewModel = createViewModel()

        viewModel.onSetBalanceTabButtonsHidden(true)

        assertFalse(store.balanceTabButtonsEnabled)
        assertTrue(viewModel.uiState.balanceTabButtonsHidden)
    }

    @Test
    fun onEnterTheme_darkTheme_updatesStoreThroughThemeService() {
        val viewModel = createViewModel()

        viewModel.onEnterTheme(ThemeType.Dark)

        assertEquals(ThemeType.Dark, store.currentTheme)
        assertEquals(ThemeType.Dark, viewModel.uiState.themeOptions.selected)
    }

    @Test
    fun onEnterBalanceViewType_fiatFirst_updatesStore() {
        val viewModel = createViewModel()

        viewModel.onEnterBalanceViewType(BalanceViewType.FiatThenCoin)

        assertEquals(BalanceViewType.FiatThenCoin, store.balanceViewType)
    }

    @Test
    fun launchScreenAndBalanceViewType_emptyStore_fallBackToDefaultConstants() {
        assertEquals(LaunchScreenService.DEFAULT_LAUNCH_PAGE, LaunchScreenService(store).selectedLaunchScreen)
        assertEquals(BalanceViewTypeManager.DEFAULT_VIEW_TYPE, BalanceViewTypeManager(store).balanceViewType)
    }
}

private class FakeAppearanceSettingsStore : AppearanceSettingsStore {
    override var currentTheme = ThemeType.System
    override var balanceViewType: BalanceViewType? = null
    override var launchPage: LaunchPage? = null

    private val marketsTabFlow = MutableStateFlow(true)
    override var marketsTabEnabled: Boolean
        get() = marketsTabFlow.value
        set(value) { marketsTabFlow.value = value }
    override val marketsTabEnabledFlow: StateFlow<Boolean> = marketsTabFlow

    private val buttonsFlow = MutableStateFlow(true)
    override var balanceTabButtonsEnabled: Boolean
        get() = buttonsFlow.value
        set(value) { buttonsFlow.value = value }
    override val balanceTabButtonsEnabledFlow: StateFlow<Boolean> = buttonsFlow

    private val intervalFlow = MutableStateFlow(PriceChangeInterval.LAST_24H)
    override var priceChangeInterval: PriceChangeInterval
        get() = intervalFlow.value
        set(value) { intervalFlow.value = value }
    override val priceChangeIntervalFlow: StateFlow<PriceChangeInterval> = intervalFlow

    override var customDashPeers = ""
}
