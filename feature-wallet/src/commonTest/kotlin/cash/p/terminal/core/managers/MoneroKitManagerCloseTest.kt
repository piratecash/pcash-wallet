package cash.p.terminal.core.managers

import io.horizontalsystems.core.BackgroundManagerState
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.test.assertFailsWith

/** Switching accounts closes the old native wallet; the close outlives a cancelled caller. */
@OptIn(ExperimentalCoroutinesApi::class)
class MoneroKitManagerCloseTest : MoneroKitManagerTestFixture() {
    private val otherAccount = account.copy(id = "other-account-id")

    @Test
    fun getMoneroKitWrapper_callerCancelledWhileOldWalletCloses_nextSwitchWaitsForClose() = testScope.runTest {
        val closing = CompletableDeferred<Unit>()
        val next = createWrapper()
        val manager = switchingManager(next) { closing.await() }

        async { manager.getMoneroKitWrapper(otherAccount) }.cancel()
        val switching = async { manager.getMoneroKitWrapper(otherAccount) }

        assertFalse(switching.isCompleted)
        coVerify(exactly = 0) { next.start() }
        closing.complete(Unit)
        assertSame(next, switching.await())
        coVerify(exactly = 1) { mockWrapper.close() }
    }

    @Test
    fun getMoneroKitWrapper_switchBackWhileOldWalletCloses_reopensWithNewWrapper() = testScope.runTest {
        val closing = CompletableDeferred<Unit>()
        val reopened = createWrapper()
        val manager = switchingManager(reopened) { closing.await() }

        async { manager.getMoneroKitWrapper(otherAccount) }.cancel()
        val back = async { manager.getMoneroKitWrapper(account) }

        assertFalse(back.isCompleted)
        closing.complete(Unit)
        assertSame(reopened, back.await())
    }

    @Test
    fun getMoneroKitWrapper_closeEndsAsCallerIsCancelled_switchBackOpensNewWrapper() = testScope.runTest {
        val closing = CompletableDeferred<Unit>()
        val reopened = createWrapper()
        val manager = switchingManager(reopened) { closing.await() }
        val switching = launch(StandardTestDispatcher(testScheduler)) { manager.getMoneroKitWrapper(otherAccount) }
        runCurrent()

        // The close ends while the caller's resumption is still queued.
        closing.complete(Unit)
        switching.cancel()

        assertSame(reopened, manager.getMoneroKitWrapper(account))
    }

    @Test
    fun startForPolling_switchWhileOldWalletResumes_closesItOnlyAfterResume() = testScope.runTest {
        val resumed = CompletableDeferred<Boolean>()
        coEvery { mockWrapper.resume() } coAnswers { resumed.await() }
        val manager = switchingManager(createWrapper()) {}

        launch { manager.startForPolling() }
        val switching = async { manager.getMoneroKitWrapper(otherAccount) }

        coVerify(exactly = 0) { mockWrapper.close() }
        resumed.complete(true)
        switching.await()
        coVerify(exactly = 1) { mockWrapper.close() }
    }

    @Test
    fun getMoneroKitWrapper_oldWalletCloseFails_keepsOldAccountAndNextSwitchRetriesClose() = testScope.runTest {
        var closeAttempts = 0
        val next = createWrapper()
        val manager = switchingManager(next) { if (++closeAttempts == 1) error("close failed") }

        assertFailsWith<IllegalStateException> { manager.getMoneroKitWrapper(otherAccount) }
        assertSame(account, manager.currentAccount)
        clearMocks(mockWrapper, answers = false)
        networkAvailability.emit(true)
        coVerify(exactly = 1) { mockWrapper.refresh() }

        assertSame(next, manager.getMoneroKitWrapper(otherAccount))
        assertEquals(2, closeAttempts)
    }

    // The open wallet belongs to [account]; [close] stands in for its native close.
    private fun switchingManager(next: MoneroKitWrapper, close: suspend () -> Unit): MoneroKitManager {
        coEvery { mockWrapper.close() } coAnswers { close() }
        return createKitInstanceSpy(MutableStateFlow(BackgroundManagerState.EnterForeground), next).also {
            it.moneroKitWrapper = mockWrapper
            setField(it, "currentAccount", account)
        }
    }
}
