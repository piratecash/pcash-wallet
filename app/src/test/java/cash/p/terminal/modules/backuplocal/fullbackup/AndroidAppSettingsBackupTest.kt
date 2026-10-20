package cash.p.terminal.modules.backuplocal.fullbackup

import cash.p.terminal.core.managers.EncryptDecryptManager
import cash.p.terminal.entities.BtcRestoreMode
import cash.p.terminal.entities.EvmSyncSource
import cash.p.terminal.entities.LaunchPage
import cash.p.terminal.entities.TransactionDataSortMode
import cash.p.terminal.modules.backuplocal.BackupLocalModule
import cash.p.terminal.modules.backuplocal.BackupProviderRestoreTestFixture
import cash.p.terminal.modules.settings.appearance.AppIcon
import cash.p.terminal.modules.settings.appearance.PriceChangeInterval
import cash.p.terminal.modules.theme.ThemeType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.balance.BalanceViewType
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.ethereumkit.models.RpcSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.net.URI

internal class AndroidAppSettingsBackupTest : BackupProviderRestoreTestFixture() {

    private val chartIndicators = ChartIndicators(
        rsi = listOf(RsiBackup(period = 7, enabled = true)),
        ma = listOf(MaBackup(type = "ema", period = 20, enabled = false)),
        macd = listOf(MacdBackup(fast = 10, slow = 20, signal = 5, enabled = true)),
    )

    @Before
    fun setUpAppIcon() {
        every { localStorage.appIcon } returns AppIcon.Main
    }

    @Test
    fun restore_desktopSettings_appliesSharedFieldsOnly() = runTest {
        appSettingsBackup.restore(desktopSettings(), PASSPHRASE)

        verify { languageManager.currentLocaleTag = "de" }
        verify { currencyManager.setBaseCurrencyCode("EUR") }
        verify { chartIndicatorManager.enable() }
        verify { chartIndicatorSettingsDao.insertAll(chartIndicators.toChartIndicatorSettings()) }
        verify { launchScreenService.setLaunchScreen(LaunchPage.Watchlist) }
        verify(exactly = 0) {
            themeService.setThemeType(any())
            balanceViewTypeManager.setViewType(any())
            balanceHiddenManager.setBalanceAutoHidden(any())
            localStorage.marketsTabEnabled = any()
            localStorage.balanceTabButtonsEnabled = any()
            localStorage.priceChangeInterval = any()
            appIconService.setAppIcon(any(), any())
        }
    }

    @Test
    fun restore_androidSettings_appliesEveryField() = runTest {
        appSettingsBackup.restore(androidSettings(), PASSPHRASE)

        verify { balanceViewTypeManager.setViewType(BalanceViewType.FiatThenCoin) }
        verify { themeService.setThemeType(ThemeType.Light) }
        verify { languageManager.currentLocaleTag = "de" }
        verify { chartIndicatorManager.enable() }
        verify { chartIndicatorSettingsDao.insertAll(chartIndicators.toChartIndicatorSettings()) }
        verify { balanceHiddenManager.setBalanceAutoHidden(true) }
        verify { baseTokenManager.setBaseTokenQueryId("bitcoin|native") }
        verify { launchScreenService.setLaunchScreen(LaunchPage.Watchlist) }
        verify { localStorage.marketsTabEnabled = false }
        verify { localStorage.balanceTabButtonsEnabled = true }
        verify { localStorage.priceChangeInterval = PriceChangeInterval.FROM_UTC_MIDNIGHT }
        verify { currencyManager.setBaseCurrencyCode("EUR") }
        verify { btcBlockchainManager.save(BtcRestoreMode.Hybrid, BlockchainType.Bitcoin) }
        verify { btcBlockchainManager.save(TransactionDataSortMode.Bip69, BlockchainType.Bitcoin) }
        verify { evmSyncSourceManager.saveSyncSource(BlockchainType.Ethereum, CUSTOM_RPC, null) }
        verify { blockchainSettingsStorage.save(SELECTED_RPC, BlockchainType.Ethereum) }
        verify { blockchainSettingsStorage.save("Solana RPC", BlockchainType.Solana) }
        verify { appIconService.setAppIcon(AppIcon.Pirate, any()) }
    }

    @Test
    fun currentSettings_everyFieldSet_exportsTheGoldenSettings() {
        givenGoldenAndroidState()
        val cachedKey = requireNotNull(EncryptDecryptManager.getKey(PASSPHRASE, BackupLocalModule.kdfDefault))

        val settings = appSettingsBackup.currentSettings(cachedKey, PASSPHRASE)

        val customAuth = requireNotNull(settings.evmSyncSources.custom.single().auth)
        assertEquals(CUSTOM_AUTH, String(decryptedBackupCrypto(customAuth, PASSPHRASE), Charsets.UTF_8))
        assertEquals(androidSettings(), settings.withoutCustomAuth())
    }

    @Test
    fun restore_goldenExport_appliesEveryFieldAndDecryptsCustomAuth() = runTest {
        givenGoldenAndroidState()
        val cachedKey = requireNotNull(EncryptDecryptManager.getKey(PASSPHRASE, BackupLocalModule.kdfDefault))
        val exported = appSettingsBackup.currentSettings(cachedKey, PASSPHRASE)
        every { localStorage.appIcon } returns AppIcon.Main

        appSettingsBackup.restore(exported, PASSPHRASE)

        verify { evmSyncSourceManager.saveSyncSource(BlockchainType.Ethereum, CUSTOM_RPC, CUSTOM_AUTH) }
        verify { themeService.setThemeType(ThemeType.Light) }
        verify { balanceViewTypeManager.setViewType(BalanceViewType.FiatThenCoin) }
        verify { balanceHiddenManager.setBalanceAutoHidden(true) }
        verify { baseTokenManager.setBaseTokenQueryId("bitcoin|native") }
        verify { launchScreenService.setLaunchScreen(LaunchPage.Watchlist) }
        verify { localStorage.marketsTabEnabled = false }
        verify { localStorage.balanceTabButtonsEnabled = true }
        verify { localStorage.priceChangeInterval = PriceChangeInterval.FROM_UTC_MIDNIGHT }
        verify { btcBlockchainManager.save(BtcRestoreMode.Hybrid, BlockchainType.Bitcoin) }
        verify { btcBlockchainManager.save(TransactionDataSortMode.Bip69, BlockchainType.Bitcoin) }
        verify { blockchainSettingsStorage.save(SELECTED_RPC, BlockchainType.Ethereum) }
        verify { blockchainSettingsStorage.save("Solana RPC", BlockchainType.Solana) }
        verify { languageManager.currentLocaleTag = "de" }
        verify { currencyManager.setBaseCurrencyCode("EUR") }
        verify { chartIndicatorSettingsDao.insertAll(chartIndicators.toChartIndicatorSettings()) }
        verify { appIconService.setAppIcon(AppIcon.Pirate, any()) }
    }

    private fun givenGoldenAndroidState() {
        val bitcoin = Blockchain(BlockchainType.Bitcoin, "Bitcoin", null)
        val ethereum = Blockchain(BlockchainType.Ethereum, "Ethereum", null)
        every { localStorage.appIcon } returns AppIcon.Pirate
        every { localStorage.chartIndicatorsEnabled } returns true
        every { localStorage.marketsTabEnabled } returns false
        every { localStorage.balanceTabButtonsEnabled } returns true
        every { localStorage.priceChangeInterval } returns PriceChangeInterval.FROM_UTC_MIDNIGHT
        every { themeService.selectedTheme } returns ThemeType.Light
        every { balanceViewTypeManager.balanceViewTypeFlow } returns MutableStateFlow(BalanceViewType.FiatThenCoin)
        every { chartIndicatorSettingsDao.getAllBlocking() } returns chartIndicators.toChartIndicatorSettings()
        every { balanceHiddenManager.balanceAutoHidden } returns true
        every { baseTokenManager.token } returns Token(
            coin = Coin(uid = "bitcoin", name = "Bitcoin", code = "BTC"),
            blockchain = bitcoin,
            type = TokenType.Native,
            decimals = 8,
        )
        every { languageManager.currentLocaleTag } returns "de"
        every { launchScreenService.selectedLaunchScreen } returns LaunchPage.Watchlist
        every { currencyManager.baseCurrency } returns Currency("EUR", "€", 2)
        every { btcBlockchainManager.allBlockchains } returns listOf(bitcoin)
        every { btcBlockchainManager.restoreMode(BlockchainType.Bitcoin) } returns BtcRestoreMode.Hybrid
        every { btcBlockchainManager.transactionSortMode(BlockchainType.Bitcoin) } returns TransactionDataSortMode.Bip69
        every { evmBlockchainManager.allBlockchains } returns listOf(ethereum)
        every { evmSyncSourceManager.getSyncSource(BlockchainType.Ethereum) } returns syncSource(SELECTED_RPC, null)
        every { evmSyncSourceManager.customSyncSources(BlockchainType.Ethereum) } returns
            listOf(syncSource(CUSTOM_RPC, CUSTOM_AUTH))
    }

    private fun syncSource(url: String, auth: String?) = EvmSyncSource(
        id = url,
        name = url,
        rpcSource = RpcSource.Http(listOf(URI(url)), auth),
        transactionSource = mockk(relaxed = true),
    )

    private fun androidSettings() = Settings(
        balanceViewType = BalanceViewType.FiatThenCoin,
        appIcon = AppIcon.Pirate.titleText,
        currentTheme = ThemeType.Light,
        chartIndicatorsEnabled = true,
        chartIndicators = chartIndicators,
        balanceAutoHidden = true,
        conversionTokenQueryId = "bitcoin|native",
        language = "de",
        launchScreen = LaunchPage.Watchlist,
        marketsTabEnabled = false,
        balanceHideButtons = true,
        baseCurrency = "EUR",
        btcModes = listOf(
            BtcMode(BlockchainType.Bitcoin.uid, BtcRestoreMode.Hybrid.raw, TransactionDataSortMode.Bip69.raw)
        ),
        priceChangeMode = PriceChangeInterval.FROM_UTC_MIDNIGHT,
        evmSyncSources = EvmSyncSources(
            selected = listOf(EvmSyncSourceBackup(BlockchainType.Ethereum.uid, SELECTED_RPC, null)),
            custom = listOf(EvmSyncSourceBackup(BlockchainType.Ethereum.uid, CUSTOM_RPC, null)),
        ),
        solanaSyncSource = SolanaSyncSource(BlockchainType.Solana.uid, "Solana RPC"),
    )

    private fun desktopSettings() = androidSettings().copy(
        balanceViewType = null,
        appIcon = null,
        currentTheme = null,
        balanceAutoHidden = null,
        conversionTokenQueryId = null,
        marketsTabEnabled = null,
        balanceHideButtons = null,
        btcModes = emptyList(),
        priceChangeMode = null,
        evmSyncSources = EvmSyncSources(selected = emptyList(), custom = emptyList()),
        solanaSyncSource = null,
    )

    private companion object {
        const val PASSPHRASE = "passphrase"
        const val SELECTED_RPC = "https://selected.example.com"
        const val CUSTOM_RPC = "https://custom.example.com"
        const val CUSTOM_AUTH = "user:secret"
    }
}
