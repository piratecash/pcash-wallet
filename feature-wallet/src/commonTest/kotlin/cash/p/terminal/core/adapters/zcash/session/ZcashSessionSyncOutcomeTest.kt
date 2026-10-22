package cash.p.terminal.core.adapters.zcash.session

import cash.p.terminal.core.managers.NetworkErrorTracker
import cash.p.zcash.SyncState
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private const val CURRENT_HEIGHT = 2_500_000
private const val TARGET_HEIGHT = CURRENT_HEIGHT + 100

class ZcashSessionSyncOutcomeTest {

    private val wallet = zcashWalletMock()

    private val networkErrorTracker = mockk<NetworkErrorTracker>(relaxed = true)

    private fun TestScope.session() = zcashSession(wallet, networkErrorTracker = networkErrorTracker)

    private fun ZcashSessionResult<ZcashPassOutcome>.failure() =
        (this as ZcashSessionResult.Success).value as ZcashPassOutcome.Failed

    @Test
    fun sync_passReachesSynced_returnsSynced() = runTest {
        every { wallet.sync(any()) } returns flowOf(SyncState.Synced)

        assertEquals(ZcashSessionResult.Success(ZcashPassOutcome.Synced), session().sync())
    }

    @Test
    fun sync_cancelledBeforeTheEnd_returnsCancelled() = runTest {
        every { wallet.sync(any()) } returns flow {
            emit(SyncState.Connecting)
            awaitCancellation()
        }
        val session = session()
        val pass = async { session.sync() }
        runCurrent()

        session.cancelSync()

        assertEquals(ZcashSessionResult.Success(ZcashPassOutcome.Cancelled), pass.await())
    }

    @Test
    fun sync_firstNetworkFailure_keepsTheLastStateAndRecordsTheError() = runTest {
        val progress = SyncState.Syncing(current = CURRENT_HEIGHT, target = TARGET_HEIGHT)
        val error = IOException("unexpected EOF")
        every { wallet.sync(any()) } returns flow {
            emit(progress)
            throw error
        }
        val session = session()
        val seen = mutableListOf<SyncState>()
        backgroundScope.launch { session.state.collect { seen += it.syncState } }
        runCurrent()

        val result = session.sync()
        runCurrent()

        assertEquals(ZcashSessionResult.Success(ZcashPassOutcome.Failed(error, published = false)), result)
        assertEquals(progress, session.state.value.syncState)
        assertTrue("$seen", seen.none { it is SyncState.Failed })
        verify(exactly = 1) { networkErrorTracker.record(BlockchainType.Zcash, "account", any()) }
    }

    @Test
    fun sync_secondConsecutiveNetworkFailure_publishesTheFailure() = runTest {
        val error = IOException("unexpected EOF")
        every { wallet.sync(any()) } returns flow { throw error }
        val session = session()
        session.sync()

        val result = session.sync()

        assertEquals(ZcashSessionResult.Success(ZcashPassOutcome.Failed(error, published = true)), result)
        assertEquals(SyncState.Failed(error), session.state.value.syncState)
    }

    @Test
    fun sync_syncedBetweenNetworkFailures_resetsTheStreak() = runTest {
        val failing = flow<SyncState> { throw IOException("unexpected EOF") }
        every { wallet.sync(any()) } returnsMany listOf(failing, flowOf(SyncState.Synced), failing)
        val session = session()
        session.sync()
        session.sync()

        val result = session.sync()

        assertFalse(result.failure().published)
        assertEquals(SyncState.Connecting, session.state.value.syncState)
    }

    @Test
    fun sync_otherFailureBetweenNetworkFailures_resetsTheStreak() = runTest {
        val failing = flow<SyncState> { throw IOException("unexpected EOF") }
        val bug = flow<SyncState> { throw IllegalStateException("bug") }
        every { wallet.sync(any()) } returnsMany listOf(failing, bug, failing)
        val session = session()
        session.sync()
        val other = session.sync()

        val result = session.sync()

        assertTrue(other.failure().published)
        assertFalse(result.failure().published)
    }
}
