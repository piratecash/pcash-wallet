package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.adapters.zcash.session.FakeCoverage
import cash.p.terminal.core.adapters.zcash.session.ZcashSession
import cash.p.terminal.core.adapters.zcash.session.stubDiscovery
import cash.p.terminal.core.adapters.zcash.session.zcashSession
import cash.p.terminal.core.adapters.zcash.session.zcashWalletMock
import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import cash.p.zcash.NextUnusedAddress
import cash.p.zcash.TransparentCoverage
import cash.p.zcash.ZcashWallet
import io.mockk.coEvery
import io.horizontalsystems.core.BackgroundManagerState
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coVerify
import io.mockk.every
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * [ZcashAdapter.freshReceiveAddress] and [ZcashAdapter.freshReceiveAddressChanges]: the next
 * transparent address the Receive screen can show without reusing one Suite (or this wallet's own
 * one-time payouts) has already handed out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZcashAdapterFreshReceiveAddressTest : ZcashAdapterTestFixture() {

    private fun createTransparentAdapter() =
        createAdapter(addressSpecTyped = AddressSpecType.Transparent).also { adapter = it }

    /** Swaps the fixture's mocked [session] for a real one, so discovery's own coalescing runs. */
    private fun TestScope.useRealSession(): ZcashWallet {
        val innerWallet = zcashWalletMock()
        val realSession = zcashSession(innerWallet, discovery = discoveryState)
        coEvery { sessionManager.acquire(any()) } returns realSession
        advanceUntilIdle()
        return innerWallet
    }

    @Test
    fun freshReceiveAddress_nonTransparentSpec_returnsTheStaticAddressUnchecked() = runTest(dispatcher) {
        val shielded = createAdapter(addressSpecTyped = AddressSpecType.Shielded)

        val fresh = shielded.freshReceiveAddress()

        assertEquals(shielded.receiveAddress, fresh)
        coVerify(exactly = 0) { zcashWallet.nextUnusedTransparentAddress(any(), any()) }
    }

    @Test
    fun freshReceiveAddress_sdkCallFails_returnsTheStaticAddress() = runTest(dispatcher) {
        coEvery { session.discoverForEpoch(any()) } returns true
        coEvery { zcashWallet.nextUnusedTransparentAddress(any(), any()) } throws IOException("boom")
        val adapter = createTransparentAdapter()

        val fresh = adapter.freshReceiveAddress()

        assertEquals(adapter.receiveAddress, fresh)
    }

    @Test
    fun freshReceiveAddress_cancelledAwaitingDiscovery_propagatesTheCancellation() = runTest(dispatcher) {
        val discoveryStarted = CompletableDeferred<Unit>()
        coEvery { session.discoverForEpoch(any()) } coAnswers {
            discoveryStarted.complete(Unit)
            awaitCancellation()
        }
        val adapter = createTransparentAdapter()

        val job = launch { adapter.freshReceiveAddress() }
        discoveryStarted.await()
        job.cancel()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        coVerify(exactly = 0) { zcashWallet.nextUnusedTransparentAddress(any(), any()) }
    }

    @Test
    fun freshReceiveAddress_cancelledAcquiringTheSession_propagatesTheCancellation() = runTest(dispatcher) {
        val acquireStarted = CompletableDeferred<Unit>()
        coEvery { sessionManager.acquire(any()) } coAnswers {
            acquireStarted.complete(Unit)
            awaitCancellation()
        }
        val adapter = createTransparentAdapter()

        val job = launch { adapter.freshReceiveAddress() }
        acquireStarted.await()
        job.cancel()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        coVerify(exactly = 0) { session.discoverForEpoch(any()) }
    }

    @Test
    fun freshReceiveAddress_sessionNotYetAcquired_awaitsItInsteadOfThrowing() = runTest(dispatcher) {
        coEvery { session.discoverForEpoch(any()) } returns true
        val adapter = createTransparentAdapter()

        adapter.freshReceiveAddress()

        coVerify(exactly = 1) { sessionManager.acquire(wallet) }
        coVerify(exactly = 1) { zcashWallet.nextUnusedTransparentAddress(any(), any()) }
    }

    @Test
    fun freshReceiveAddress_inBackground_waitsForTheForegroundInsteadOfReopeningTheSession() =
        runTest(dispatcher) {
            coEvery { session.discoverForEpoch(any()) } returns true
            every { backgroundManager.inForeground } returns false
            backgroundStateFlow.value = BackgroundManagerState.EnterBackground
            val adapter = createTransparentAdapter()

            val answer = async { adapter.freshReceiveAddress() }
            advanceUntilIdle()

            assertFalse(answer.isCompleted)
            coVerify(exactly = 0) { sessionManager.acquire(any()) }

            every { backgroundManager.inForeground } returns true
            backgroundStateFlow.value = BackgroundManagerState.EnterForeground
            advanceUntilIdle()

            assertTrue(answer.isCompleted)
            answer.await()
            coVerify(exactly = 1) { sessionManager.acquire(wallet) }
        }

    @Test
    fun freshReceiveAddress_inBackgroundDuringPolling_answersWithoutWaitingForTheForeground() =
        runTest(dispatcher) {
            coEvery { session.discoverForEpoch(any()) } returns true
            every { backgroundManager.inForeground } returns false
            backgroundStateFlow.value = BackgroundManagerState.EnterBackground
            val adapter = createTransparentAdapter()
            adapter.startForPolling()

            adapter.freshReceiveAddress()
            advanceUntilIdle()

            coVerify(exactly = 1) { sessionManager.acquire(wallet) }
        }

    @Test
    fun freshReceiveAddress_epochNotYetDiscovered_triggersTheDiscoveryItself() = runTest(dispatcher) {
        val innerWallet = useRealSession()
        stubDiscovery(innerWallet, FakeCoverage())
        coEvery { innerWallet.nextUnusedTransparentAddress(any(), any()) } returns
            NextUnusedAddress(address = DERIVED_ADDRESS, certified = true, gap = 0)
        val adapter = createTransparentAdapter()

        val fresh = adapter.freshReceiveAddress()

        coVerify(exactly = 1) { innerWallet.discoverTransparentAddresses(any(), any(), any()) }
        assertEquals(DERIVED_ADDRESS, fresh)
    }

    @Test
    fun freshReceiveAddress_schedulerAlreadyDiscoveringThisEpoch_joinsThatWalk() = runTest(dispatcher) {
        val innerWallet = useRealSession()
        val walkStarted = CompletableDeferred<Unit>()
        val releaseWalk = CompletableDeferred<Unit>()
        coEvery { innerWallet.transparentCoverage(any()) } returns
            TransparentCoverage(certified = false, gap = 0, trim = 0)
        coEvery { innerWallet.discoverTransparentAddresses(any(), any(), any()) } coAnswers {
            walkStarted.complete(Unit)
            releaseWalk.await()
            0
        }
        coEvery { innerWallet.nextUnusedTransparentAddress(any(), any()) } returns
            NextUnusedAddress(address = DERIVED_ADDRESS, certified = false, gap = 0)
        val adapter = createTransparentAdapter()

        val first = async { adapter.freshReceiveAddress() }
        walkStarted.await()
        val second = async { adapter.freshReceiveAddress() }
        runCurrent()

        releaseWalk.complete(Unit)
        advanceUntilIdle()

        first.await()
        second.await()
        coVerify(exactly = 1) { innerWallet.discoverTransparentAddresses(any(), any(), any()) }
    }

    @Test
    fun freshReceiveAddress_laterForegroundEpoch_runsThatEpochsDiscovery() = runTest(dispatcher) {
        coEvery { session.discoverForEpoch(any()) } returns true
        val adapter = createTransparentAdapter()
        adapter.freshReceiveAddress()
        coVerify(exactly = 1) { session.discoverForEpoch(0) }

        foregroundEpochFlow.value = 1
        adapter.freshReceiveAddress()

        coVerify(exactly = 1) { session.discoverForEpoch(1) }
    }

    @Test
    fun freshReceiveAddress_foregroundEpochRisesDuringTheCall_rediscoversOnce() = runTest(dispatcher) {
        coEvery { session.discoverForEpoch(0) } coAnswers {
            foregroundEpochFlow.value = 1
            true
        }
        coEvery { session.discoverForEpoch(1) } returns true
        val adapter = createTransparentAdapter()

        adapter.freshReceiveAddress()

        coVerify(exactly = 1) { session.discoverForEpoch(0) }
        coVerify(exactly = 1) { session.discoverForEpoch(1) }
    }

    @Test
    fun freshReceiveAddress_discoveryThrows_stillAsksTheSdk() = runTest(dispatcher) {
        coEvery { session.discoverForEpoch(any()) } throws IOException("boom")
        coEvery { zcashWallet.nextUnusedTransparentAddress(any(), any()) } returns
            NextUnusedAddress(address = DERIVED_ADDRESS, certified = true, gap = ZcashSession.DEEP_GAP_LIMIT)
        val adapter = createTransparentAdapter()

        val fresh = adapter.freshReceiveAddress()

        coVerify(exactly = 1) { zcashWallet.nextUnusedTransparentAddress(any(), any()) }
        assertEquals(DERIVED_ADDRESS, fresh)
    }

    @Test
    fun freshReceiveAddress_discoveryFails_stillAsksTheSdk() = runTest(dispatcher) {
        coEvery { session.discoverForEpoch(any()) } returns false
        coEvery { zcashWallet.nextUnusedTransparentAddress(any(), any()) } returns
            NextUnusedAddress(address = DERIVED_ADDRESS, certified = true, gap = ZcashSession.DEEP_GAP_LIMIT)
        val adapter = createTransparentAdapter()

        val fresh = adapter.freshReceiveAddress()

        coVerify(exactly = 1) { zcashWallet.nextUnusedTransparentAddress(any(), any()) }
        assertEquals(DERIVED_ADDRESS, fresh)
    }

    @Test
    fun freshReceiveAddress_offline_answersFromTheDatabaseWithoutStartingDiscovery() = runTest(dispatcher) {
        every { offlineModeManager.isNetworkPaused(OfflineKey(ACCOUNT_ID, BlockchainType.Zcash)) } returns true
        coEvery { zcashWallet.nextUnusedTransparentAddress(any(), any()) } returns
            NextUnusedAddress(address = DERIVED_ADDRESS, certified = true, gap = ZcashSession.DEEP_GAP_LIMIT)
        val adapter = createTransparentAdapter()

        val fresh = adapter.freshReceiveAddress()

        coVerify(exactly = 0) { session.discoverForEpoch(any()) }
        coVerify(exactly = 1) { zcashWallet.nextUnusedTransparentAddress(any(), any()) }
        assertEquals(DERIVED_ADDRESS, fresh)
    }

    @Test
    fun freshReceiveAddress_selectorThrows_reportsTheFailureInTheDiscoveryStatusRow() = runTest(dispatcher) {
        coEvery { session.discoverForEpoch(any()) } returns true
        coEvery { zcashWallet.nextUnusedTransparentAddress(any(), any()) } throws IOException("disk full")
        val adapter = createTransparentAdapter()

        adapter.freshReceiveAddress()

        assertTrue(adapter.statusInfo["Transparent discovery"].toString().contains("selector failed (IOException)"))
    }

    @Test
    fun freshReceiveAddress_coverageUncertifiedByATrim_returnsTheStaticAddress() = runTest(dispatcher) {
        coEvery { session.discoverForEpoch(any()) } returns true
        coEvery { zcashWallet.nextUnusedTransparentAddress(any(), any()) } returns
            NextUnusedAddress(address = DERIVED_ADDRESS, certified = false, gap = 100)
        val adapter = createTransparentAdapter()

        val fresh = adapter.freshReceiveAddress()

        assertEquals(adapter.receiveAddress, fresh)
    }

    @Test
    fun freshReceiveAddress_certifiedSelectorAnswer_returnsTheDerivedAddress() = runTest(dispatcher) {
        coEvery { session.discoverForEpoch(any()) } returns true
        coEvery { zcashWallet.nextUnusedTransparentAddress(any(), any()) } returns
            NextUnusedAddress(address = DERIVED_ADDRESS, certified = true, gap = ZcashSession.DEEP_GAP_LIMIT)
        val adapter = createTransparentAdapter()

        val fresh = adapter.freshReceiveAddress()

        assertEquals(DERIVED_ADDRESS, fresh)
    }

    @Test
    fun freshReceiveAddressChanges_emitsOnceOnSubscriptionAndOncePerCompletedWalk() = runTest(dispatcher) {
        val adapter = createTransparentAdapter()
        var emissions = 0
        val job = launch { adapter.freshReceiveAddressChanges.collect { emissions++ } }
        runCurrent()
        assertEquals(1, emissions)

        discoveryState.completedWalks.value = 1
        runCurrent()
        assertEquals(2, emissions)

        // A revalidation walk of the same epoch still bumps completedWalks.
        discoveryState.completedWalks.value = 2
        runCurrent()
        assertEquals(3, emissions)

        job.cancel()
    }

    @Test
    fun generateOneTimeAddress_concurrentFreshReceiveAddress_doesNotInterleaveTheCriticalSections() =
        runTest(dispatcher) {
            coEvery { session.discoverForEpoch(any()) } returns true
            val started = CompletableDeferred<Unit>()
            val releaseGenerate = CompletableDeferred<Unit>()
            coEvery { zcashWallet.nextTransparentAddress(any()) } coAnswers {
                started.complete(Unit)
                releaseGenerate.await()
                ONE_TIME_ADDRESS
            }
            val adapter = createTransparentAdapter()
            adapter.attachLocalData()
            advanceUntilIdle()

            val generate = launch { adapter.generateOneTimeAddress() }
            started.await()
            val fresh = async { adapter.freshReceiveAddress() }
            runCurrent()
            assertFalse(fresh.isCompleted)
            coVerify(exactly = 0) { zcashWallet.nextUnusedTransparentAddress(any(), any()) }

            releaseGenerate.complete(Unit)
            advanceUntilIdle()

            generate.join()
            fresh.await()
        }

    @Test
    fun generateOneTimeAddress_thenFreshReceiveAddress_neverReturnTheSameAddress() = runTest(dispatcher) {
        coEvery { session.discoverForEpoch(any()) } returns true
        val reservations = mutableListOf<String>()
        coEvery { singleUseAddressManager.saveNewAddress(any()) } coAnswers { reservations += firstArg<String>() }
        coEvery { singleUseAddressManager.getAllAddresses() } answers { reservations.toList() }
        coEvery { zcashWallet.nextTransparentAddress(any()) } returns ONE_TIME_ADDRESS
        coEvery { zcashWallet.nextUnusedTransparentAddress(any(), any()) } coAnswers {
            val excluded = secondArg<List<String>>()
            assertTrue(ONE_TIME_ADDRESS in excluded)
            NextUnusedAddress(address = DERIVED_ADDRESS, certified = true, gap = 0)
        }
        val adapter = createTransparentAdapter()
        adapter.attachLocalData()
        advanceUntilIdle()

        val oneTime = adapter.generateOneTimeAddress()
        val fresh = adapter.freshReceiveAddress()

        assertEquals(ONE_TIME_ADDRESS, oneTime)
        assertEquals(DERIVED_ADDRESS, fresh)
        assertTrue(fresh != oneTime)
    }

    private companion object {
        const val DERIVED_ADDRESS = "t1derived"
        const val ONE_TIME_ADDRESS = "t1onetime"
    }
}
