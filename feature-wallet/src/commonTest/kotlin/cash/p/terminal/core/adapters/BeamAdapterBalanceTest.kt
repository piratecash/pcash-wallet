package cash.p.terminal.core.adapters

import cash.p.beam.BeamAddress
import cash.p.beam.BeamAddressType
import cash.p.beam.BeamBalance
import cash.p.beam.BeamNetwork
import cash.p.beam.BeamWalletSession
import cash.p.beam.BeamWalletState
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.wallet.AdapterState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/**
 * What the balance row shows while a BEAM wallet is not yet authoritative, and what the send
 * flows are allowed to treat as spendable at the same moment. Kept apart from [BeamAdapterTest]
 * the way [BeamAdapterTransactionsTest] is.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BeamAdapterBalanceTest {
    private val sdkState = MutableStateFlow<BeamWalletState>(BeamWalletState.Stopped)
    private val sdkBalance = MutableStateFlow(BeamBalance())
    private val sdk = mockk<BeamWalletSession> {
        every { state } returns sdkState
        every { balance } returns sdkBalance
        every { transactions } returns MutableStateFlow(emptyList())
    }
    private val session = mockk<BeamSessionOwner.Session> {
        every { accountId } returns "beam-account"
        every { wallet } returns sdk
        every { receiveAddress } returns
            BeamAddress("public-offline-test", BeamAddressType.PublicOffline, BeamNetwork.Mainnet)
    }
    private val owner = mockk<BeamSessionOwner>(relaxed = true) {
        every { current } returns session
    }

    @Test
    fun balanceData_adapterBuiltOverALoadedSession_showsTheAmountsBeforeAnySnapshotArrives() = runTest {
        // An adapter created for a session that already read the wallet database must not start
        // the row at zero and wait for the next emission.
        sdkBalance.value = BeamBalance(available = 4200, loadedFromDatabase = true)
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            assertEquals(BigDecimal("0.00004200"), adapter.balanceData.available)
            assertEquals(null, adapter.spendableBalanceData)
        } finally {
            adapter.close()
        }
    }

    @Test
    fun balanceData_loadedFromDatabaseOnly_isDisplayedWhileNothingIsSpendable() = runTest {
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            adapter.attachLocalData()
            runCurrent()
            sdkBalance.value = BeamBalance(available = 4200, receiving = 7, loadedFromDatabase = true)
            runCurrent()

            // R1: the row shows what the wallet database knows.
            assertEquals(BigDecimal("0.00004200"), adapter.balanceData.available)
            assertEquals(BigDecimal("0.00000007"), adapter.balanceData.pending)
            // R3: nothing of it may fund a send until the chain has confirmed it.
            assertEquals(null, adapter.spendableBalanceData)
            assertEquals(0, BigDecimal.ZERO.compareTo(adapter.maxSpendableBalance))
        } finally {
            adapter.close()
        }
    }

    @Test
    fun maxSpendableBalance_authoritativeThenSyncingAgain_isClearedNotKept() = runTest {
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            adapter.attachLocalData()
            runCurrent()
            sdkBalance.value = BeamBalance(available = 4200, loadedFromDatabase = true, isAuthoritative = true)
            runCurrent()
            assertEquals(BigDecimal("0.00004200"), adapter.maxSpendableBalance)

            // Ready -> Syncing: the chain may since have invalidated these coins.
            sdkBalance.value = BeamBalance(available = 4200, loadedFromDatabase = true)
            runCurrent()

            assertEquals(BigDecimal("0.00004200"), adapter.balanceData.available)
            assertEquals(null, adapter.spendableBalanceData)
            assertEquals(0, BigDecimal.ZERO.compareTo(adapter.maxSpendableBalance))
        } finally {
            adapter.close()
        }
    }

    @Test
    fun maxSpendableBalance_authoritativeThenASnapshotThatReadNothing_isClearedNotKept() = runTest {
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            adapter.attachLocalData()
            runCurrent()
            sdkBalance.value = BeamBalance(available = 4200, loadedFromDatabase = true, isAuthoritative = true)
            runCurrent()
            assertEquals(BigDecimal("0.00004200"), adapter.maxSpendableBalance)

            // What the session republishes once it stops: the native reset zeroes the snapshot and
            // clears the database flag, so the balance is neither loaded nor authoritative.
            sdkBalance.value = BeamBalance()
            runCurrent()

            // The row keeps the last amounts the database reported rather than dropping to zero...
            assertEquals(BigDecimal("0.00004200"), adapter.balanceData.available)
            // ...but a stopped wallet holds nothing the chain has confirmed, so nothing is spendable.
            assertEquals(null, adapter.spendableBalanceData)
            assertEquals(0, BigDecimal.ZERO.compareTo(adapter.maxSpendableBalance))
        } finally {
            adapter.close()
        }
    }

    @Test
    fun balanceState_syncingWithNodeCounters_reportsPercentageWithoutABlockBacklog() = runTest {
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            adapter.attachLocalData()
            runCurrent()
            sdkState.value = BeamWalletState.Syncing(
                currentHeight = 10,
                targetHeight = 1000,
                syncDone = 3,
                syncTotal = 4,
            )
            runCurrent()

            val state = adapter.balanceState as AdapterState.Syncing
            assertEquals(75.0, requireNotNull(state.progress), 0.0001)
            // A backlog would send the status text to the "N blocks left" branch instead.
            assertEquals(null, state.blocksRemained)
        } finally {
            adapter.close()
        }
    }

    @Test
    fun balanceState_syncingWithoutNodeCounters_keepsTheHeightRatioAndBacklog() = runTest {
        val adapter = adapter(StandardTestDispatcher(testScheduler))
        try {
            adapter.attachLocalData()
            runCurrent()
            // An older native library reports no counters.
            sdkState.value = BeamWalletState.Syncing(currentHeight = 250, targetHeight = 1000)
            runCurrent()

            val state = adapter.balanceState as AdapterState.Syncing
            assertEquals(25.0, requireNotNull(state.progress), 0.0001)
            assertEquals(750L, state.blocksRemained)
        } finally {
            adapter.close()
        }
    }

    private fun adapter(dispatcher: CoroutineDispatcher) = testBeamAdapter(owner, session, dispatcher)
}
