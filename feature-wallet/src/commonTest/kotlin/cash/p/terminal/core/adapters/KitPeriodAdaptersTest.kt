package cash.p.terminal.core.adapters

import cash.p.terminal.core.adapters.stellar.StellarTransactionsAdapter
import cash.p.terminal.core.managers.EvmKitWrapper
import cash.p.terminal.core.managers.SolanaKitWrapper
import cash.p.terminal.core.managers.StellarKitWrapper
import cash.p.terminal.core.managers.TonKitWrapper
import cash.p.terminal.data.repository.EvmTransactionRepository
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.modules.transactions.TimestampRange
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.solanakit.SolanaKit
import io.horizontalsystems.stellarkit.StellarKit
import io.horizontalsystems.stellarkit.TagQuery as StellarTagQuery
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.models.TagQuery as TonTagQuery
import io.mockk.coEvery
import io.mockk.every
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KitPeriodAdaptersTest {

    private val period = TimestampRange(since = 1_000, until = 2_000)
    private val splToken = Token(
        coin = Coin("usdc", "USD Coin", "USDC"),
        blockchain = Blockchain(BlockchainType.Solana, "Solana", null),
        type = TokenType.Spl("USDC_MINT"),
        decimals = 6,
    )

    // --- Solana ---

    private val solanaKit = mockk<SolanaKit>(relaxed = true)
    private val solana = SolanaTransactionsAdapter(
        solanaKitWrapper = mockk<SolanaKitWrapper> { every { solanaKit } returns this@KitPeriodAdaptersTest.solanaKit },
        solanaTransactionConverter = mockk(relaxed = true),
    )

    @Test
    fun getTransactions_solanaSplPeriod_passesBoundsToKit() = runTest {
        coEvery { solanaKit.getSplTransactions(any(), any(), any(), any(), any(), any()) } returns emptyList()

        solana.getTransactions(null, splToken, 10, FilterTransactionType.Incoming, null, period)

        coVerify { solanaKit.getSplTransactions("USDC_MINT", true, null, 10, 1_000L, 2_000L) }
    }

    @Test
    fun getOldestTransactionTimestamp_solanaSplOutgoing_usesSameMintAndDirection() = runTest {
        coEvery { solanaKit.oldestSplTransactionTimestamp("USDC_MINT", false) } returns 500L

        assertEquals(500L, solana.getOldestTransactionTimestamp(splToken, FilterTransactionType.Outgoing, null))
    }

    @Test
    fun getOldestTransactionTimestamp_solanaAddressOrUnsupportedType_returnsNull() = runTest {
        assertNull(solana.getOldestTransactionTimestamp(null, FilterTransactionType.All, "addr"))
        assertNull(solana.getOldestTransactionTimestamp(null, FilterTransactionType.Swap, null))
    }

    @Test
    fun getOldestTransactionTimestamp_solanaNoToken_asksAllTransactions() = runTest {
        coEvery { solanaKit.oldestTransactionTimestamp(null) } returns 7L

        assertEquals(7L, solana.getOldestTransactionTimestamp(null, FilterTransactionType.All, null))
    }

    // --- TON ---

    private val tonKit = mockk<TonKit>(relaxed = true)
    private val ton = TonTransactionsAdapter(
        tonKitWrapper = mockk<TonKitWrapper> { every { tonKit } returns this@KitPeriodAdaptersTest.tonKit },
        tonTransactionConverter = mockk(relaxed = true),
    )

    @Test
    fun getTransactions_tonPeriod_passesBoundsAndOldestUsesSameTagQuery() = runTest {
        val historyQuery = slot<TonTagQuery>()
        coEvery { tonKit.events(capture(historyQuery), any(), any(), any(), any()) } returns emptyList()
        val oldestQuery = slot<TonTagQuery>()
        coEvery { tonKit.oldestEventTimestamp(capture(oldestQuery)) } returns 42L

        ton.getTransactions(null, null, 10, FilterTransactionType.Incoming, null, period)
        val oldest = ton.getOldestTransactionTimestamp(null, FilterTransactionType.Incoming, null)

        coVerify { tonKit.events(any(), null, 10, 1_000L, 2_000L) }
        assertEquals(42L, oldest)
        assertEquals(historyQuery.captured, oldestQuery.captured)
    }

    @Test
    fun getOldestTransactionTimestamp_tonApproveFilter_returnsNull() = runTest {
        assertNull(ton.getOldestTransactionTimestamp(null, FilterTransactionType.Approve, null))
    }

    // --- Stellar ---

    private val stellarKit = mockk<StellarKit>(relaxed = true)
    private val stellar = StellarTransactionsAdapter(
        stellarKitWrapper = mockk<StellarKitWrapper> { every { stellarKit } returns this@KitPeriodAdaptersTest.stellarKit },
        transactionConverter = mockk(relaxed = true),
    )

    @Test
    fun getTransactions_stellarPeriod_passesBoundsAndOldestUsesSameTagQuery() = runTest {
        val historyQuery = slot<StellarTagQuery>()
        coEvery { stellarKit.operationsBefore(capture(historyQuery), any(), any(), any(), any()) } returns emptyList()
        val oldestQuery = slot<StellarTagQuery>()
        coEvery { stellarKit.oldestOperationTimestamp(capture(oldestQuery)) } returns 42L

        stellar.getTransactions(null, null, 10, FilterTransactionType.Outgoing, "G_ADDR", period)
        val oldest = stellar.getOldestTransactionTimestamp(null, FilterTransactionType.Outgoing, "G_ADDR")

        coVerify { stellarKit.operationsBefore(any(), null, 10, 1_000L, 2_000L) }
        assertEquals(42L, oldest)
        assertEquals(historyQuery.captured, oldestQuery.captured)
    }

    // --- EVM ---

    private val evmRepository = mockk<EvmTransactionRepository>(relaxed = true)
    private val evmToken = Token(
        coin = Coin("ethereum", "Ethereum", "ETH"),
        blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
        type = TokenType.Native,
        decimals = 18,
    )
    private val evm = EvmTransactionsAdapter(
        evmKitWrapper = mockk<EvmKitWrapper>(relaxed = true),
        evmTransactionRepository = evmRepository,
        baseToken = evmToken,
        coinManager = mockk(relaxed = true),
        source = mockk(relaxed = true),
        evmTransactionSource = mockk(relaxed = true),
        evmLabelManager = mockk(relaxed = true),
    )

    @Test
    fun getTransactions_evmPeriod_passesPeriodAndOldestUsesSameFilters() = runTest {
        val historyTags = slot<List<List<String>>>()
        coEvery { evmRepository.getFullTransactionsAsync(capture(historyTags), any(), any(), any()) } returns emptyList()
        val oldestTags = slot<List<List<String>>>()
        coEvery { evmRepository.getOldestTransactionTimestamp(capture(oldestTags)) } returns 42L

        evm.getTransactions(null, evmToken, 10, FilterTransactionType.Incoming, "0xABC", period)
        val oldest = evm.getOldestTransactionTimestamp(evmToken, FilterTransactionType.Incoming, "0xABC")

        coVerify { evmRepository.getFullTransactionsAsync(any(), null, 10, period) }
        assertEquals(42L, oldest)
        assertEquals(historyTags.captured, oldestTags.captured)
    }
}
