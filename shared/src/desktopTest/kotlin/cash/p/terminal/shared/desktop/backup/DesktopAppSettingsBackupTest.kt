package cash.p.terminal.shared.desktop.backup

import cash.p.terminal.entities.BtcRestoreMode
import cash.p.terminal.entities.EvmSyncSourceRecord
import cash.p.terminal.entities.LaunchPage
import cash.p.terminal.entities.TransactionDataSortMode
import cash.p.terminal.modules.backuplocal.fullbackup.toChartIndicators
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.shared.desktop.DesktopAppSettingsBackup
import cash.p.terminal.shared.desktop.backup.DesktopSettingsBackupFixture.Companion.CUSTOM_AUTH
import cash.p.terminal.shared.desktop.backup.DesktopSettingsBackupFixture.Companion.CUSTOM_RPC
import cash.p.terminal.shared.desktop.backup.DesktopSettingsBackupFixture.Companion.PASSPHRASE
import cash.p.terminal.shared.desktop.backup.DesktopSettingsBackupFixture.Companion.SELECTED_RPC
import cash.p.terminal.shared.desktop.backup.DesktopSettingsBackupFixture.Companion.SOLANA_RPC
import cash.p.terminal.shared.desktop.backup.DesktopSettingsBackupFixture.Companion.androidSettings
import cash.p.terminal.shared.desktop.backup.DesktopSettingsBackupFixture.Companion.customAuth
import cash.p.terminal.shared.desktop.backup.DesktopSettingsBackupFixture.Companion.withoutCustomAuth
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopAppSettingsBackupTest {

    private val fixture = DesktopSettingsBackupFixture()
    private val backup = fixture.backup

    @AfterTest
    fun tearDown() = fixture.tearDown()

    @Test
    fun currentSettings_desktopState_writesEveryFieldButAppIcon() {
        val expected = androidSettings().copy(appIcon = null)
        with(fixture) {
            balanceViewTypeManager.setViewType(requireNotNull(expected.balanceViewType))
            themeService.setThemeType(requireNotNull(expected.currentTheme))
            chartIndicatorManager.enable()
            indicatorStorage.insertAll(
                indicatorStorage.all().map { it.copy(enabled = !it.enabled) }
            )
            balanceHiddenManager.setBalanceAutoHidden(true)
            settingsStore.setString(DesktopAppSettingsBackup.CONVERSION_TOKEN_QUERY_ID, expected.conversionTokenQueryId)
            languageManager.currentLocaleTag = expected.language
            launchScreenService.setLaunchScreen(LaunchPage.Watchlist)
            appearanceStore.marketsTabEnabled = false
            appearanceStore.balanceTabButtonsEnabled = false
            currencyManager.setBaseCurrencyCode(expected.baseCurrency)
            btcBlockchainManager.save(BtcRestoreMode.Hybrid, BlockchainType.Bitcoin)
            btcBlockchainManager.save(TransactionDataSortMode.Bip69, BlockchainType.Bitcoin)
            appearanceStore.priceChangeInterval = requireNotNull(expected.priceChangeMode)
            evmSyncSourceManager.saveSyncSource(BlockchainType.Ethereum, CUSTOM_RPC, CUSTOM_AUTH)
            blockchainSettingsStorage.save(SELECTED_RPC, BlockchainType.Ethereum)
        }

        val settings = fixture.export()

        assertEquals(CUSTOM_AUTH, settings.customAuth())
        assertEquals(expected.withoutCustomAuth(), settings.withoutCustomAuth())
    }

    @Test
    fun restore_androidSettings_appliesEveryField() = runTest {
        val settings = androidSettings()

        backup.restore(settings, PASSPHRASE)

        with(fixture) {
            assertEquals(settings.balanceViewType, balanceViewTypeManager.balanceViewType)
            assertEquals(settings.currentTheme, themeService.selectedTheme)
            assertTrue(chartIndicatorManager.isEnabled)
            assertEquals(settings.chartIndicators, indicatorStorage.all().toChartIndicators())
            assertTrue(balanceHiddenManager.balanceAutoHidden)
            assertEquals(
                settings.conversionTokenQueryId,
                settingsStore.string(DesktopAppSettingsBackup.CONVERSION_TOKEN_QUERY_ID),
            )
            assertEquals(settings.language, languageManager.currentLocaleTag)
            assertEquals(settings.launchScreen, launchScreenService.selectedLaunchScreen)
            assertEquals(settings.marketsTabEnabled, appearanceStore.marketsTabEnabled)
            assertEquals(settings.balanceHideButtons, appearanceStore.balanceTabButtonsEnabled)
            assertEquals(settings.baseCurrency, currencyManager.baseCurrency.code)
            assertEquals(BtcRestoreMode.Hybrid, btcBlockchainManager.restoreMode(BlockchainType.Bitcoin))
            assertEquals(TransactionDataSortMode.Bip69, btcBlockchainManager.transactionSortMode(BlockchainType.Bitcoin))
            assertEquals(settings.priceChangeMode, appearanceStore.priceChangeInterval)
            assertEquals(
                listOf(EvmSyncSourceRecord(BlockchainType.Ethereum.uid, CUSTOM_RPC, CUSTOM_AUTH)),
                evmSyncSourceStorage.evmSyncSources(BlockchainType.Ethereum),
            )
            assertEquals(SELECTED_RPC, blockchainSettingsStorage.evmSyncSourceUrl(BlockchainType.Ethereum))
            assertEquals(SOLANA_RPC, blockchainSettingsStorage.evmSyncSourceUrl(BlockchainType.Solana))
        }
    }

    @Test
    fun restore_noLaunchScreen_leavesLaunchPageUntouched() = runTest {
        fixture.launchScreenService.setLaunchScreen(LaunchPage.Market)

        backup.restore(androidSettings().copy(launchScreen = null), PASSPHRASE)

        assertEquals(LaunchPage.Market, fixture.launchScreenService.selectedLaunchScreen)
    }

    @Test
    fun customRpcCount_customSourceSaved_countsIt() {
        assertNull(backup.customRpcCount())

        fixture.evmSyncSourceManager.saveSyncSource(BlockchainType.Ethereum, CUSTOM_RPC, null)

        assertEquals(1, backup.customRpcCount())
    }

    @Test
    fun contacts_desktop_noneKeptOrRestored() {
        backup.restoreContacts(listOf(Contact("uid", "Alice", emptyList())))

        assertTrue(backup.contacts.isEmpty())
        assertTrue(backup.parseContacts("""[{"uid":"uid","name":"Alice","addresses":[]}]""").isEmpty())
    }
}
