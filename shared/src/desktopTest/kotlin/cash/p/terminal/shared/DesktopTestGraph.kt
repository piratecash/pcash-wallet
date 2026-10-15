package cash.p.terminal.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import cash.p.terminal.feature.market.di.marketFeatureModule
import cash.p.terminal.modules.chart.ChartIndicatorStorage
import cash.p.terminal.shared.desktop.DesktopChartIndicatorStorage
import cash.p.terminal.shared.desktop.desktopFeatureModule
import cash.p.terminal.shared.desktop.vault.DesktopPreferencesReset
import cash.p.terminal.shared.desktop.vault.DesktopVaultFiles
import cash.p.terminal.vault.PasswordVault
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.SubscriptionManager
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.favorites.MarketFavoritesManager
import cash.p.terminal.wallet.models.MarketGlobal
import cash.p.terminal.wallet.models.MarketInfo
import com.google.gson.Gson
import io.horizontalsystems.core.BaseCurrencyStorage
import io.horizontalsystems.core.DefaultDispatcherProvider
import io.horizontalsystems.core.DesktopSettingsStore
import io.horizontalsystems.core.ILockoutStorage
import io.horizontalsystems.core.MarketSettingsStore
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Single
import java.io.File
import java.math.BigDecimal
import kotlin.io.path.createTempDirectory
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.binds
import org.koin.dsl.module

internal val BITCOIN = Coin(uid = "bitcoin", name = "Bitcoin", code = "BTC", marketCapRank = 1)

internal const val USD_TOTAL_CAP = 1_000_000_000_000L
internal const val EUR_TOTAL_CAP = 2_000_000_000_000L

/** Starts the real desktop Koin graph with a mocked [MarketKitWrapper] and private temp storage. */
internal object DesktopTestGraph {

    fun start(marketKit: MarketKitWrapper = marketKitMock()) {
        val dataDir = createTempDirectory().toFile().apply { deleteOnExit() }
        startKoin {
            modules(
                desktopFeatureModule,
                marketFeatureModule,
                testModule(marketKit, dataDir),
            )
        }
    }

    fun stop() = stopKoin()

    fun marketKitMock(): MarketKitWrapper = mockk(relaxed = true) {
        every { marketGlobalSingle("USD") } returns Single.just(marketGlobal(USD_TOTAL_CAP))
        every { marketGlobalSingle("EUR") } returns Single.just(marketGlobal(EUR_TOTAL_CAP))
        every { topCoinsMarketInfosSingle(any(), any()) } returns Single.just(listOf(bitcoinMarketInfo()))
        every { fullCoins(listOf(BITCOIN.uid)) } returns listOf(FullCoin(BITCOIN, emptyList()))
    }

    private fun testModule(marketKit: MarketKitWrapper, dataDir: File) = module {
        single { marketKit }
        single { SubscriptionManager() }
        single {
            MarketFavoritesManager(
                PreferenceDataStoreFactory.create { File(dataDir, "favorites.preferences_pb") },
                mockk(relaxed = true),
            )
        }
        single { DesktopSettingsStore(File(dataDir, "settings.preferences_pb")) } binds
            arrayOf(MarketSettingsStore::class, BaseCurrencyStorage::class, ILockoutStorage::class)
        // The vault of a test never leaves the temp directory: the real one holds the user's wallets.
        single { DesktopVaultFiles(dataDir) }
        single { PasswordVault(get<DesktopVaultFiles>().vaultFile) }
        single<DesktopPreferencesReset> { mockk(relaxed = true) }
        single<ChartIndicatorStorage> {
            DesktopChartIndicatorStorage(File(dataDir, "indicators.preferences_pb"), Gson(), DefaultDispatcherProvider())
        }
    }

    private fun marketGlobal(marketCap: Long) = MarketGlobal(
        marketCap = BigDecimal(marketCap),
        marketCapChange = null,
        defiMarketCap = null,
        defiMarketCapChange = null,
        volume = null,
        volumeChange = null,
        btcDominance = null,
        btcDominanceChange = null,
        tvl = null,
        tvlChange = null,
        etfTotalInflow = null,
        etfDailyInflow = null,
    )

    private fun bitcoinMarketInfo() = MarketInfo(
        fullCoin = FullCoin(BITCOIN, emptyList()),
        price = BigDecimal(60_000),
        priceChange1d = BigDecimal.ONE,
        priceChange24h = BigDecimal.ONE,
        priceChange7d = null,
        priceChange14d = null,
        priceChange30d = null,
        priceChange90d = null,
        priceChange200d = null,
        priceChange1y = null,
        marketCap = BigDecimal(USD_TOTAL_CAP),
        marketCapRank = 1,
        totalVolume = BigDecimal(1_000_000),
        athPercentage = null,
        atlPercentage = null,
        listedOnTopExchanges = null,
        solidCex = null,
        solidDex = null,
        goodDistribution = null,
        advice = null,
    )
}

private class TestHostOwner : LifecycleOwner, ViewModelStoreOwner {
    override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val viewModelStore = ViewModelStore()
}

/** The test scene has no window, so it supplies the lifecycle and ViewModel owners a real window provides. */
@Composable
internal fun TestWindowHost(content: @Composable () -> Unit) {
    val owner = remember { TestHostOwner() }
    CompositionLocalProvider(
        LocalLifecycleOwner provides owner,
        LocalViewModelStoreOwner provides owner,
        content = content,
    )
}
