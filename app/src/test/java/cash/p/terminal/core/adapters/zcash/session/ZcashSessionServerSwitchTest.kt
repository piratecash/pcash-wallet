package cash.p.terminal.core.adapters.zcash.session

import cash.p.zcash.MempoolEvent
import cash.p.zcash.ServerConfig
import cash.p.zcash.SyncState
import cash.p.zcash.ZcashException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private const val OLD_URL = "https://zec.rocks:443"
private val NEW_SERVER = ServerConfig(url = "https://eu.zec.rocks:443")

@OptIn(ExperimentalCoroutinesApi::class)
class ZcashSessionServerSwitchTest {

    private val mempoolEvents = MutableSharedFlow<MempoolEvent>(extraBufferCapacity = 8)

    private val wallet = zcashWalletMock(mempoolEvents).apply {
        every { sync(any()) } returns flow {
            emit(SyncState.Connecting)
            awaitCancellation()
        }
    }

    private fun TestScope.session(networkPaused: Boolean = false) =
        zcashSession(wallet, networkPaused = networkPaused)

    @Test
    fun switchServer_syncRunning_setsTheServerBeforeCancellingTheSync() = runTest {
        val session = session()
        val pass = async { session.sync() }
        runCurrent()

        session.switchServer(NEW_SERVER)

        coVerifyOrder {
            wallet.setServer(NEW_SERVER)
            wallet.cancelSync()
        }
        assertEquals(ZcashSessionResult.Success(ZcashPassOutcome.Cancelled), pass.await())
    }

    @Test
    fun switchServer_mempoolSubscribed_restartsTheSubscription() = runTest {
        val session = session()
        advanceUntilIdle()

        session.switchServer(NEW_SERVER)
        advanceUntilIdle()

        verify(exactly = 2) { wallet.mempool() }
        assertEquals(1, mempoolEvents.subscriptionCount.value)
        session.drain(DRAIN_TIMEOUT_MS)
    }

    @Test
    fun switchServer_networkPaused_keepsTheMempoolOff() = runTest {
        val session = session(networkPaused = true)
        advanceUntilIdle()

        session.switchServer(NEW_SERVER)
        advanceUntilIdle()

        verify(exactly = 0) { wallet.mempool() }
    }

    @Test
    fun switchServer_succeeds_reportsTheNewUrl() = runTest {
        val session = session()

        session.switchServer(NEW_SERVER)

        assertEquals(NEW_SERVER.url, session.serverUrl)
        session.drain(DRAIN_TIMEOUT_MS)
    }

    @Test
    fun switchServer_setServerFails_keepsTheOldUrlAndTheSyncRunning() = runTest {
        coEvery { wallet.setServer(any()) } throws ZcashException("invalid server url")
        val session = session()
        val pass = async { session.sync() }
        runCurrent()

        session.switchServer(NEW_SERVER)
        runCurrent()

        assertEquals(OLD_URL, session.serverUrl)
        assertFalse(pass.isCompleted)
        assertEquals(SyncState.Connecting, session.state.value.syncState)
        coVerify(exactly = 0) { wallet.cancelSync() }
        verify(exactly = 1) { wallet.mempool() }
        session.cancelSync()
    }

    @Test
    fun sync_entersWhileTheSwitchRestartsTheMempool_reportsThePassOnTheNewServer() = runTest {
        val oldStreamStopped = CompletableDeferred<Unit>()
        every { wallet.mempool() } returns flow {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { oldStreamStopped.await() }
            }
        }
        every { wallet.sync(any()) } returns flow { throw IOException("unexpected EOF") }
        val session = session()
        advanceUntilIdle()
        val switch = async { session.switchServer(NEW_SERVER) }
        runCurrent() // the new server is set; the switch now waits for the old stream to stop

        val result = session.sync()

        assertTrue((result as ZcashSessionResult.Success).value is ZcashPassOutcome.Failed)
        assertEquals(NEW_SERVER.url, session.passServerUrl)
        oldStreamStopped.complete(Unit)
        switch.await()
        session.drain(DRAIN_TIMEOUT_MS)
    }

    private companion object {
        const val DRAIN_TIMEOUT_MS = 30_000L
    }
}
