package cash.p.terminal.shared.desktop.session

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.feature.market.di.marketFeatureModule
import cash.p.terminal.modules.chart.ChartIndicatorStorage
import cash.p.terminal.network.di.networkModule
import cash.p.terminal.shared.desktop.DesktopChartIndicatorStorage
import cash.p.terminal.wallet.favorites.MarketFavoritesManager
import com.google.gson.Gson
import io.horizontalsystems.core.DefaultDispatcherProvider
import cash.p.terminal.shared.DesktopTestGraph
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.shared.desktop.DesktopAppRelauncher
import cash.p.terminal.shared.desktop.DesktopWalletSession
import cash.p.terminal.shared.desktop.DesktopWipeCoordinator
import cash.p.terminal.shared.desktop.desktopFeatureModule
import cash.p.terminal.shared.desktop.vault.DesktopPreferencesReset
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.DesktopVaultFiles
import cash.p.terminal.shared.desktop.vault.WipeOutcome
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.BaseCurrencyStorage
import io.horizontalsystems.core.DesktopSettingsStore
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.ILockoutStorage
import io.horizontalsystems.core.MarketSettingsStore
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module
import java.io.File
import java.util.UUID

/**
 * One desktop process over the real Koin graph and a private data directory: [restart] keeps the directory,
 * like quitting and starting the app again. The network and the wallet list (it needs the market catalog) are mocked.
 */
internal class DesktopWalletGraph(
    val dataDirectory: File,
    val piratePlaceRepository: PiratePlaceRepository = mockk(relaxed = true),
) {
    // A process holds one DataStore per file, and a restart in the same JVM cannot release the first one.
    private val settingsStore = DesktopSettingsStore(File(dataDirectory, "settings.preferences_pb"))
    private val chartIndicatorStorage = DesktopChartIndicatorStorage(
        File(dataDirectory, "chart_indicators.preferences_pb"), Gson(), DefaultDispatcherProvider(),
    )
    private lateinit var scope: CoroutineScope
    lateinit var koin: Koin
        private set

    val vault: DesktopVault get() = koin.get()
    val accountManager: IAccountManager get() = koin.get()
    val checkPremiumUseCase: CheckPremiumUseCase get() = koin.get()

    /** What the mocked wallet list holds; the adapters follow it. */
    val activeWallets = MutableStateFlow<List<Wallet>>(emptyList())

    /** A test never starts a second app process. */
    val relauncher = mockk<DesktopAppRelauncher> { every { relaunch(any()) } returns true }

    fun start(): DesktopWalletGraph = apply {
        // As desktop main(): the kits' crypto provider.
        EthereumKit.init()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        koin = startKoin {
            modules(networkModule, marketFeatureModule, desktopFeatureModule, DesktopTestGraph.networkStandIns, testModule())
        }.koin
        koin.get<DesktopWalletSession>().start(scope)
        koin.get<DesktopWipeCoordinator>().start(scope)
    }

    suspend fun restart() {
        stop()
        start()
    }

    /** The warm-up reads on real IO threads; stopped first, it cannot fail in the background of another test. */
    suspend fun wipe(): WipeOutcome {
        checkPremiumUseCase.stopWarmUp()
        return vault.wipe()
    }

    /** The wipe ends the process; the next vault belongs to a new one. */
    suspend fun wipeAndRelaunch(): WipeOutcome = wipe().also { restart() }

    suspend fun closeVault() {
        checkPremiumUseCase.stopWarmUp()
        vault.close()
    }

    suspend fun stop() {
        try {
            closeVault()
        } finally {
            scope.cancel()
            stopKoin()
        }
    }

    private fun testModule() = module {
        // The vault of a test never leaves the temp directory: the real one holds the user's wallets.
        single { DesktopVaultFiles(dataDirectory) }
        single { settingsStore } binds
            arrayOf(MarketSettingsStore::class, BaseCurrencyStorage::class, ILockoutStorage::class)
        single<DesktopPreferencesReset> { mockk(relaxed = true) }
        single<DispatcherProvider> { TestDispatcherProvider(Dispatchers.Unconfined, scope) }
        single { piratePlaceRepository }
        single { relauncher }
        single { chartIndicatorStorage } bind ChartIndicatorStorage::class
        single<MarketFavoritesManager> { mockk { coEvery { getAll() } returns emptyList() } }
        single<IWalletManager> {
            val wallets = activeWallets
            mockk(relaxed = true) {
                every { activeWalletsFlow } returns wallets
                every { activeWallets } answers { wallets.value }
            }
        }
    }
}

/** The BIP-39 test vector: a public phrase that no real wallet uses. */
internal val TEST_WORDS = List(11) { "abandon" } + "about"

internal fun mnemonicAccount(isBackedUp: Boolean, words: List<String> = TEST_WORDS) = Account(
    id = UUID.randomUUID().toString(),
    name = "Wallet",
    type = AccountType.Mnemonic(words, passphrase = ""),
    origin = AccountOrigin.Created,
    level = 0,
    isBackedUp = isBackedUp,
)

/** Every file of the data directory, so a check covers the database, its WAL and the kit databases. */
internal fun File.rawBytes(): String =
    walkTopDown().filter(File::isFile).joinToString("") { String(it.readBytes(), Charsets.ISO_8859_1) }
