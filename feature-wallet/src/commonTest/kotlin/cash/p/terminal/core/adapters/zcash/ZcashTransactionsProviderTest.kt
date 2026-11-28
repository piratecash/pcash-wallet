package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.modules.transactions.TimestampRange
import cash.p.zcash.Transaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ZcashTransactionsProviderTest {

    private val provider = ZcashTransactionsProvider()

    private fun transaction(txid: String, height: Int = 0, fee: Long = 0, time: Long = 1_700_000_000) = Transaction(
        id = 0,
        txid = txid,
        height = height,
        time = time,
        value = 120_000,
        memo = null,
        fee = fee,
        totalReceived = 120_000,
        isChange = false,
        recipient = null,
    )

    private fun all() = provider
        .getTransactions(null, FilterTransactionType.All, null, MANY)
        .map { it.txid }

    @Test
    fun onTransactions_transactionDisappeared_dropsItFromTheList() = runTest {
        provider.onTransactions(listOf(transaction("a"), transaction("b")))

        provider.onTransactions(listOf(transaction("a")))

        assertEquals(listOf("a"), all())
    }

    @Test
    fun onTransactions_transactionDisappeared_emitsReloadSignal() = runTest {
        val signals = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            provider.transactionsReloadSignalFlow.collect(signals::add)
        }
        provider.onTransactions(listOf(transaction("a"), transaction("b")))
        runCurrent()
        assertTrue(signals.isEmpty())

        provider.onTransactions(listOf(transaction("a")))
        runCurrent()

        assertEquals(1, signals.size)
    }

    @Test
    fun onTransactions_knownTransactionMined_republishesItWithoutReloadSignal() = runTest {
        val published = mutableListOf<List<Transaction>>()
        val signals = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            provider.getNewTransactionsFlowable(FilterTransactionType.All, null).collect(published::add)
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            provider.transactionsReloadSignalFlow.collect(signals::add)
        }
        provider.onTransactions(listOf(transaction("a")))
        runCurrent()

        provider.onTransactions(listOf(transaction("a", height = 2_500_000, fee = 15_000)))
        runCurrent()

        assertEquals(listOf(listOf("a"), listOf("a")), published.map { txs -> txs.map { it.txid } })
        assertTrue(signals.isEmpty())
    }

    @Test
    fun onTransactions_nothingChanged_publishesNothing() = runTest {
        val published = mutableListOf<List<Transaction>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            provider.getNewTransactionsFlowable(FilterTransactionType.All, null).collect(published::add)
        }
        provider.onTransactions(listOf(transaction("a")))
        runCurrent()

        provider.onTransactions(listOf(transaction("a")))
        runCurrent()

        assertEquals(1, published.size)
    }

    @Test
    fun getTransactions_period_returnsOnlyTransactionsInsidePeriod() = runTest {
        provider.onTransactions(listOf(transaction("new", time = 2_000), transaction("in", time = 1_999), transaction("old", time = 999)))

        val page = provider.getTransactions(null, FilterTransactionType.All, null, MANY, TimestampRange(1_000, 2_000))

        assertEquals(listOf("in"), page.map { it.txid })
    }

    @Test
    fun oldestTimestamp_matchingAndNoMatchingTransactions_returnsMinimumOrNull() = runTest {
        provider.onTransactions(listOf(transaction("new", time = 2_000), transaction("old", time = 999)))

        assertEquals(999L, provider.oldestTimestamp(FilterTransactionType.All, null))
        assertNull(provider.oldestTimestamp(FilterTransactionType.Outgoing, null))
    }

    @Test
    fun oldestTimestamp_undatedTransaction_returnsOldestDatedAndPeriodExcludesUndated() = runTest {
        provider.onTransactions(listOf(transaction("dated", time = 1_500), transaction("undated", time = 0)))

        val page = provider.getTransactions(null, FilterTransactionType.All, null, MANY, TimestampRange(null, 2_000))

        assertEquals(1_500L, provider.oldestTimestamp(FilterTransactionType.All, null))
        assertEquals(listOf("dated"), page.map { it.txid })
    }

    private companion object {
        const val MANY = 100
    }
}
