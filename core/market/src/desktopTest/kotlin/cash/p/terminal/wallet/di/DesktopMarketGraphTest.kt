package cash.p.terminal.wallet.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import cash.p.terminal.network.desktopAppDataFile
import cash.p.terminal.network.di.networkModule
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.favorites.MarketFavoritesChangeListener
import cash.p.terminal.wallet.favorites.MarketFavoritesManager
import cash.p.terminal.wallet.storage.BlockchainRecord
import cash.p.terminal.wallet.storage.CoinRecord
import cash.p.terminal.wallet.storage.CoinStorage
import cash.p.terminal.wallet.storage.CoinsData
import cash.p.terminal.wallet.storage.MARKET_DATABASE_NAME
import cash.p.terminal.wallet.storage.MarketDatabase
import cash.p.terminal.wallet.storage.TokenRecord
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val DEADLOCK_TIMEOUT_SECONDS = 30L
private const val COIN_UID = "bitcoin"
private const val OTHER_COIN_UID = "dash"
private const val TOKEN_REFERENCE = "0xabc"

/** The Koin graph the desktop app starts, against a redirected application data directory. */
class DesktopMarketGraphTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var originalUserHome: String

    @Before
    fun setUp() {
        originalUserHome = checkNotNull(System.getProperty("user.home"))
        System.setProperty("user.home", temporaryFolder.root.absolutePath)
        check(desktopAppDataFile(MARKET_DATABASE_NAME).startsWith(temporaryFolder.root)) {
            "Application data directory escaped the test folder; is XDG_DATA_HOME or APPDATA set?"
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        System.setProperty("user.home", originalUserHome)
    }

    // Both must be singletons: a second DataStore on the favorites file would be rejected at runtime.
    @Test
    fun desktopGraph_started_resolvesMarketKitWrapperAndFavoritesManagerAsSingletons() {
        val koin = startDesktopKoin()

        assertSame(koin.get<MarketKitWrapper>(), koin.get<MarketKitWrapper>())
        assertSame(koin.get<MarketFavoritesManager>(), koin.get<MarketFavoritesManager>())
    }

    @Test
    fun marketKitWrapper_freshDataDirectory_servesCoinsFromInitialDump() {
        val marketKit = startDesktopKoin().get<MarketKitWrapper>()

        assertTrue(marketKit.allCoins().isNotEmpty())
        assertNotNull(marketKit.token(TokenQuery(BlockchainType.Ethereum, TokenType.Native)))
    }

    @Test
    fun coinStorage_replaceAllThenApplyRanks_completeWithoutDeadlockAndPersistRows() {
        val storage = CoinStorage(startDesktopKoin().get<MarketDatabase>())

        withinDeadlockTimeout { storage.replaceAll(singleCoinData()) }
        withinDeadlockTimeout { storage.applyRanks(mapOf(COIN_UID to 7)) }

        assertEquals(listOf(COIN_UID), storage.allCoins().map { it.uid })
        assertEquals(7, storage.coin(COIN_UID)?.marketCapRank)
        assertEquals(1, checkNotNull(storage.fullCoin(COIN_UID)).tokens.size)
    }

    @Test
    fun marketFavoritesManager_desktopDataStore_persistsAddAndRemoveAcrossInstances() = runBlocking {
        withReleasedFavoritesManager { it.add(COIN_UID) }

        withReleasedFavoritesManager {
            assertEquals(listOf(COIN_UID), it.getAll())
            it.add(OTHER_COIN_UID)
            it.remove(COIN_UID)
        }

        val manager = startDesktopKoin().get<MarketFavoritesManager>()

        assertEquals(listOf(OTHER_COIN_UID), manager.getAll())
        assertEquals(listOf(OTHER_COIN_UID), manager.manualSortingOrder.first())
    }

    private fun startDesktopKoin(): Koin =
        startKoin { modules(networkModule, marketModule) }.koin

    /** Writes through a DataStore we can release: two live instances on one file are rejected. */
    private suspend fun withReleasedFavoritesManager(block: suspend (MarketFavoritesManager) -> Unit) {
        val job = Job()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { desktopAppDataFile(MARKET_FAVORITES_FILE_NAME) },
        )
        try {
            block(MarketFavoritesManager(dataStore, NoOpFavoritesChangeListener))
        } finally {
            job.cancel()
            job.join()
        }
    }

    /** A deadlocked blocking DAO cannot be interrupted, so it runs on a thread we can abandon. */
    private fun withinDeadlockTimeout(block: () -> Unit) {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable).apply { isDaemon = true }
        }
        try {
            executor.submit(Runnable { block() }).get(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun singleCoinData() = CoinsData(
        coins = listOf(CoinRecord(uid = COIN_UID, name = "Bitcoin", code = "BTC")),
        blockchains = listOf(
            BlockchainRecord(
                uid = BlockchainType.Ethereum.uid,
                name = "Ethereum",
                eip3091url = null,
            )
        ),
        tokens = listOf(
            TokenRecord(
                coinUid = COIN_UID,
                blockchainUid = BlockchainType.Ethereum.uid,
                type = "eip20",
                decimals = 18,
                reference = TOKEN_REFERENCE,
            )
        ),
    )

    private object NoOpFavoritesChangeListener : MarketFavoritesChangeListener {
        override fun onFavoritesChanged() = Unit
    }
}
