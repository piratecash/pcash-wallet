package cash.p.terminal.core.adapters

import cash.p.beam.BeamAddress
import cash.p.beam.BeamAddressType
import cash.p.beam.BeamBalance
import cash.p.beam.BeamNetwork
import cash.p.beam.BeamTransaction
import cash.p.beam.BeamTransactionDirection
import cash.p.beam.BeamTransactionPage
import cash.p.beam.BeamTransactionStatus
import cash.p.beam.BeamWalletSession
import cash.p.beam.BeamWalletState
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.beam.BeamTransactionRecordConverter
import cash.p.terminal.modules.transactions.FilterTransactionType
import cash.p.terminal.modules.transactions.TransactionStatus
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class BeamAdapterTransactionsTest {
    private val blockchain = Blockchain(BlockchainType.Beam, "Beam", null)
    private val token = Token(Coin("beam", "Beam", "BEAM"), blockchain, TokenType.Native, 8)
    private val account = mockk<Account> { every { id } returns "beam-test-account" }
    private val source = TransactionSource(blockchain, account, null)
    private val wallet = mockk<Wallet> {
        every { token } returns this@BeamAdapterTransactionsTest.token
        every { transactionSource } returns source
    }
    private val history = MutableStateFlow<List<BeamTransaction>>(emptyList())
    private val state = MutableStateFlow<BeamWalletState>(BeamWalletState.Stopped)
    private val sdk = mockk<BeamWalletSession> {
        every { transactions } returns history
        every { state } returns this@BeamAdapterTransactionsTest.state
        every { balance } returns MutableStateFlow(BeamBalance())
        coEvery { transactionPage(any(), any()) } answers {
            page(history.value, firstArg(), secondArg())
        }
    }
    private val session = mockk<BeamSessionOwner.Session> {
        every { wallet } returns sdk
        every { receiveAddress } returns BeamAddress("public-test", BeamAddressType.PublicOffline, BeamNetwork.Mainnet)
    }
    private val owner = mockk<BeamSessionOwner>(relaxed = true) {
        every { current } returns session
        coEvery { withSession<List<TransactionRecord>>(session, any()) } coAnswers {
            secondArg<suspend (BeamWalletSession) -> List<TransactionRecord>>().invoke(sdk)
        }
    }

    @Test
    fun getTransactions_sparseOutgoingFilter_scansRawOffsetsAndIncludesSelf() = runTest {
        history.value = List(450) { transaction(it) }.mapIndexed { index, transaction ->
            when (index) {
                205 -> transaction.copy(direction = BeamTransactionDirection.Self)
                420 -> transaction.copy(direction = BeamTransactionDirection.Outgoing)
                else -> transaction
            }
        }
        withAdapter { adapter ->
            val records = adapter.getTransactions(null, token, 2, FilterTransactionType.Outgoing, null)
            assertEquals(listOf("0205", "0420"), records.map { it.transactionHash })
            assertTrue(records.first().sentToSelf)
            coVerify(exactly = 1) { sdk.transactionPage(0, 200) }
            coVerify(exactly = 1) { sdk.transactionPage(200, 200) }
            coVerify(exactly = 1) { sdk.transactionPage(400, 200) }
        }
    }

    @Test
    fun getTransactions_equalTimestampsAcrossPages_usesStableIdCursor() = runTest {
        history.value = List(205) { transaction(it).copy(createdAtEpochSeconds = 1000) }
        withAdapter { adapter ->
            val first = adapter.getTransactions(null, null, 2, FilterTransactionType.All, null)
            assertEquals(listOf("0204", "0203"), first.map { it.transactionHash })
            history.value = history.value.reversed().filterNot { it.id == "0203" } +
                transaction(999).copy(createdAtEpochSeconds = 1000)
            val second = adapter.getTransactions(first.last(), null, 2, FilterTransactionType.All, null)
            assertEquals(listOf("0202", "0201"), second.map { it.transactionHash })
        }
    }

    @Test
    fun getTransactions_insertAndDeleteBeforeCursor_doesNotSkipOlderRecords() = runTest {
        history.value = List(6) { transaction(it) }
        withAdapter { adapter ->
            val first = adapter.getTransactions(null, token, 3, FilterTransactionType.Incoming, null)
            history.value = listOf(transaction(99).copy(createdAtEpochSeconds = 2000)) + history.value.drop(3)
            val second = adapter.getTransactions(first.last(), token, 3, FilterTransactionType.Incoming, null)
            assertEquals(listOf("0003", "0004", "0005"), second.map { it.transactionHash })
        }
    }

    @Test
    fun getTransactions_nativeDeletionBetweenPages_usesSingleSnapshotWithoutSkippingMatch() = runTest {
        history.value = List(401) { transaction(it) }.mapIndexed { index, transaction ->
            if (index == 200) transaction.copy(direction = BeamTransactionDirection.Outgoing) else transaction
        }
        coEvery { sdk.transactionPage(200, 200) } answers { page(history.value.drop(1), 200, 200) }
        withAdapter { adapter ->
            val records = adapter.getTransactions(null, token, 1, FilterTransactionType.Outgoing, null)
            assertEquals(listOf("0200"), records.map { it.transactionHash })
        }
    }

    @Test
    fun getTransactions_largeLimitAndExhaustion_respectsSdkPageLimit() = runTest {
        history.value = List(401) { transaction(it) }
        withAdapter { adapter ->
            val first = adapter.getTransactions(null, token, 400, FilterTransactionType.All, null)
            val last = adapter.getTransactions(first.last(), token, 400, FilterTransactionType.All, null)
            assertEquals(400, first.size)
            assertEquals(listOf("0400"), last.map { it.transactionHash })
            assertTrue(adapter.getTransactions(last.last(), token, 1, FilterTransactionType.All, null).isEmpty())
            coVerify(exactly = 0) { sdk.transactionPage(any(), more(200)) }
        }
    }

    @Test
    fun getTransactions_unsupportedFiltersAndZeroLimit_returnsEmptyWithoutSdkReads() = runTest {
        history.value = listOf(transaction(0))
        val gameToken = token.copy(coin = Coin("beam-2", "Beam", "BEAM"))
        withAdapter { adapter ->
            val filters = listOf(
                Triple(token, FilterTransactionType.Swap, null),
                Triple(token, FilterTransactionType.Approve, null),
                Triple(token, FilterTransactionType.All, "contact-address"),
                Triple(gameToken, FilterTransactionType.All, null),
            )
            filters.forEach { (filterToken, type, address) ->
                assertTrue(adapter.getTransactions(null, filterToken, 1, type, address).isEmpty())
                assertTrue(adapter.getTransactionRecordsFlow(filterToken, type, address).first().isEmpty())
            }
            assertTrue(adapter.getTransactions(null, token, 0, FilterTransactionType.All, null).isEmpty())
            coVerify(exactly = 0) { sdk.transactionPage(any(), any()) }
        }
    }

    @Test
    fun transactionFlow_sameIdReorgAndDeletion_emitsUpdatedStatusAndEmptyList() = runTest {
        withAdapter { adapter ->
            val emissions = mutableListOf<List<TransactionRecord>>()
            val collector = backgroundScope.launch {
                adapter.getTransactionRecordsFlow(token, FilterTransactionType.Incoming, null)
                    .collect { emissions += it }
            }
            runCurrent()
            val confirmed = transaction(0).copy(status = BeamTransactionStatus.Completed, proofHeight = 100)
            history.value = listOf(confirmed)
            runCurrent()
            history.value = listOf(confirmed.copy(status = BeamTransactionStatus.Registering))
            runCurrent()
            history.value = emptyList()
            runCurrent()
            assertEquals(4, emissions.size)
            assertEquals(TransactionStatus.Completed, emissions[1].single().status(100))
            val reorgStatus = emissions[2].single().status(100)
            assertTrue(reorgStatus is TransactionStatus.Processing)
            assertEquals(0.5f, (reorgStatus as TransactionStatus.Processing).progress, 0f)
            assertEquals(emissions[1].single().uid, emissions[2].single().uid)
            assertTrue(emissions.last().isEmpty())
            collector.cancelAndJoin()
        }
    }

    @Test
    fun flowables_stateAndHeightReorg_emitAndStopWithAdapter() = runTest {
        withAdapter { adapter ->
            val states = adapter.transactionsStateUpdatedFlowable.test()
            val blocks = adapter.lastBlockUpdatedFlowable.test()
            runCurrent()
            state.value = BeamWalletState.Ready(100)
            runCurrent()
            assertEquals(AdapterState.Synced, adapter.transactionsState)
            assertEquals(100, adapter.lastBlockInfo?.height)
            state.value = BeamWalletState.Ready(90)
            runCurrent()
            assertEquals(90, adapter.lastBlockInfo?.height)
            blocks.assertValueCount(3)
            states.assertValueCount(2)
            adapter.stop()
            state.value = BeamWalletState.Ready(120)
            runCurrent()
            assertEquals(90, adapter.lastBlockInfo?.height)
            states.cancel()
            blocks.cancel()
        }
    }

    @Test
    fun getTransactions_cancelledRead_propagatesCancellationWithoutClosingBorrowedSession() = runTest {
        history.value = listOf(transaction(0))
        coEvery { sdk.transactionPage(any(), any()) } coAnswers { awaitCancellation() }
        withAdapter { adapter ->
            val request = async { adapter.getTransactions(null, token, 1, FilterTransactionType.All, null) }
            runCurrent()
            assertFalse(request.isCompleted)
            request.cancelAndJoin()
            assertFailsWith<CancellationException> { request.await() }
            coVerify(exactly = 0) { owner.close() }
            coVerify(exactly = 0) { sdk.close() }
        }
    }

    @Test
    fun getTransactions_sdkFailure_sanitizesErrorInsteadOfReportingEmptyHistory() = runTest {
        history.value = listOf(transaction(0))
        coEvery { sdk.transactionPage(any(), any()) } throws IllegalStateException("private-address-detail")
        withAdapter { adapter ->
            val error = assertFailsWith<IllegalStateException> {
                adapter.getTransactions(null, token, 1, FilterTransactionType.All, null)
            }
            assertEquals("BEAM transaction read failed", error.message)
            assertNull(error.cause)
        }
    }

    @Test
    fun getTransactions_adapterStoppedDuringRead_doesNotReturnLateRecordsOrCloseSession() = runTest {
        history.value = listOf(transaction(0))
        val gate = CompletableDeferred<Unit>()
        coEvery { sdk.transactionPage(any(), any()) } coAnswers {
            gate.await()
            page(history.value, firstArg(), secondArg())
        }
        withAdapter { adapter ->
            val request = async {
                assertFailsWith<IllegalStateException> {
                    adapter.getTransactions(null, token, 1, FilterTransactionType.All, null)
                }
            }
            runCurrent()
            adapter.stop()
            gate.complete(Unit)
            assertEquals("BEAM transaction read failed", request.await().message)
            coVerify(exactly = 0) { owner.close() }
            coVerify(exactly = 0) { sdk.close() }
        }
    }

    @Test
    fun lastBlockInfo_unknownOrOutOfRangeHeight_doesNotOverflow() = runTest {
        withAdapter { adapter ->
            listOf(
                BeamWalletState.Ready(-1),
                BeamWalletState.Ready(Int.MAX_VALUE.toLong() + 1),
                BeamWalletState.Offline(null),
                BeamWalletState.Connecting,
            ).forEach {
                state.value = it
                runCurrent()
                assertNull(adapter.lastBlockInfo)
            }
        }
    }

    @Test
    fun getTransactions_foreignAccountCursor_isRejected() = runTest {
        val foreignAccount = mockk<Account> { every { id } returns "another-account" }
        val converter = BeamTransactionRecordConverter(TransactionSource(blockchain, foreignAccount, null), token)
        withAdapter { adapter ->
            assertFailsWith<IllegalArgumentException> {
                adapter.getTransactions(converter.convert(transaction(0)), token, 1, FilterTransactionType.All, null)
            }
            coVerify(exactly = 0) { sdk.transactionPage(any(), any()) }
        }
    }

    private suspend fun TestScope.withAdapter(block: suspend (BeamAdapter) -> Unit) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = mockk<DispatcherProvider> { every { io } returns dispatcher }
        val adapter = BeamAdapter(owner, session, dispatchers, wallet)
        adapter.attachLocalData()
        try {
            block(adapter)
        } finally {
            adapter.stop()
        }
    }

    private fun page(items: List<BeamTransaction>, offset: Int, limit: Int): BeamTransactionPage {
        val records = items.drop(offset).take(limit)
        return BeamTransactionPage(records, (offset + records.size).takeIf { records.size == limit })
    }

    private fun transaction(index: Int) = BeamTransaction(
        id = index.toString().padStart(4, '0'),
        direction = BeamTransactionDirection.Incoming,
        amount = 100,
        fee = 1,
        createdAtEpochSeconds = 1000L - index,
        minHeight = null,
        proofHeight = null,
        kernelId = null,
        status = BeamTransactionStatus.Pending,
        failureReason = null,
    )
}
