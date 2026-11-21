package cash.p.terminal.core.tor

import cash.p.terminal.core.ITorManager
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.modules.settings.security.tor.TorStatus
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.AdapterStoppedException
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TorSyncCoordinatorTest {

    private val dispatcher = StandardTestDispatcher()
    private val torStatus = MutableStateFlow(TorStatus.Closed)
    private val torManager = mockk<ITorManager> {
        every { isTorEnabled } returns true
        every { torStatusFlow } returns torStatus
        coEvery { newCircuits() } just runs
    }
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val walletManager = mockk<IWalletManager>()
    private val offlineModeManager = mockk<OfflineModeManager> {
        every { isNetworkPaused(any<OfflineKey>()) } returns false
    }

    @Test
    fun connected_refreshesAllAdaptersUnattended() = runTest(dispatcher) {
        givenWallets()
        val coordinator = createCoordinator()

        torStatus.value = TorStatus.Connected
        runCurrent()

        coVerify(exactly = 1) { adapterManager.refresh(attended = false) }
        assertTrue(coordinator.syncErrorsHidden.value)
    }

    @Test
    fun connected_afterWarmUp_failedWalletsGetNewCircuitsAndRefresh() = runTest(dispatcher) {
        val failed = wallet(AdapterState.NotSynced(IOException("connection refused")))
        val synced = wallet(AdapterState.Synced)
        givenWallets(failed, synced)
        val coordinator = createCoordinator()

        torStatus.value = TorStatus.Connected
        advanceTimeBy(WARM_UP_MILLIS - 1)
        coVerify(exactly = 0) { torManager.newCircuits() }

        advanceTimeBy(1)
        runCurrent()
        coVerify(exactly = 1) { torManager.newCircuits() }
        verify(exactly = 1) { adapterManager.refreshByWallet(failed, attended = false) }
        verify(exactly = 0) { adapterManager.refreshByWallet(synced, any()) }
        assertTrue(coordinator.syncErrorsHidden.value)

        advanceTimeBy(RETRY_SETTLE_MILLIS)
        runCurrent()
        assertFalse(coordinator.syncErrorsHidden.value)
    }

    @Test
    fun connected_refreshStillRunning_warmUpRetriesAndUnmasksOnTime() = runTest(dispatcher) {
        val failed = wallet(AdapterState.NotSynced(IOException("connection refused")))
        givenWallets(failed)
        coEvery { adapterManager.refresh(attended = false) } coAnswers { awaitCancellation() }
        val coordinator = createCoordinator()

        torStatus.value = TorStatus.Connected
        advanceTimeBy(WARM_UP_MILLIS)
        runCurrent()
        verify(exactly = 1) { adapterManager.refreshByWallet(failed, attended = false) }

        advanceTimeBy(RETRY_SETTLE_MILLIS)
        runCurrent()
        assertFalse(coordinator.syncErrorsHidden.value)
    }

    @Test
    fun connected_noFailedWallets_noNewCircuits() = runTest(dispatcher) {
        givenWallets(wallet(AdapterState.Synced), wallet(AdapterState.Syncing()))
        val coordinator = createCoordinator()

        torStatus.value = TorStatus.Connected
        advanceTimeBy(WARM_UP_MILLIS)
        runCurrent()

        coVerify(exactly = 0) { torManager.newCircuits() }
        verify(exactly = 0) { adapterManager.refreshByWallet(any(), any()) }
        assertFalse(coordinator.syncErrorsHidden.value)
    }

    @Test
    fun refreshThrows_laterConnectedStillRecovers() = runTest(dispatcher) {
        val failed = wallet(AdapterState.NotSynced(IOException("connection refused")))
        givenWallets(failed)
        coEvery { adapterManager.refresh(attended = false) } throws IOException("kit refresh failed")
        val coordinator = createCoordinator()

        torStatus.value = TorStatus.Connected
        runCurrent()
        assertFalse(coordinator.syncErrorsHidden.value)

        coEvery { adapterManager.refresh(attended = false) } just runs
        torStatus.value = TorStatus.Connecting
        runCurrent()
        torStatus.value = TorStatus.Connected
        advanceTimeBy(WARM_UP_MILLIS)
        runCurrent()

        coVerify(exactly = 2) { adapterManager.refresh(attended = false) }
        verify(exactly = 1) { adapterManager.refreshByWallet(failed, attended = false) }
    }

    @Test
    fun connecting_hidesErrors_failed_showsErrors() = runTest(dispatcher) {
        givenWallets()
        val coordinator = createCoordinator()

        torStatus.value = TorStatus.Connecting
        runCurrent()
        assertTrue(coordinator.syncErrorsHidden.value)

        torStatus.value = TorStatus.Failed
        runCurrent()
        assertFalse(coordinator.syncErrorsHidden.value)
    }

    @Test
    fun connectingDuringWarmUp_cancelsRetry() = runTest(dispatcher) {
        givenWallets(wallet(AdapterState.NotSynced(IOException("connection refused"))))
        val coordinator = createCoordinator()

        torStatus.value = TorStatus.Connected
        advanceTimeBy(WARM_UP_MILLIS / 2)
        torStatus.value = TorStatus.Connecting
        advanceTimeBy(WARM_UP_MILLIS)
        runCurrent()

        coVerify(exactly = 0) { torManager.newCircuits() }
        verify(exactly = 0) { adapterManager.refreshByWallet(any(), any()) }
        assertTrue(coordinator.syncErrorsHidden.value)
    }

    @Test
    fun stoppedAdapter_notRetried() = runTest(dispatcher) {
        givenWallets(wallet(AdapterState.NotSynced(AdapterStoppedException())))
        createCoordinator()

        torStatus.value = TorStatus.Connected
        advanceTimeBy(WARM_UP_MILLIS)
        runCurrent()

        coVerify(exactly = 0) { torManager.newCircuits() }
        verify(exactly = 0) { adapterManager.refreshByWallet(any(), any()) }
    }

    @Test
    fun pausedWallet_notRetried() = runTest(dispatcher) {
        givenWallets(wallet(AdapterState.NotSynced(IOException("connection refused")), accountId = PAUSED_ACCOUNT))
        every { offlineModeManager.isNetworkPaused(OfflineKey(PAUSED_ACCOUNT, BlockchainType.Bitcoin)) } returns true
        createCoordinator()

        torStatus.value = TorStatus.Connected
        advanceTimeBy(WARM_UP_MILLIS)
        runCurrent()

        coVerify(exactly = 0) { torManager.newCircuits() }
        verify(exactly = 0) { adapterManager.refreshByWallet(any(), any()) }
    }

    @Test
    fun torDisabled_connecting_keepsErrorsVisible() = runTest(dispatcher) {
        every { torManager.isTorEnabled } returns false
        givenWallets()
        val coordinator = createCoordinator()

        torStatus.value = TorStatus.Connecting
        runCurrent()
        assertFalse(coordinator.syncErrorsHidden.value)

        torStatus.value = TorStatus.Connected
        runCurrent()
        coVerify(exactly = 0) { adapterManager.refresh(attended = false) }
    }

    private fun givenWallets(vararg wallets: Wallet) {
        every { walletManager.activeWallets } returns wallets.toList()
    }

    private fun wallet(state: AdapterState, accountId: String = "account"): Wallet {
        val wallet = mockk<Wallet> {
            every { account.id } returns accountId
            every { token.blockchainType } returns BlockchainType.Bitcoin
        }
        every { adapterManager.getBalanceAdapterForWallet(wallet) } returns mockk {
            every { balanceState } returns state
        }
        return wallet
    }

    private fun TestScope.createCoordinator() = TorSyncCoordinator(
        torManager,
        lazyOf(adapterManager),
        lazyOf(walletManager),
        lazyOf(offlineModeManager),
        TestDispatcherProvider(dispatcher, this),
    )

    private companion object {
        const val WARM_UP_MILLIS = 20_000L
        const val RETRY_SETTLE_MILLIS = 10_000L
        const val PAUSED_ACCOUNT = "paused"
    }
}
