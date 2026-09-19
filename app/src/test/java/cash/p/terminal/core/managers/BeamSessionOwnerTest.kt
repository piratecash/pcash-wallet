package cash.p.terminal.core.managers

import cash.p.beam.BeamAddress
import cash.p.beam.BeamAddressType
import cash.p.beam.BeamWalletSession
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith
import cash.p.beam.BeamNetwork as SdkBeamNetwork

@OptIn(ExperimentalCoroutinesApi::class)
class BeamSessionOwnerTest {
    private val factory = mockk<BeamSessionFactory>(relaxUnitFun = true)
    private val owner = BeamSessionOwner(factory)
    private val events = mutableListOf<String>()
    private val first = account("first")
    private val second = account("second")

    @Test
    fun drainDeleted_inflightNativeWork_finishesBeforeCloseAndRejectsStaleAcquisition() = runTest {
        val wallet = wallet(first)
        val session = owner.acquire(first)
        val finishNative = CompletableDeferred<Unit>()
        val operation = async {
            assertFailsWith<CancellationException> {
                owner.withSession(session) { finishNative.await() }
            }
        }
        runCurrent()
        coEvery { factory.ensureAvailable(first.id) } throws IllegalStateException("deletion pending")
        owner.fenceDeletion(listOf(first.id))
        val drain = async { owner.drainDeleted(listOf(first.id)) }
        runCurrent()

        assertFalse(drain.isCompleted)
        assertNull(owner.current)
        assertFailsWith<IllegalStateException> { owner.acquire(first) }
        coVerify(exactly = 0) { wallet.close() }
        finishNative.complete(Unit)
        operation.await()
        drain.await()

        coVerify(exactly = 1) { wallet.close() }
        coVerify(exactly = 1) { factory.open(first, BeamNetwork.Mainnet) }
    }

    @Test
    fun drainDeleted_startupRunning_cancelsAndAwaitsBeforeClosing() = runTest {
        val wallet = wallet(first)
        val session = owner.acquire(first)
        val cleanup = CompletableDeferred<Unit>()
        coEvery { wallet.start() } coAnswers {
            try {
                CompletableDeferred<Unit>().await()
            } finally {
                withContext(NonCancellable) { cleanup.await() }
            }
        }
        val startup = async { assertFailsWith<CancellationException> { owner.start(session) } }
        runCurrent()
        owner.fenceDeletion(listOf(first.id))
        val drain = async { owner.drainDeleted(listOf(first.id)) }
        runCurrent()

        assertFalse(drain.isCompleted)
        coVerify(exactly = 0) { wallet.close() }
        cleanup.complete(Unit)
        startup.await()
        drain.await()
        coVerify(exactly = 1) { wallet.close() }
    }

    @Test
    fun drainDeleted_siblingOwnsSession_leavesSiblingOpen() = runTest {
        val wallet = wallet(second)
        val session = owner.acquire(second)
        owner.fenceDeletion(listOf(first.id))
        owner.drainDeleted(listOf(first.id))

        assertSame(session, owner.current)
        coVerify(exactly = 0) { wallet.stop() }
        coVerify(exactly = 0) { wallet.close() }
    }

    @Test
    fun withSession_pendingDeletion_rejectsPollingAndSigningBeforeSdkAccess() = runTest {
        wallet(first)
        val session = owner.acquire(first)
        coEvery { factory.ensureAvailable(first.id) } throws IllegalStateException("deletion pending")
        var nativeCalls = 0

        assertFailsWith<IllegalStateException> { owner.withSession(session) { nativeCalls++ } }
        assertFailsWith<IllegalStateException> { owner.start(session) }

        assertEquals(0, nativeCalls)
        coVerify(exactly = 1) { factory.open(first, BeamNetwork.Mainnet) }
    }

    @Test
    fun acquire_sameAccountConcurrently_reusesSessionAndCachesAddressBeforeStart() = runTest {
        val wallet = wallet(first)
        val requests = List(3) { async { owner.acquire(first) } }
        val acquired = requests.map { it.await() }
        acquired.forEach { assertSame(acquired.first(), it) }
        assertEquals("first-address", acquired.first().receiveAddress.token)
        coVerify(exactly = 1) { factory.open(first, BeamNetwork.Mainnet) }
        coVerify(exactly = 0) { wallet.start() }
        owner.start(acquired.first())
        assertEquals(listOf("first-open", "first-address", "first-start"), events)
    }

    @Test
    fun start_concurrentCalls_shareStartup() = runTest {
        val wallet = wallet(first)
        val acquired = owner.acquire(first)
        val download = CompletableDeferred<Unit>()
        coEvery { wallet.start() } coAnswers { download.await() }
        val starts = List(2) { async { owner.start(acquired) } }
        runCurrent()
        coVerify(exactly = 1) { wallet.start() }
        download.complete(Unit)
        starts.forEach { it.await() }
    }

    @Test
    fun start_afterAwaitedStop_startsSameSessionAgain() = runTest {
        val wallet = wallet(first)
        val acquired = owner.acquire(first)

        owner.start(acquired)
        owner.stop(acquired)
        owner.start(acquired)

        assertSame(acquired, owner.current)
        coVerify(exactly = 2) { wallet.start() }
        coVerify(exactly = 1) { wallet.stop() }
        assertEquals(
            listOf("first-open", "first-address", "first-start", "first-stop", "first-start"),
            events,
        )
    }

    @Test
    fun acquire_suspendedStartup_cancelsDownloadBeforeShutdownAndReplacement() = runTest {
        val previous = wallet(first)
        wallet(second)
        val acquired = owner.acquire(first)
        val download = suspendStartup(previous)
        val starting = async { owner.start(acquired) }
        runCurrent()
        val switched = async { owner.acquire(second) }
        runCurrent()
        assertTrue(switched.isCompleted)
        assertSame(switched.await(), owner.current)
        assertFailsWith<CancellationException> { starting.await() }
        assertFalse(download.isCompleted)
        assertEquals(
            listOf(
                "first-open", "first-address", "startup-cancelled", "first-stop", "first-close",
                "second-open", "second-address",
            ),
            events,
        )
        assertFailsWith<IllegalStateException> { owner.start(acquired) }
        assertFailsWith<IllegalStateException> { owner.withSession(acquired) { it.stop() } }
    }

    @Test
    fun close_suspendedStartup_cancelsDownloadAndClosesWithoutReleasingGate() = runTest {
        val previous = wallet(first)
        val acquired = owner.acquire(first)
        val download = suspendStartup(previous)
        val starting = async { owner.start(acquired) }
        runCurrent()
        val closing = async { owner.close() }
        runCurrent()
        assertTrue(closing.isCompleted)
        closing.await()
        assertFailsWith<CancellationException> { starting.await() }
        assertFalse(download.isCompleted)
        assertNull(owner.current)
        assertEquals(listOf("first-open", "first-address", "startup-cancelled", "first-stop", "first-close"), events)
    }

    @Test
    fun close_startupQueued_neverStartsClosedWallet() = runTest {
        val previous = wallet(first)
        val acquired = owner.acquire(first)
        val starting = async(start = CoroutineStart.UNDISPATCHED) { owner.start(acquired) }
        owner.close()
        assertFailsWith<CancellationException> { starting.await() }
        assertNull(owner.current)
        coVerify(exactly = 0) { previous.start() }
        assertEquals(listOf("first-open", "first-address", "first-stop", "first-close"), events)
    }

    @Test
    fun acquire_startupCancellationSuspends_joinsBeforeShutdownAndReplacement() = runTest {
        val previous = wallet(first)
        wallet(second)
        val acquired = owner.acquire(first)
        val cleanup = CompletableDeferred<Unit>()
        suspendStartup(previous, cleanup)
        val starting = async { owner.start(acquired) }
        runCurrent()
        val switched = async { owner.acquire(second) }
        runCurrent()
        assertNull(owner.current)
        assertFalse(switched.isCompleted)
        coVerify(exactly = 0) { previous.stop() }
        coVerify(exactly = 0) { previous.close() }
        coVerify(exactly = 0) { factory.open(second, BeamNetwork.Mainnet) }
        cleanup.complete(Unit)
        assertSame(switched.await(), owner.current)
        assertFailsWith<CancellationException> { starting.await() }
        assertTrue(events.indexOf("startup-cancelled") < events.indexOf("first-stop"))
    }

    @Test
    fun start_callerCancelled_handoffStillClosesWallet() = runTest {
        val previous = wallet(first)
        wallet(second)
        val acquired = owner.acquire(first)
        val download = suspendStartup(previous)
        val starting = launch { owner.start(acquired) }
        runCurrent()
        starting.cancel()
        starting.join()
        owner.acquire(second)
        assertFalse(download.isCompleted)
        assertTrue(events.indexOf("startup-cancelled") < events.indexOf("first-stop"))
        assertTrue(events.indexOf("first-close") < events.indexOf("second-open"))
    }

    @Test
    fun acquire_accountSwitch_waitsForStopAndCloseAndInvalidatesOldAddress() = runTest {
        val previous = wallet(first)
        wallet(second)
        val acquired = owner.acquire(first)
        val stop = CompletableDeferred<Unit>()
        val close = CompletableDeferred<Unit>()
        coEvery { previous.stop() } coAnswers { events.add("stop-enter"); stop.await() }
        coEvery { previous.close() } coAnswers { events.add("close-enter"); close.await() }
        val switched = async { owner.acquire(second) }
        runCurrent()
        assertNull(owner.current)
        assertFailsWith<IllegalStateException> { acquired.receiveAddress }
        assertFalse(events.contains("second-open"))
        stop.complete(Unit)
        runCurrent()
        assertTrue(events.contains("close-enter"))
        assertFalse(events.contains("second-open"))
        close.complete(Unit)
        assertSame(switched.await(), owner.current)
    }

    @Test
    fun acquire_supersededDuringOpen_closesUnpublishedSession() = runTest {
        val previous = wallet(first)
        wallet(second)
        val gate = CompletableDeferred<Unit>()
        coEvery { factory.open(first, BeamNetwork.Mainnet) } coAnswers { gate.await(); previous }
        val stale = async { owner.acquire(first) }
        runCurrent()
        val latest = async { owner.acquire(second) }
        runCurrent()
        gate.complete(Unit)
        assertFailsWith<CancellationException> { stale.await() }
        assertSame(latest.await(), owner.current)
        assertTrue(events.indexOf("first-close") < events.indexOf("second-open"))
    }

    @Test
    fun acquire_cancelledDuringOpen_closesSessionWithoutPublication() = runTest {
        val previous = wallet(first)
        val gate = CompletableDeferred<Unit>()
        coEvery { factory.open(first, BeamNetwork.Mainnet) } coAnswers { gate.await(); previous }
        val acquiring = launch { owner.acquire(first) }
        runCurrent()
        acquiring.cancel()
        gate.complete(Unit)
        acquiring.join()
        assertNull(owner.current)
        assertEquals(listOf("first-address", "first-stop", "first-close"), events)
    }

    @Test
    fun acquire_cancelledDuringHandoff_finishesClosingWithoutOpeningReplacement() = runTest {
        val previous = wallet(first)
        wallet(second)
        owner.acquire(first)
        val gate = CompletableDeferred<Unit>()
        coEvery { previous.stop() } coAnswers { gate.await() }
        val switching = launch { owner.acquire(second) }
        runCurrent()
        switching.cancel()
        gate.complete(Unit)
        switching.join()
        assertNull(owner.current)
        coVerify(exactly = 1) { previous.close() }
        coVerify(exactly = 0) { factory.open(second, BeamNetwork.Mainnet) }
    }

    @Test
    fun acquire_addressFails_closesSessionAndDoesNotPublish() = runTest {
        val wallet = wallet(first)
        coEvery { wallet.receiveAddress(BeamAddressType.PublicOffline) } throws IllegalStateException("No address")
        assertFailsWith<IllegalStateException> { owner.acquire(first) }
        assertNull(owner.current)
        assertEquals(listOf("first-open", "first-stop", "first-close"), events)
    }

    @Test
    fun acquire_shutdownFails_attemptsCloseAndBlocksReplacement() = runTest {
        val previous = wallet(first)
        wallet(second)
        owner.acquire(first)
        coEvery { previous.stop() } throws IllegalStateException("Stop failed")
        repeat(2) {
            assertFailsWith<IllegalStateException> { owner.acquire(second) }
        }
        assertNull(owner.current)
        coVerify(exactly = 1) { previous.close() }
        coVerify(exactly = 0) { factory.open(second, BeamNetwork.Mainnet) }
    }

    @Test
    fun close_cancelledDuringShutdown_awaitsCloseAndRemainsEmpty() = runTest {
        val previous = wallet(first)
        owner.acquire(first)
        val gate = CompletableDeferred<Unit>()
        coEvery { previous.stop() } coAnswers { gate.await() }
        val closing = launch { owner.close() }
        runCurrent()
        closing.cancel()
        assertNull(owner.current)
        gate.complete(Unit)
        closing.join()
        owner.close()
        coVerify(exactly = 1) { previous.close() }
    }

    private fun suspendStartup(
        wallet: BeamWalletSession,
        cleanup: CompletableDeferred<Unit>? = null,
    ): CompletableDeferred<Unit> {
        val download = CompletableDeferred<Unit>()
        coEvery { wallet.start() } coAnswers {
            try {
                download.await()
            } finally {
                withContext(NonCancellable) { cleanup?.await() }
                events.add("startup-cancelled")
            }
        }
        return download
    }

    private fun wallet(account: Account): BeamWalletSession {
        val wallet = mockk<BeamWalletSession>()
        coEvery { factory.open(account, BeamNetwork.Mainnet) } coAnswers {
            events.add("${account.id}-open")
            wallet
        }
        coEvery { wallet.receiveAddress(BeamAddressType.PublicOffline) } coAnswers {
            events.add("${account.id}-address")
            BeamAddress("${account.id}-address", BeamAddressType.PublicOffline, SdkBeamNetwork.Mainnet)
        }
        coEvery { wallet.start() } coAnswers { events.add("${account.id}-start"); Unit }
        coEvery { wallet.stop() } coAnswers { events.add("${account.id}-stop"); Unit }
        coEvery { wallet.close() } coAnswers { events.add("${account.id}-close"); Unit }
        return wallet
    }

    private fun account(id: String) = Account(id, id, mockk<AccountType.Mnemonic>(), AccountOrigin.Created, 0)
}
