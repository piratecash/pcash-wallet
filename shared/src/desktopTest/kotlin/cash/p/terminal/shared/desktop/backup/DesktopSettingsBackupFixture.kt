package cash.p.terminal.shared.desktop.backup

import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.contacts.ContactsSettingsBackup
import cash.p.terminal.shared.desktop.DesktopContactsFileStorage
import cash.p.terminal.shared.desktop.balanceHiddenManager
import cash.p.terminal.shared.desktop.vault.DesktopSecretFieldsEncryption
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.managers.BaseTokenManager
import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.core.managers.DefaultCurrencyManager
import cash.p.terminal.core.managers.EncryptDecryptManager
import cash.p.terminal.core.managers.EvmBlockchainCatalog
import cash.p.terminal.core.managers.EvmSyncSourceManager
import cash.p.terminal.core.managers.SolanaRpcSourceManager
import cash.p.terminal.core.providers.EvmExplorerApiKeys
import cash.p.terminal.core.storage.BlockchainSettingsStorage
import cash.p.terminal.core.storage.EvmSyncSourceStorage
import cash.p.terminal.entities.BtcRestoreMode
import cash.p.terminal.entities.LaunchPage
import cash.p.terminal.entities.TransactionDataSortMode
import cash.p.terminal.modules.backuplocal.BackupLocalModule
import cash.p.terminal.modules.backuplocal.fullbackup.AppearanceSettingsBackup
import cash.p.terminal.modules.backuplocal.fullbackup.BlockchainSettingsBackup
import cash.p.terminal.modules.backuplocal.fullbackup.BtcMode
import cash.p.terminal.modules.backuplocal.fullbackup.EvmSyncSourceBackup
import cash.p.terminal.modules.backuplocal.fullbackup.EvmSyncSources
import cash.p.terminal.modules.backuplocal.fullbackup.Settings
import cash.p.terminal.modules.backuplocal.fullbackup.SolanaSyncSource
import cash.p.terminal.modules.backuplocal.fullbackup.decryptedBackupCrypto
import cash.p.terminal.modules.backuplocal.fullbackup.encryptedBackupCrypto
import cash.p.terminal.modules.backuplocal.fullbackup.toChartIndicators
import cash.p.terminal.modules.balance.BalanceViewTypeManager
import cash.p.terminal.modules.chart.ChartIndicatorManager
import cash.p.terminal.modules.chart.ChartIndicatorSetting
import cash.p.terminal.modules.settings.appearance.LaunchScreenService
import cash.p.terminal.modules.settings.appearance.PriceChangeInterval
import cash.p.terminal.modules.theme.ThemeService
import cash.p.terminal.modules.theme.ThemeType
import cash.p.terminal.shared.desktop.DesktopAddressResolutionConfig
import cash.p.terminal.shared.desktop.DesktopAppSettingsBackup
import cash.p.terminal.shared.desktop.DesktopAppearanceSettingsStore
import cash.p.terminal.shared.desktop.DesktopChartIndicatorStorage
import cash.p.terminal.shared.desktop.DesktopWalletSettingsStorage
import cash.p.terminal.shared.desktop.vault.DesktopAppDatabaseProvider
import cash.p.terminal.core.storage.AppDatabase
import cash.p.terminal.core.storage.AppDatabaseProvider
import cash.p.terminal.shared.desktop.vault.DesktopPreferencesReset
import cash.p.terminal.shared.desktop.vault.DesktopVaultFixture
import cash.p.terminal.shared.desktop.vault.password
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.balance.BalanceViewType
import com.google.gson.Gson
import io.horizontalsystems.core.DesktopLanguageManager
import io.horizontalsystems.core.DesktopSettingsStore
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Locale
import kotlin.io.path.createTempDirectory

/** The real desktop settings stack over a fresh vault; only the market catalog is mocked. */
internal class DesktopSettingsBackupFixture {
    private val dataDirectory: File = createTempDirectory("desktop-settings-backup").toFile()
    private val originalLocale = Locale.getDefault()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dispatcherProvider = TestDispatcherProvider(Dispatchers.Unconfined, scope)
    private val marketKit = mockk<MarketKitWrapper> {
        every { blockchains(any()) } answers { firstArg<List<String>>().mapNotNull(BLOCKCHAINS::get) }
    }

    val settingsStore = DesktopSettingsStore(File(dataDirectory, "settings.preferences_pb"), scope)
    val appearanceStore = DesktopAppearanceSettingsStore(settingsStore)
    val themeService = ThemeService(appearanceStore)
    val launchScreenService = LaunchScreenService(appearanceStore)
    val balanceViewTypeManager = BalanceViewTypeManager(appearanceStore)
    val balanceHiddenManager = balanceHiddenManager(settingsStore, dispatcherProvider)
    val languageManager = DesktopLanguageManager(settingsStore)
    val currencyManager = DefaultCurrencyManager(settingsStore)
    val indicatorStorage = DesktopChartIndicatorStorage(
        File(dataDirectory, "indicators.preferences_pb"),
        Gson(),
        dispatcherProvider,
    )
    val chartIndicatorManager = ChartIndicatorManager(indicatorStorage, settingsStore, dispatcherProvider)
    private val coinManager = mockk<ICoinManager> {
        every { getToken(any()) } answers { token(firstArg()) }
    }
    var baseTokenManager = newBaseTokenManager()
        private set

    private val vaultFixture = DesktopVaultFixture(
        dataDirectory = dataDirectory,
        preferencesReset = DesktopPreferencesReset(
            settingsStore = settingsStore,
            favoritesManager = mockk(relaxed = true),
            currencyManager = currencyManager,
            chartIndicatorManager = chartIndicatorManager,
            languageManager = languageManager,
            themeService = themeService,
            launchScreenService = launchScreenService,
            balanceViewTypeManager = balanceViewTypeManager,
        ),
    )
    var vault = vaultFixture.vault()
        private set
    val vaultFiles get() = vaultFixture.files

    // Through the current vault, so the stores follow relaunchVault().
    private val databaseProvider = object : AppDatabaseProvider {
        override val database: AppDatabase get() = DesktopAppDatabaseProvider(vault).database
    }
    val blockchainSettingsStorage = BlockchainSettingsStorage(databaseProvider)
    val evmSyncSourceStorage = EvmSyncSourceStorage(databaseProvider)
    val btcBlockchainManager = BtcBlockchainManager(blockchainSettingsStorage, marketKit)
    val evmSyncSourceManager =
        EvmSyncSourceManager(blockchainSettingsStorage, evmSyncSourceStorage, DesktopAddressResolutionConfig)

    val contactsRepository = ContactsRepository(
        marketKit,
        DesktopContactsFileStorage(vaultFixture.files, DesktopSecretFieldsEncryption(vault)),
    )

    var backup = newBackup()
        private set

    init {
        // The real keys are decoded through Koin; the explorer is irrelevant to the backup.
        mockkObject(EvmExplorerApiKeys)
        every { EvmExplorerApiKeys.etherscan } returns emptyList()
        runBlocking { vault.create(password()) }
    }

    /** A wiping process never opens the storage again: the next vault belongs to a new process. */
    fun relaunchVault() {
        vault = vaultFixture.vault()
        baseTokenManager = newBaseTokenManager()
        backup = newBackup()
    }

    private fun newBaseTokenManager() = BaseTokenManager(coinManager, DesktopWalletSettingsStorage(settingsStore))

    private fun newBackup() = DesktopAppSettingsBackup(
        languageManager = languageManager,
        currencyManager = currencyManager,
        chartIndicatorManager = chartIndicatorManager,
        chartIndicatorStorage = indicatorStorage,
        baseTokenManager = baseTokenManager,
        appearanceSettingsBackup = AppearanceSettingsBackup(
            themeService = themeService,
            balanceViewTypeManager = balanceViewTypeManager,
            launchScreenService = launchScreenService,
            store = appearanceStore,
            balanceHiddenManager = balanceHiddenManager,
            dispatcherProvider = dispatcherProvider,
        ),
        blockchainSettingsBackup = BlockchainSettingsBackup(
            btcBlockchainManager = btcBlockchainManager,
            evmBlockchains = EvmBlockchainCatalog(marketKit),
            evmSyncSourceManager = evmSyncSourceManager,
            evmSyncSourceStorage = evmSyncSourceStorage,
            blockchainSettingsStorage = blockchainSettingsStorage,
            solanaRpcSourceManager = SolanaRpcSourceManager(blockchainSettingsStorage, marketKit),
        ),
        contactsSettingsBackup = ContactsSettingsBackup(contactsRepository),
    )

    fun export(): Settings = backup.currentSettings(cachedKey, PASSPHRASE)

    fun tearDown() {
        unmockkObject(EvmExplorerApiKeys)
        Locale.setDefault(originalLocale)
        vaultFixture.tearDown()
    }

    companion object {
        const val PASSPHRASE = "backup-passphrase"
        const val CUSTOM_RPC = "https://custom.example.com"
        const val CUSTOM_AUTH = "user:secret"
        const val SOLANA_RPC = "TritonOne API"

        // A default Ethereum source other than the first, so a lost selection shows.
        const val SELECTED_RPC = "https://ethereum-rpc.publicnode.com"

        init {
            // Registers the keccak digest the backup MAC needs, as the desktop main does.
            EthereumKit.init()
        }

        val cachedKey: ByteArray =
            requireNotNull(EncryptDecryptManager.getKey(PASSPHRASE, BackupLocalModule.kdfDefault))

        private fun token(query: TokenQuery) = Token(
            coin = Coin(uid = query.blockchainType.uid, name = query.blockchainType.uid, code = query.blockchainType.uid),
            blockchain = Blockchain(query.blockchainType, query.blockchainType.uid, null),
            type = query.tokenType,
            decimals = 8,
        )

        private val BLOCKCHAINS = listOf(
            Blockchain(BlockchainType.Bitcoin, "Bitcoin", null),
            Blockchain(BlockchainType.Ethereum, "Ethereum", null),
        ).associateBy { it.uid }

        /** What an Android device exports with every setting changed from its default. */
        fun androidSettings() = Settings(
            balanceViewType = BalanceViewType.FiatThenCoin,
            appIcon = "Pirate",
            currentTheme = ThemeType.Light,
            chartIndicatorsEnabled = true,
            chartIndicators = ChartIndicatorSetting.defaults().map { it.copy(enabled = !it.enabled) }.toChartIndicators(),
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
                        encryptedBackupCrypto(CUSTOM_AUTH.toByteArray(Charsets.UTF_8), cachedKey, PASSPHRASE),
                    )
                ),
            ),
            solanaSyncSource = SolanaSyncSource(BlockchainType.Solana.uid, SOLANA_RPC),
        )

        fun Settings.customAuth(): String? = evmSyncSources.custom.single().auth
            ?.let { String(decryptedBackupCrypto(it, PASSPHRASE), Charsets.UTF_8) }

        /** The auth ciphertext gets a fresh IV on every export, so it is compared decrypted instead. */
        fun Settings.withoutCustomAuth() = copy(
            evmSyncSources = evmSyncSources.copy(custom = evmSyncSources.custom.map { it.copy(auth = null) })
        )
    }
}
