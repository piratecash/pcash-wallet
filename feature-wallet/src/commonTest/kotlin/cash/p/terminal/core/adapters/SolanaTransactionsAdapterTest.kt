package cash.p.terminal.core.adapters

import cash.p.terminal.core.adapters.SolanaTransactionConverterTestFixture.usdcToken
import cash.p.terminal.core.managers.SolanaKitWrapper
import cash.p.terminal.modules.transactions.FilterTransactionType
import io.horizontalsystems.solanakit.SolanaKit
import io.horizontalsystems.solanakit.models.FullTransaction
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class SolanaTransactionsAdapterTest {

    private val allTransactions = MutableSharedFlow<List<FullTransaction>>(extraBufferCapacity = 1)

    private val kit: SolanaKit = mockk(relaxed = true) {
        every { allTransactionsFlow(null) } returns allTransactions
        every { splTransactionsFlow("USDC_MINT", false) } returns emptyFlow()
    }

    private val adapter = SolanaTransactionsAdapter(
        solanaKitWrapper = mockk<SolanaKitWrapper>(relaxed = true) {
            every { solanaKit } returns kit
        },
        solanaTransactionConverter = mockk(relaxed = true)
    )

    @Test
    fun getTransactionsReloadSignalFlow_batchBecameIncoming_emitsUnitWhileOutgoingFilterStaysSilent() =
        runTest(UnconfinedTestDispatcher()) {
            val reloadSignals = mutableListOf<Unit>()
            val outgoingRecords = mutableListOf<Any>()
            val reloadJob = launch { adapter.getTransactionsReloadSignalFlow().collect { reloadSignals.add(it) } }
            val outgoingJob = launch {
                adapter.getTransactionRecordsFlow(usdcToken, FilterTransactionType.Outgoing, null)
                    .collect { outgoingRecords.add(it) }
            }

            allTransactions.emit(listOf(mockk<FullTransaction>(relaxed = true)))

            assertEquals(listOf(Unit), reloadSignals)
            assertEquals(emptyList<Any>(), outgoingRecords)
            reloadJob.cancel()
            outgoingJob.cancel()
        }
}
