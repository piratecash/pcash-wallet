package cash.p.terminal.wallet.syncers

import cash.p.terminal.network.pirate.domain.model.CoinsList
import cash.p.terminal.network.pirate.domain.model.RemoteBlockchain
import cash.p.terminal.network.pirate.domain.model.RemoteCoin
import cash.p.terminal.network.pirate.domain.model.RemoteToken
import cash.p.terminal.network.pirate.domain.repository.CoinsListRepository
import cash.p.terminal.wallet.managers.VirtualCoinMapper
import cash.p.terminal.wallet.models.SyncerState
import cash.p.terminal.wallet.storage.CoinDao
import cash.p.terminal.wallet.storage.CoinStorage
import cash.p.terminal.wallet.storage.CoinsData
import cash.p.terminal.wallet.storage.MarketDatabase
import cash.p.terminal.wallet.storage.SyncerStateDao
import io.horizontalsystems.core.DispatcherProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private class FakeSyncerStateDao : SyncerStateDao {
    private val state = mutableMapOf<String, String>()

    override fun insert(syncerState: SyncerState) {
        state[syncerState.key] = syncerState.value
    }

    override fun get(key: String): String? = state[key]
}

private class TestDispatcherProvider(dispatcher: kotlinx.coroutines.CoroutineDispatcher) : DispatcherProvider {
    override val io = dispatcher
    override val default = dispatcher
    override val main = dispatcher
    override val applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
}

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CoinSyncerTest {

    // Mirrors CoinSyncer's private key: staleness tests prime it directly instead of faking the clock.
    private val keyRanksDownloadedAt = "coin-syncer-v2-ranks-downloaded-at"

    private val dispatcher = UnconfinedTestDispatcher()
    private val syncerStateDao = FakeSyncerStateDao()
    private val repository = mockk<CoinsListRepository>()
    private val coinDao = mockk<CoinDao> {
        every { getCoinsCount() } returns 1
        every { getBlockchainsCount() } returns 1
        every { getTokensCount() } returns 1
    }
    private val storage = mockk<CoinStorage> {
        every { marketDatabase } returns mockk<MarketDatabase> {
            every { coinDao() } returns coinDao
        }
        every { replaceAll(any()) } returns Unit
        every { applyRanks(any()) } returns Unit
        every { ranks() } returns emptyMap()
    }
    private val coinSyncer = CoinSyncer(
        repository = repository,
        storage = storage,
        syncerStateDao = syncerStateDao,
        virtualCoinMapper = VirtualCoinMapper(),
        mapper = CoinsListMapper(),
        dispatcherProvider = TestDispatcherProvider(dispatcher),
    )

    private val validCoinsList = CoinsList(
        blockchains = listOf(RemoteBlockchain("bitcoin", "Bitcoin", null)),
        coins = listOf(
            RemoteCoin(
                coingeckoId = "bitcoin",
                name = "Bitcoin",
                code = "btc",
                priority = null,
                tokens = listOf(RemoteToken(type = "native", blockchainUid = "bitcoin", address = null, decimals = 8))
            )
        )
    )
    private val validRanks = mapOf("bitcoin" to 1)

    @Test
    fun sync_updatedAtUnchangedRanksFresh_onlyChecksUpdates() = runTest(dispatcher) {
        coEvery { repository.coinsUpdatedAt() } returns 10L
        coEvery { repository.coinRanks() } returns validRanks
        coEvery { repository.coinsList() } returns validCoinsList

        // First call establishes state from empty; second call sees the same updated_at and
        // freshly downloaded ranks, so it must not refetch.
        coinSyncer.sync(force = false)
        coinSyncer.sync(force = false)

        coVerify(exactly = 2) { repository.coinsUpdatedAt() }
        coVerify(exactly = 1) { repository.coinRanks() }
        coVerify(exactly = 1) { repository.coinsList() }
    }

    @Test
    fun sync_updatedAtChanged_fetchesListAndRanks_replacesAll() = runTest(dispatcher) {
        coEvery { repository.coinsUpdatedAt() } returns 10L
        coEvery { repository.coinRanks() } returns validRanks
        coEvery { repository.coinsList() } returns validCoinsList

        coinSyncer.sync(force = false)

        coVerify(exactly = 1) { storage.replaceAll(any()) }
        val syncInfo = coinSyncer.syncInfo()
        assertEquals("10", syncInfo.listTimestamp)
        assertEquals(true, syncInfo.serverAvailable)
    }

    @Test
    fun sync_ranksOlderThanHour_appliesRanksOnly_keepsListDownloadedAtAndCounts() = runTest(dispatcher) {
        coEvery { repository.coinsUpdatedAt() } returns 10L
        coEvery { repository.coinRanks() } returns validRanks
        coEvery { repository.coinsList() } returns validCoinsList
        coinSyncer.sync(force = false)
        val listDownloadedAtBefore = coinSyncer.syncInfo().listDownloadedAt
        val coinsCountBefore = coinSyncer.syncInfo().coinsCount
        syncerStateDao.save(keyRanksDownloadedAt, (System.currentTimeMillis() - 61 * 60 * 1000L).toString())

        coinSyncer.sync(force = false)

        coVerify(exactly = 1) { storage.replaceAll(any()) }
        coVerify(exactly = 2) { storage.applyRanks(any()) }
        coVerify(exactly = 1) { repository.coinsList() }
        val syncInfo = coinSyncer.syncInfo()
        assertEquals(listDownloadedAtBefore, syncInfo.listDownloadedAt)
        assertEquals(coinsCountBefore, syncInfo.coinsCount)
    }

    @Test
    fun sync_ranksDownloadedAtInFuture_refreshesRanks() = runTest(dispatcher) {
        coEvery { repository.coinsUpdatedAt() } returns 10L
        coEvery { repository.coinRanks() } returns validRanks
        coEvery { repository.coinsList() } returns validCoinsList
        coinSyncer.sync(force = false)
        syncerStateDao.save(keyRanksDownloadedAt, (System.currentTimeMillis() + 60 * 60 * 1000L).toString())

        coinSyncer.sync(force = false)

        coVerify(exactly = 2) { repository.coinRanks() }
        coVerify(exactly = 2) { storage.applyRanks(any()) }
    }

    @Test
    fun sync_force_fetchesListEvenIfUpdatedAtUnchanged() = runTest(dispatcher) {
        coEvery { repository.coinsUpdatedAt() } returns 10L
        coEvery { repository.coinRanks() } returns validRanks
        coEvery { repository.coinsList() } returns validCoinsList
        coinSyncer.sync(force = false)

        coinSyncer.sync(force = true)

        coVerify(exactly = 2) { repository.coinsList() }
        coVerify(exactly = 2) { storage.replaceAll(any()) }
        assertEquals("10", coinSyncer.syncInfo().listTimestamp)
    }

    @Test
    fun sync_updatesRequestFails_listUntouched_staleRanksStillApplied() = runTest(dispatcher) {
        coEvery { repository.coinsUpdatedAt() } throws RuntimeException("offline")
        coEvery { repository.coinRanks() } returns validRanks

        coinSyncer.sync(force = false)

        coVerify(exactly = 0) { repository.coinsList() }
        coVerify(exactly = 0) { storage.replaceAll(any()) }
        coVerify(exactly = 1) { storage.applyRanks(validRanks) }
        val syncInfo = coinSyncer.syncInfo()
        assertEquals(false, syncInfo.serverAvailable)
        assertNull(syncInfo.listTimestamp)
        assertNotNull(syncInfo.ranksDownloadedAt)
    }

    @Test
    fun sync_everyRequestFails_nothingWritten_serverUnavailable() = runTest(dispatcher) {
        coEvery { repository.coinsUpdatedAt() } throws RuntimeException("offline")
        coEvery { repository.coinRanks() } throws RuntimeException("offline")

        coinSyncer.sync(force = false)

        coVerify(exactly = 0) { storage.replaceAll(any()) }
        coVerify(exactly = 0) { storage.applyRanks(any()) }
        assertEquals(false, coinSyncer.syncInfo().serverAvailable)
        assertNull(coinSyncer.syncInfo().listTimestamp)
        assertNull(coinSyncer.syncInfo().ranksDownloadedAt)
    }

    @Test
    fun sync_updatesRecoveredNothingOutdated_marksServerAvailable() = runTest(dispatcher) {
        coEvery { repository.coinRanks() } returns validRanks
        coEvery { repository.coinsList() } returns validCoinsList
        coEvery { repository.coinsUpdatedAt() } returns 10L
        coinSyncer.sync(force = false)
        coEvery { repository.coinsUpdatedAt() } throws RuntimeException("offline")
        coinSyncer.sync(force = false)
        coEvery { repository.coinsUpdatedAt() } returns 10L

        coinSyncer.sync(force = false)

        coVerify(exactly = 1) { repository.coinRanks() }
        assertEquals(true, coinSyncer.syncInfo().serverAvailable)
    }

    @Test
    fun sync_ranksRequestFails_listStillReplacedWithStoredRanks() = runTest(dispatcher) {
        coEvery { repository.coinsUpdatedAt() } returns 10L
        coEvery { repository.coinsList() } returns validCoinsList
        coEvery { repository.coinRanks() } throws RuntimeException("offline")
        every { storage.ranks() } returns mapOf("bitcoin" to 5)
        val replaced = slot<CoinsData>()
        every { storage.replaceAll(capture(replaced)) } returns Unit

        coinSyncer.sync(force = false)

        assertEquals(5, replaced.captured.coins.single().marketCapRank)
        coVerify(exactly = 0) { storage.applyRanks(any()) }
        val syncInfo = coinSyncer.syncInfo()
        assertEquals("10", syncInfo.listTimestamp)
        assertNull(syncInfo.ranksDownloadedAt)
        assertEquals(false, syncInfo.serverAvailable)
    }

    @Test
    fun sync_ranksFailedAfterListReplaced_retriedOnNextSync() = runTest(dispatcher) {
        coEvery { repository.coinsList() } returns validCoinsList
        coEvery { repository.coinRanks() } returns validRanks
        coEvery { repository.coinsUpdatedAt() } returns 10L
        coinSyncer.sync(force = false)
        // Still fresh, but strictly older than the next list download even within the same millisecond.
        syncerStateDao.save(keyRanksDownloadedAt, (System.currentTimeMillis() - 1000L).toString())
        coEvery { repository.coinsUpdatedAt() } returns 11L
        coEvery { repository.coinRanks() } throws RuntimeException("offline")
        coinSyncer.sync(force = false)
        coEvery { repository.coinRanks() } returns validRanks

        coinSyncer.sync(force = false)

        coVerify(exactly = 3) { repository.coinRanks() }
        coVerify(exactly = 2) { storage.applyRanks(validRanks) }
        assertEquals(true, coinSyncer.syncInfo().serverAvailable)
    }

    @Test
    fun sync_emptyList_listNotReplaced_ranksStillApplied() = runTest(dispatcher) {
        coEvery { repository.coinsUpdatedAt() } returns 10L
        coEvery { repository.coinRanks() } returns validRanks
        coEvery { repository.coinsList() } returns CoinsList(blockchains = emptyList(), coins = emptyList())

        coinSyncer.sync(force = false)

        coVerify(exactly = 0) { storage.replaceAll(any()) }
        coVerify(exactly = 1) { storage.applyRanks(validRanks) }
        assertEquals(false, coinSyncer.syncInfo().serverAvailable)
        assertNull(coinSyncer.syncInfo().listTimestamp)
    }

    @Test
    fun sync_emptyRanks_ranksNotApplied_listStillReplaced() = runTest(dispatcher) {
        coEvery { repository.coinsUpdatedAt() } returns 10L
        coEvery { repository.coinsList() } returns validCoinsList
        coEvery { repository.coinRanks() } returns emptyMap()

        coinSyncer.sync(force = false)

        coVerify(exactly = 1) { storage.replaceAll(any()) }
        coVerify(exactly = 0) { storage.applyRanks(any()) }
        val syncInfo = coinSyncer.syncInfo()
        assertEquals(false, syncInfo.serverAvailable)
        assertEquals("10", syncInfo.listTimestamp)
        assertNull(syncInfo.ranksDownloadedAt)
    }

    @Test
    fun sync_listWithZeroTokens_nothingWritten_keysNotSaved() = runTest(dispatcher) {
        coEvery { repository.coinsUpdatedAt() } returns 10L
        coEvery { repository.coinRanks() } returns validRanks
        coEvery { repository.coinsList() } returns CoinsList(
            blockchains = listOf(RemoteBlockchain("bitcoin", "Bitcoin", null)),
            coins = listOf(
                RemoteCoin(
                    coingeckoId = "bitcoin",
                    name = "Bitcoin",
                    code = "btc",
                    priority = null,
                    // Token references an unlisted blockchain, so it is filtered out by the mapper.
                    tokens = listOf(
                        RemoteToken(type = "native", blockchainUid = "unknown-chain", address = null, decimals = 8)
                    )
                )
            )
        )

        coinSyncer.sync(force = false)

        coVerify(exactly = 0) { storage.replaceAll(any()) }
        assertEquals(false, coinSyncer.syncInfo().serverAvailable)
        assertNull(coinSyncer.syncInfo().listTimestamp)
    }
}
