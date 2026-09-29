package cash.p.terminal.core.notifications.polling

import cash.p.beam.BeamAddress
import cash.p.beam.BeamAddressType
import cash.p.beam.BeamBalance
import cash.p.beam.BeamNetwork
import cash.p.beam.BeamRestorePhase
import cash.p.beam.BeamRestoreProgress
import cash.p.beam.BeamTransaction
import cash.p.beam.BeamTransactionDirection
import cash.p.beam.BeamTransactionPage
import cash.p.beam.BeamTransactionStatus
import cash.p.beam.BeamWalletSession
import cash.p.beam.BeamWalletState
import cash.p.terminal.core.ITransactionsAdapter
import cash.p.terminal.core.adapters.BeamAdapter
import cash.p.terminal.core.managers.BeamLifecycleCoordinator
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.core.managers.TransactionAdapterManager
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BeamTransactionsPollerTest {
    private val blockchain = Blockchain(BlockchainType.Beam, "Beam", null)
    private val account = mockk<Account> { every { id } returns "account" }
    private val token = Token(Coin("beam", "Beam", "BEAM"), blockchain, TokenType.Native, 8)
    private val source = TransactionSource(blockchain, account, null)
    private val wallet = mockk<Wallet> {
        every { account } returns this@BeamTransactionsPollerTest.account
        every { token } returns this@BeamTransactionsPollerTest.token
        every { transactionSource } returns source
    }
    private val state = MutableStateFlow<BeamWalletState>(BeamWalletState.Stopped)
    private val background = MutableStateFlow(BackgroundManagerState.Unknown)
    private val offline = MutableStateFlow<Set<OfflineKey>>(emptySet())
    private val transactions = listOf(BeamTransaction(
        id = "transaction", direction = BeamTransactionDirection.Incoming, amount = 100, fee = 1,
        createdAtEpochSeconds = 1000, minHeight = null, proofHeight = null, kernelId = null,
        status = BeamTransactionStatus.Pending, failureReason = null,
    ))
    private val sdk = mockk<BeamWalletSession> {
        every { state } returns this@BeamTransactionsPollerTest.state
        every { balance } returns MutableStateFlow(BeamBalance())
        every { transactions } returns MutableStateFlow(this@BeamTransactionsPollerTest.transactions)
        coEvery { transactionPage(any(), any()) } returns
            BeamTransactionPage(this@BeamTransactionsPollerTest.transactions, null)
    }
    private val session = mockk<BeamSessionOwner.Session> {
        every { accountId } returns "account"
        every { wallet } returns sdk
        every { receiveAddress } returns BeamAddress("public-test", BeamAddressType.PublicOffline, BeamNetwork.Mainnet)
    }
    private val owner = mockk<BeamSessionOwner>(relaxed = true) {
        every { current } returns session
        coEvery { start(session) } coAnswers { state.value = BeamWalletState.Ready(100) }
        coEvery { withSession<List<TransactionRecord>>(session, any()) } coAnswers {
            secondArg<suspend (BeamWalletSession) -> List<TransactionRecord>>().invoke(sdk)
        }
    }
    private val adapters = MutableStateFlow<Map<TransactionSource, ITransactionsAdapter>>(emptyMap())
    private val manager = mockk<TransactionAdapterManager> { every { adaptersReadyFlow } returns adapters }
    private val poller = BeamTransactionsPoller(manager)

    @Test
    fun pollOnce_noActiveAdapter_returnsEmpty() = runTest {
        assertTrue(poller.pollOnce(listOf(wallet)).isEmpty())
        coVerify(exactly = 0) { owner.start(any()) }
    }

    @Test
    fun pollOnce_unknownColdStart_returnsCurrentAccountSdkRecordsAndReleasesLease() = runTest {
        withAdapter { adapter ->
            val records = poller.pollOnce(listOf(wallet, wallet))
            assertEquals(listOf("transaction"), records.map { it.transactionHash })
            assertEquals(source, records.single().source)
            coVerify(exactly = 1) { owner.start(session) }
            assertTrue(adapter.isNetworkPaused)
        }
    }

    @Test
    fun pollOnce_waitsForMatchingAdapterPublication() = runTest {
        withAdapter(publish = false) { adapter ->
            val worker = async { poller.pollOnce(listOf(wallet)) }
            runCurrent()
            assertTrue(worker.isActive)
            adapters.value = mapOf(source to adapter)
            assertEquals(listOf("transaction"), worker.await().map { it.transactionHash })
        }
    }

    @Test
    fun pollOnce_restoreInProgress_returnsEmptyWithoutSecondSync() = runTest {
        state.value = BeamWalletState.Restoring(BeamRestoreProgress(BeamRestorePhase.DownloadingSnapshot))
        withAdapter {
            assertTrue(poller.pollOnce(listOf(wallet)).isEmpty())
            coVerify(exactly = 0) { owner.start(any()) }
            coVerify(exactly = 0) { owner.stop(any()) }
        }
    }

    @Test
    fun pollOnce_manualOffline_evenCachedReadyStateCannotReturnRecords() = runTest {
        offline.value = setOf(OfflineKey("account", BlockchainType.Beam))
        state.value = BeamWalletState.Ready(100)
        withAdapter {
            assertTrue(poller.pollOnce(listOf(wallet)).isEmpty())
            coVerify(exactly = 0) { owner.start(any()) }
            coVerify(exactly = 0) { sdk.transactionPage(any(), any()) }
        }
    }

    @Test
    fun pollOnce_workerCancelledDuringSync_releasesLeaseAndStopsNetwork() = runTest {
        coEvery { owner.start(session) } coAnswers { state.value = BeamWalletState.Syncing(1, 100) }
        withAdapter { adapter ->
            val worker = async { poller.pollOnce(listOf(wallet)) }
            runCurrent()
            background.value = BackgroundManagerState.AllActivitiesDestroyed
            runCurrent()
            assertTrue(worker.isActive)
            worker.cancelAndJoin()
            assertTrue(adapter.isNetworkPaused)
        }
    }

    @Test
    fun pollOnce_accountReplaced_doesNotStartOrReadStaleSession() = runTest {
        withAdapter {
            every { owner.current } returns mockk()
            assertTrue(poller.pollOnce(listOf(wallet)).isEmpty())
            coVerify(exactly = 0) { owner.start(any()) }
            coVerify(exactly = 0) { sdk.transactionPage(any(), any()) }
        }
    }

    @Test
    fun pollOnce_adapterStopped_cancelsWaitingWorkerAndIgnoresLateReady() = runTest {
        coEvery { owner.start(session) } coAnswers { state.value = BeamWalletState.Syncing(1, 100) }
        withAdapter { adapter ->
            val worker = async { poller.pollOnce(listOf(wallet)) }
            runCurrent()
            adapter.stop()
            state.value = BeamWalletState.Ready(100)
            runCurrent()
            assertTrue(worker.isCancelled)
            coVerify(exactly = 0) { sdk.transactionPage(any(), any()) }
        }
    }

    @Test
    fun refresh_unknownWithoutLease_doesNotStartNetwork() = runTest {
        withAdapter { adapter ->
            adapter.refresh()
            runCurrent()
            coVerify(exactly = 0) { owner.start(any()) }
        }
    }

    private suspend fun TestScope.withAdapter(
        publish: Boolean = true,
        block: suspend (BeamAdapter) -> Unit,
    ) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val lifecycle = BeamLifecycleCoordinator(
            mockk { every { stateFlow } returns background },
            mockk { every { keepAliveBlockchains } returns MutableStateFlow(emptySet()) },
            mockk {
                every { isConnected } returns MutableStateFlow(true)
                every { acquireMonitoringLease() } returns AutoCloseable { }
                coEvery { refreshAndAwaitValidation() } returns true
            },
            mockk {
                every { effectiveFlow } returns offline
                every { stateFlow } returns MutableStateFlow(emptyMap())
                every { isNetworkPaused(any()) } answers { firstArg<OfflineKey>() in offline.value }
            },
        )
        val dispatchers = mockk<DispatcherProvider> { every { io } returns dispatcher }
        val adapter = BeamAdapter(owner, session, dispatchers, wallet, lifecycle)
        if (publish) adapters.value = mapOf(source to adapter)
        try {
            block(adapter)
        } finally {
            adapter.close()
        }
    }
}
