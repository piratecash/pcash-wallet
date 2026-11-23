package cash.p.terminal.core.adapters

import cash.p.terminal.core.managers.MoneroKitWrapper
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.modules.transactions.TimestampRange
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import com.m2049r.xmrwallet.model.TransactionInfo
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneroTransactionsAdapterTest {
    private val blockchain = Blockchain(BlockchainType.Monero, "Monero", null)
    private val token = Token(Coin("monero", "Monero", "XMR"), blockchain, TokenType.Native, 12)
    private val kitWrapper = mockk<MoneroKitWrapper>(relaxed = true)
    private val adapter = MoneroTransactionsAdapter(
        kitWrapper,
        TransactionSource(blockchain, mockk<Account>(relaxed = true), null),
        mockk<OfflineModeManager>(relaxed = true),
    )

    @Test
    fun getTransactions_period_pagesOnlyTransactionsInsidePeriod() = runTest {
        every { kitWrapper.getTransactions() } returns listOf(
            transaction("newer", 2_000),
            transaction("inside-1", 1_999),
            transaction("inside-2", 1_000),
            transaction("older", 999),
        )

        val first = adapter.getTransactions(null, token, 1, FilterTransactionType.All, null, PERIOD)
        val second = adapter.getTransactions(first.last(), token, 10, FilterTransactionType.All, null, PERIOD)

        assertEquals(listOf("inside-1"), first.map { it.transactionHash })
        assertEquals(listOf("inside-2"), second.map { it.transactionHash })
    }

    @Test
    fun getOldestTransactionTimestamp_typeFilter_returnsOldestMatchingOrNull() = runTest {
        every { kitWrapper.getTransactions() } returns listOf(
            transaction("incoming", 2_000, TransactionInfo.Direction.Direction_In),
            transaction("outgoing", 1_000, TransactionInfo.Direction.Direction_Out),
        )

        assertEquals(1_000L, adapter.getOldestTransactionTimestamp(token, FilterTransactionType.All, null))
        assertEquals(2_000L, adapter.getOldestTransactionTimestamp(token, FilterTransactionType.Incoming, null))
        assertNull(adapter.getOldestTransactionTimestamp(token, FilterTransactionType.Swap, null))
    }

    private fun transaction(
        hash: String,
        timestamp: Long,
        direction: TransactionInfo.Direction = TransactionInfo.Direction.Direction_In,
    ) = TransactionInfo(
        direction.value, false, false, 100, 1, 10, hash, timestamp, "", 0, 0, 20, 0, "", emptyList(),
    )

    private companion object {
        val PERIOD = TimestampRange(since = 1_000, until = 2_000)
    }
}
