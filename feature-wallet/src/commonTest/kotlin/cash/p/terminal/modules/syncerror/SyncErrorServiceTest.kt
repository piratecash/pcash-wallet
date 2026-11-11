package cash.p.terminal.modules.syncerror

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.tor.TorSyncState
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SyncErrorServiceTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val wallet = mockk<Wallet>(relaxed = true)
    private val adapterManager = mockk<IAdapterManager>(relaxed = true)
    private val torSyncState = mockk<TorSyncState> {
        coEvery { requestNewCircuits() } just runs
    }

    @Test
    fun retry_requestsNewCircuitsThenRefreshes() = runTest(dispatcher) {
        createService().retry()

        coVerifyOrder {
            torSyncState.requestNewCircuits()
            adapterManager.refreshByWallet(wallet)
        }
    }

    @Test
    fun retry_newCircuitsThrows_stillNoCrash() = runTest(dispatcher) {
        coEvery { torSyncState.requestNewCircuits() } throws IOException("control port closed")

        createService().retry()

        verify { adapterManager.refreshByWallet(wallet) }
    }

    // The application scope is the test scope, so an exception escaping retry() fails the test
    private fun TestScope.createService() = SyncErrorService(
        wallet = wallet,
        adapterManager = adapterManager,
        reportEmail = "support@example.com",
        btcBlockchainManager = mockk(relaxed = true),
        evmBlockchainManager = mockk(relaxed = true),
        networkErrorTracker = mockk(relaxed = true),
        systemInfoManager = mockk(relaxed = true),
        torSyncState = torSyncState,
        dispatcherProvider = TestDispatcherProvider(dispatcher, this),
    )
}
