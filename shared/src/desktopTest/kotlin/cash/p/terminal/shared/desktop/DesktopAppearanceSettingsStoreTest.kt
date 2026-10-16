package cash.p.terminal.shared.desktop

import cash.p.terminal.entities.LaunchPage
import cash.p.terminal.modules.settings.appearance.PriceChangeInterval
import cash.p.terminal.modules.theme.ThemeType
import cash.p.terminal.shared.main.MainDestination
import cash.p.terminal.wallet.balance.BalanceViewType
import io.horizontalsystems.core.DesktopSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopAppearanceSettingsStoreTest {

    private val directory = Files.createTempDirectory("appearance-store").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settingsStore = DesktopSettingsStore(File(directory, "settings.preferences_pb"), scope)
    private val store = DesktopAppearanceSettingsStore(settingsStore)

    @AfterTest
    fun tearDown() {
        scope.cancel()
        directory.deleteRecursively()
    }

    @Test
    fun read_nothingStored_returnsTheAndroidDefaults() {
        assertEquals(ThemeType.System, store.currentTheme)
        assertNull(store.balanceViewType)
        assertNull(store.launchPage)
        assertTrue(store.marketsTabEnabled)
        assertTrue(store.balanceTabButtonsEnabled)
        assertEquals(PriceChangeInterval.LAST_24H, store.priceChangeInterval)
        assertEquals("", store.customDashPeers)
        assertNull(store.mainTab)
    }

    @Test
    fun write_everyValue_isReadBackAndFlowsFollow() {
        store.currentTheme = ThemeType.Dark
        store.balanceViewType = BalanceViewType.FiatThenCoin
        store.launchPage = LaunchPage.Watchlist
        store.marketsTabEnabled = false
        store.balanceTabButtonsEnabled = false
        store.priceChangeInterval = PriceChangeInterval.FROM_UTC_MIDNIGHT
        store.customDashPeers = "10.0.0.1"
        store.mainTab = MainDestination.Transactions

        assertEquals(ThemeType.Dark, store.currentTheme)
        assertEquals(BalanceViewType.FiatThenCoin, store.balanceViewType)
        assertEquals(LaunchPage.Watchlist, store.launchPage)
        assertEquals("Watchlist", settingsStore.launchPage)
        assertFalse(store.marketsTabEnabledFlow.value)
        assertFalse(store.balanceTabButtonsEnabledFlow.value)
        assertEquals(PriceChangeInterval.FROM_UTC_MIDNIGHT, store.priceChangeIntervalFlow.value)
        assertEquals("10.0.0.1", store.customDashPeers)
        assertEquals(MainDestination.Transactions, store.mainTab)
    }

    @Test
    fun clearAll_afterChanges_resetsValuesAndFlows() {
        store.currentTheme = ThemeType.Light
        store.marketsTabEnabled = false
        store.balanceTabButtonsEnabled = false
        store.priceChangeInterval = PriceChangeInterval.FROM_UTC_MIDNIGHT

        settingsStore.clearAll()

        assertEquals(ThemeType.System, store.currentTheme)
        assertTrue(store.marketsTabEnabledFlow.value)
        assertTrue(store.balanceTabButtonsEnabledFlow.value)
        assertEquals(PriceChangeInterval.LAST_24H, store.priceChangeIntervalFlow.value)
        assertEquals(PriceChangeInterval.LAST_24H, DesktopPriceChangeIntervalProvider(store).interval)
    }
}
