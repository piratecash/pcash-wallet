package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.MoneroKitManager
import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.core.managers.OfflineNetworkController
import cash.p.terminal.core.managers.SolanaKitManager
import cash.p.terminal.core.managers.StellarKitManager
import cash.p.terminal.core.managers.TonKitManager
import cash.p.terminal.core.managers.TronKitManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException
import kotlin.test.assertFailsWith

/**
 * Offline path of [ZcashAdapter]: pausing stops the sync only, so the session stays open and the
 * local data readable, and going back online reuses the session instead of reopening the wallet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZcashAdapterOfflineTest : ZcashAdapterTestFixture() {

    private val offlineKey = OfflineKey(ACCOUNT_ID, BlockchainType.Zcash)

    @Test
    fun pauseNetwork_cancelsSyncWithoutReleasingTheSession() = runTest(dispatcher) {
        adapter = createAdapter()
        adapter.start()
        advanceUntilIdle()

        adapter.pauseNetwork()
        advanceUntilIdle()

        coVerify(exactly = 1) { session.cancelSync() }
        coVerify(exactly = 0) { sessionManager.release(any()) }
    }

    @Test
    fun pauseController_gatedSession_doesNotCompleteBeforeCancelFinishes() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        coEvery { session.cancelSync() } coAnswers { gate.await() }
        adapter = createAdapter()
        adapter.start()
        advanceUntilIdle()
        val controller = controller()

        val pause = async { controller.pause(wallet) }
        runCurrent()

        assertFalse(pause.isCompleted)
        coVerify(exactly = 1) { session.cancelSync() }
        gate.complete(Unit)
        pause.await()
    }

    @Test
    fun pauseController_sessionThrows_propagatesFailure() = runTest(dispatcher) {
        coEvery { session.cancelSync() } throws IOException("pause failed")
        adapter = createAdapter()
        adapter.start()
        advanceUntilIdle()

        assertFailsWith<IOException> { controller().pause(wallet) }
        coVerify(exactly = 1) { session.cancelSync() }
    }

    // The manager still reports paused here on purpose: beginTransition() holds that flag for the
    // whole go-online transition, so a resume that consulted it would never lift its own pause.
    @Test
    fun resumeNetwork_transitionInFlight_reusesTheOpenSession() = runTest(dispatcher) {
        pauseNetwork()
        adapter = createAdapter()
        adapter.start()
        advanceUntilIdle()

        adapter.resumeNetwork()
        advanceUntilIdle()

        coVerify(exactly = 1) { sessionManager.acquire(wallet) }
    }

    @Test
    fun startForPolling_networkPaused_doesNotReopenTheWallet() = runTest(dispatcher) {
        pauseNetwork()
        adapter = createAdapter()
        adapter.start()
        advanceUntilIdle()

        adapter.startForPolling()
        advanceUntilIdle()

        coVerify(exactly = 1) { sessionManager.acquire(wallet) }
    }

    // The manager decides `networkPaused` when it opens the wallet; a pause arriving during those
    // seconds finds no session to stop, so the adapter re-reads the flag once the session exists.
    @Test
    fun attachLocalData_pausedWhileTheWalletOpens_pausesTheMempoolAfterwards() = runTest(dispatcher) {
        val opened = CompletableDeferred<Unit>()
        coEvery { sessionManager.acquire(wallet) } coAnswers {
            opened.await()
            session
        }
        adapter = createAdapter()
        adapter.start()
        runCurrent()

        pauseNetwork()
        opened.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { session.pauseMempool() }
    }

    private fun pauseNetwork() {
        every { offlineModeManager.isNetworkPaused(offlineKey) } returns true
    }

    private fun controller(): OfflineNetworkController {
        every { wallet.token } returns mockk<Token> {
            every { blockchainType } returns BlockchainType.Zcash
        }
        val adapterManager = mockk<IAdapterManager> {
            every { getAdapterForWalletOld(wallet) } returns adapter
        }
        return OfflineNetworkController(
            adapterManager,
            mockk<EvmBlockchainManager>(relaxed = true),
            mockk<SolanaKitManager>(relaxed = true),
            mockk<TronKitManager>(relaxed = true),
            mockk<TonKitManager>(relaxed = true),
            mockk<StellarKitManager>(relaxed = true),
            mockk<MoneroKitManager>(relaxed = true),
        )
    }
}
