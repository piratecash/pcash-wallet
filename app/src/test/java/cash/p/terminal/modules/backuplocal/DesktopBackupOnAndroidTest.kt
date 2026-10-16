package cash.p.terminal.modules.backuplocal

import cash.p.terminal.entities.BtcRestoreMode
import cash.p.terminal.entities.LaunchPage
import cash.p.terminal.entities.TransactionDataSortMode
import cash.p.terminal.modules.backuplocal.fullbackup.AppSettingsBackup
import cash.p.terminal.modules.backuplocal.fullbackup.BackupSource
import cash.p.terminal.modules.backuplocal.fullbackup.BtcMode
import cash.p.terminal.modules.backuplocal.fullbackup.ChartIndicators
import cash.p.terminal.modules.backuplocal.fullbackup.DecryptedFullBackup
import cash.p.terminal.modules.backuplocal.fullbackup.EvmSyncSourceBackup
import cash.p.terminal.modules.backuplocal.fullbackup.EvmSyncSources
import cash.p.terminal.modules.backuplocal.fullbackup.FullBackup
import cash.p.terminal.modules.backuplocal.fullbackup.RestoreOutcome
import cash.p.terminal.modules.backuplocal.fullbackup.Settings
import cash.p.terminal.modules.backuplocal.fullbackup.SolanaSyncSource
import cash.p.terminal.modules.backuplocal.fullbackup.WalletBackupProvider
import cash.p.terminal.modules.backuplocal.fullbackup.encryptedBackupCrypto
import cash.p.terminal.modules.settings.appearance.PriceChangeInterval
import cash.p.terminal.modules.theme.ThemeType
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.balance.BalanceViewType
import cash.p.terminal.wallet.entities.EnabledWallet
import io.horizontalsystems.core.entities.BlockchainType
import com.google.gson.Gson
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Backups written by the desktop app (every setting but the app icon, no contacts) restore through the Android provider. */
internal class DesktopBackupOnAndroidTest : BackupProviderRestoreTestFixture() {

    /** Everything the desktop exports: every setting but the app icon, which desktop does not have. */
    private val desktopSettings = Settings(
        balanceViewType = BalanceViewType.FiatThenCoin,
        appIcon = null,
        currentTheme = ThemeType.Dark,
        chartIndicatorsEnabled = true,
        chartIndicators = ChartIndicators(rsi = emptyList(), ma = emptyList(), macd = emptyList()),
        balanceAutoHidden = true,
        conversionTokenQueryId = "bitcoin|native",
        language = "de",
        launchScreen = LaunchPage.Watchlist,
        marketsTabEnabled = false,
        balanceHideButtons = false,
        baseCurrency = "EUR",
        btcModes = listOf(
            BtcMode(BlockchainType.Bitcoin.uid, BtcRestoreMode.Hybrid.raw, TransactionDataSortMode.Bip69.raw)
        ),
        priceChangeMode = PriceChangeInterval.FROM_UTC_MIDNIGHT,
        evmSyncSources = EvmSyncSources(
            selected = listOf(EvmSyncSourceBackup(BlockchainType.Ethereum.uid, SELECTED_RPC, null)),
            custom = listOf(
                EvmSyncSourceBackup(
                    BlockchainType.Ethereum.uid,
                    CUSTOM_RPC,
                    encryptedBackupCrypto(CUSTOM_AUTH.toByteArray(Charsets.UTF_8), null, PASSWORD),
                )
            ),
        ),
        solanaSyncSource = SolanaSyncSource(BlockchainType.Solana.uid, SOLANA_RPC),
    )

    private val desktopSettingsBackup = mockk<AppSettingsBackup>(relaxed = true) {
        every { currentSettings(any(), any()) } returns desktopSettings
        every { contacts } returns emptyList()
    }

    private val desktopProvider = WalletBackupProvider(
        walletStorage = walletStorage,
        settingsManager = settingsManager,
        accountManager = accountManager,
        accountFactory = accountFactory,
        walletManager = walletManager,
        restoreSettingsManager = restoreSettingsManager,
        marketFavoritesManager = marketFavoritesManager,
        appSettingsBackup = desktopSettingsBackup,
    )

    private val desktopAccount = Account(
        id = "desktop-account-id",
        name = "Desktop Wallet",
        type = AccountType.Mnemonic(List(11) { "abandon" } + "about", ""),
        origin = AccountOrigin.Created,
        level = 0,
        isBackedUp = true,
    )

    @Before
    fun setUpDesktopWallet() {
        every { accountManager.accounts } returns listOf(desktopAccount)
        every { walletStorage.enabledWallets(desktopAccount.id) } returns listOf(
            EnabledWallet(
                tokenQueryId = usdtQueryId,
                accountId = desktopAccount.id,
                coinName = "Tether",
                coinCode = "USDT",
                coinDecimals = 6,
                coinImage = null
            )
        )
        coEvery { marketFavoritesManager.getAll() } returns listOf("bitcoin")
        every { accountFactory.getUniqueName(any(), any()) } answers { firstArg() }
        every { accountFactory.account(any(), any(), any(), any(), any()) } answers {
            restoredAccount.copy(name = firstArg(), type = secondArg())
        }
        curate(usdtQueryId)
    }

    @Test
    fun restoreSingleWalletBackup_desktopV3Backup_restoresAccountAndWallets() = runTest {
        val backupJson = desktopProvider.createWalletBackup(desktopAccount, PASSWORD)
        val backupV3 = requireNotNull(backupProvider.parseV3Backup(backupJson))
        val (innerJson, key, kdfParams) = backupProvider.unwrapV3FormatWithKey(backupV3, PASSWORD)
        val walletBackup = Gson().fromJson(innerJson, BackupLocalModule.WalletBackup::class.java)
        val type = backupProvider.accountTypeWithKey(walletBackup, key, kdfParams, PASSWORD)
        val savedWallets = captureRestoredWallets()

        val outcome = backupProvider.restoreSingleWalletBackup(
            requireNotNull(type), desktopAccount.name, walletBackup, BackupSource.Authenticated
        )

        assertEquals(RestoreOutcome.Restored, outcome)
        assertEquals(desktopAccount.type, type)
        assertEquals(listOf(usdtQueryId), savedWallets.captured.map { it.tokenQueryId })
    }

    @Test
    fun restoreSingleWalletBackup_desktopV4Backup_restoresAccountAndWallets() = runTest {
        val binary = desktopProvider.createSingleWalletBackupV4Binary(desktopAccount, PASSWORD)
        val decrypted = requireNotNull(backupProvider.restoreFromV4BinaryBackup(binary, PASSWORD))
        val savedWallets = captureRestoredWallets()

        val outcome = backupProvider.restoreSingleWalletBackup(decrypted.wallets.single())

        assertEquals(RestoreOutcome.Restored, outcome)
        assertEquals(desktopAccount.type, decrypted.wallets.single().account.type)
        assertEquals(listOf(usdtQueryId), savedWallets.captured.map { it.tokenQueryId })
    }

    @Test
    fun restoreFullBackup_desktopV3Backup_restoresWalletsAndEverySettingButTheAppIcon() = runTest {
        val backupJson = desktopProvider.createFullBackup(listOf(desktopAccount.id), PASSWORD)
        val backupV3 = requireNotNull(backupProvider.parseV3Backup(backupJson))
        val (innerJson, key, kdfParams) = backupProvider.unwrapV3FormatWithKey(backupV3, PASSWORD)
        val fullBackup = Gson().fromJson(innerJson, FullBackup::class.java)

        assertEquals(desktopSettings.withoutCustomAuth(), fullBackup.settings?.withoutCustomAuth())
        assertNull(fullBackup.contacts)

        val decrypted =
            backupProvider.decryptedFullBackupWithKey(fullBackup, key, kdfParams, PASSWORD, BackupSource.Authenticated)
        assertRestoredWithDesktopSettings(decrypted)
    }

    @Test
    fun restoreFullBackup_desktopV4Backup_restoresWalletsAndEverySettingButTheAppIcon() = runTest {
        val binary = desktopProvider.createFullBackupV4Binary(listOf(desktopAccount.id), PASSWORD, null, null)
        val decrypted = requireNotNull(backupProvider.restoreFromV4BinaryBackup(binary, PASSWORD))

        assertEquals(desktopSettings.withoutCustomAuth(), decrypted.settings?.withoutCustomAuth())
        assertRestoredWithDesktopSettings(decrypted)
    }

    private suspend fun assertRestoredWithDesktopSettings(decrypted: DecryptedFullBackup) {
        val savedWallets = captureRestoredWallets()

        assertEquals(RestoreOutcome.Restored, backupProvider.restoreFullBackup(decrypted, PASSWORD))

        assertEquals(listOf(desktopAccount.type), decrypted.wallets.map { it.account.type })
        assertEquals(listOf(usdtQueryId), savedWallets.captured.map { it.tokenQueryId })
        coVerify { marketFavoritesManager.addAll(listOf("bitcoin")) }
        verify { balanceViewTypeManager.setViewType(BalanceViewType.FiatThenCoin) }
        verify { themeService.setThemeType(ThemeType.Dark) }
        verify { languageManager.currentLocaleTag = "de" }
        verify { chartIndicatorManager.enable() }
        verify { balanceHiddenManager.setBalanceAutoHidden(true) }
        verify { baseTokenManager.setBaseTokenQueryId("bitcoin|native") }
        verify { launchScreenService.setLaunchScreen(LaunchPage.Watchlist) }
        verify { localStorage.marketsTabEnabled = false }
        verify { localStorage.balanceTabButtonsEnabled = false }
        verify { localStorage.priceChangeInterval = PriceChangeInterval.FROM_UTC_MIDNIGHT }
        verify { currencyManager.setBaseCurrencyCode("EUR") }
        verify { btcBlockchainManager.save(BtcRestoreMode.Hybrid, BlockchainType.Bitcoin) }
        verify { btcBlockchainManager.save(TransactionDataSortMode.Bip69, BlockchainType.Bitcoin) }
        verify { evmSyncSourceManager.saveSyncSource(BlockchainType.Ethereum, CUSTOM_RPC, CUSTOM_AUTH) }
        verify { blockchainSettingsStorage.save(SELECTED_RPC, BlockchainType.Ethereum) }
        verify { blockchainSettingsStorage.save(SOLANA_RPC, BlockchainType.Solana) }
        verify { listOf(appIconService, contactsRepository) wasNot Called }
    }

    private companion object {
        const val PASSWORD = "desktop-test-password"
        const val SELECTED_RPC = "https://ethereum-rpc.publicnode.com"
        const val CUSTOM_RPC = "https://custom.example.com"
        const val CUSTOM_AUTH = "user:secret"
        const val SOLANA_RPC = "TritonOne API"
    }
}
