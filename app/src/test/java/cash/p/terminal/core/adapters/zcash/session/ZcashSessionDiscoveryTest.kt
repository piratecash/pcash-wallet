package cash.p.terminal.core.adapters.zcash.session

import cash.p.zcash.TransparentCoverage
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertFailsWith

private const val DRAIN_TIMEOUT_MS = 30_000L
private const val DB_ACCOUNT_ID = 7
private const val DEEP_GAP_LIMIT = 500
private const val STEADY_GAP_LIMIT = 20

@OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class)
class ZcashSessionDiscoveryTest {

    private val wallet = zcashWalletMock()

    private fun TestScope.session(
        supportsTransparent: Boolean = true,
        deepSweepRequired: Boolean = true,
        discovery: ZcashDiscoveryState = ZcashDiscoveryState(),
    ) = zcashSession(
        wallet,
        supportsTransparent = supportsTransparent,
        deepSweepRequired = deepSweepRequired,
        discovery = discovery,
    )

    @Test
    fun discoverForEpoch_succeeded_publishesTheEpoch() = runTest {
        stubDiscovery(wallet, FakeCoverage())
        val discovery = ZcashDiscoveryState()
        val session = session(discovery = discovery)
        advanceUntilIdle()

        assertTrue(session.discoverForEpoch(1))

        assertEquals(1, discovery.discoveredEpoch.value)
    }

    @Test
    fun discoverForEpoch_failed_doesNotPublishTheEpoch() = runTest {
        stubDiscovery(wallet, FakeCoverage())
        coEvery { wallet.discoverTransparentAddresses(any(), any(), any()) } throws IOException("boom")
        val discovery = ZcashDiscoveryState()
        val session = session(discovery = discovery)
        advanceUntilIdle()

        assertFailsWith<IOException> { session.discoverForEpoch(1) }

        assertEquals(0, discovery.discoveredEpoch.value)
    }

    @Test
    fun discoverForEpoch_secondCallerInTheSameEpoch_joinsTheRunningWalk() = runTest {
        val state = FakeCoverage()
        val walkStarted = CompletableDeferred<Unit>()
        val releaseWalk = CompletableDeferred<Unit>()
        coEvery { wallet.transparentCoverage(any()) } answers {
            TransparentCoverage(certified = state.certified, gap = state.gap, trim = state.trim)
        }
        coEvery { wallet.discoverTransparentAddresses(any(), any(), any()) } coAnswers {
            walkStarted.complete(Unit)
            releaseWalk.await()
            state.gap = maxOf(state.gap, secondArg())
            state.certified = true
            0
        }
        val session = session()
        advanceUntilIdle()

        val first = async { session.discoverForEpoch(1) }
        walkStarted.await()
        val second = async { session.discoverForEpoch(1) }
        runCurrent()
        assertFalse(second.isCompleted)

        releaseWalk.complete(Unit)
        advanceUntilIdle()

        assertTrue(first.await())
        assertTrue(second.await())
        coVerify(exactly = 1) { wallet.discoverTransparentAddresses(any(), any(), any()) }
    }

    @Test
    fun discoverForEpoch_callerStopsWaiting_theWalkStillCompletes() = runTest {
        val state = FakeCoverage()
        val walkStarted = CompletableDeferred<Unit>()
        val releaseWalk = CompletableDeferred<Unit>()
        coEvery { wallet.transparentCoverage(any()) } answers {
            TransparentCoverage(certified = state.certified, gap = state.gap, trim = state.trim)
        }
        coEvery { wallet.discoverTransparentAddresses(any(), any(), any()) } coAnswers {
            walkStarted.complete(Unit)
            releaseWalk.await()
            state.gap = maxOf(state.gap, secondArg())
            state.certified = true
            0
        }
        val discovery = ZcashDiscoveryState()
        val session = session(discovery = discovery)
        advanceUntilIdle()

        val waiter = launch { session.discoverForEpoch(1) }
        walkStarted.await()
        waiter.cancel()
        runCurrent()

        releaseWalk.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, discovery.discoveredEpoch.value)
    }

    @Test
    fun discoverForEpoch_newerEpoch_chainsBehindTheRunInFlight() = runTest {
        val state = FakeCoverage()
        val gapLimits = mutableListOf<Int>()
        val firstWalkStarted = CompletableDeferred<Unit>()
        val releaseFirstWalk = CompletableDeferred<Unit>()
        coEvery { wallet.transparentCoverage(any()) } answers {
            TransparentCoverage(certified = state.certified, gap = state.gap, trim = state.trim)
        }
        coEvery { wallet.discoverTransparentAddresses(any(), any(), any()) } coAnswers {
            val gapLimit = secondArg<Int>()
            gapLimits += gapLimit
            if (gapLimits.size == 1) {
                firstWalkStarted.complete(Unit)
                releaseFirstWalk.await()
            }
            state.gap = maxOf(state.gap, gapLimit)
            state.certified = true
            0
        }
        val session = session()
        advanceUntilIdle()

        val first = async { session.discoverForEpoch(1) }
        firstWalkStarted.await()
        val second = async { session.discoverForEpoch(2) }
        runCurrent()

        assertEquals(1, gapLimits.size)
        releaseFirstWalk.complete(Unit)
        advanceUntilIdle()

        assertTrue(first.await())
        assertTrue(second.await())
        assertEquals(2, gapLimits.size)
    }

    @Test
    fun discoverForEpoch_epochAlreadyCompleted_doesNotWalkAgain() = runTest {
        stubDiscovery(wallet, FakeCoverage())
        val session = session()
        advanceUntilIdle()
        session.discoverForEpoch(1)

        session.discoverForEpoch(1)

        coVerify(exactly = 1) { wallet.discoverTransparentAddresses(any(), any(), any()) }
    }

    @Test
    fun discoverForEpoch_twoNewerEpochsQueueBehindOneWalk_walksOnceMoreForTheNewest() = runTest {
        val state = FakeCoverage()
        val gapLimits = mutableListOf<Int>()
        var concurrentCalls = 0
        var maxConcurrentCalls = 0
        val firstWalkStarted = CompletableDeferred<Unit>()
        val releaseFirstWalk = CompletableDeferred<Unit>()
        coEvery { wallet.transparentCoverage(any()) } answers {
            TransparentCoverage(certified = state.certified, gap = state.gap, trim = state.trim)
        }
        coEvery { wallet.discoverTransparentAddresses(any(), any(), any()) } coAnswers {
            concurrentCalls++
            maxConcurrentCalls = maxOf(maxConcurrentCalls, concurrentCalls)
            val gapLimit = secondArg<Int>()
            gapLimits += gapLimit
            if (gapLimits.size == 1) {
                firstWalkStarted.complete(Unit)
                releaseFirstWalk.await()
            }
            state.gap = maxOf(state.gap, gapLimit)
            state.certified = true
            concurrentCalls--
            0
        }
        val discovery = ZcashDiscoveryState()
        val session = session(discovery = discovery)
        advanceUntilIdle()

        val first = async { session.discoverForEpoch(1) }
        firstWalkStarted.await()
        val second = async { session.discoverForEpoch(2) }
        runCurrent()
        val third = async { session.discoverForEpoch(3) }
        runCurrent()

        assertEquals(1, gapLimits.size)
        releaseFirstWalk.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, maxConcurrentCalls)
        assertEquals(listOf(DEEP_GAP_LIMIT, STEADY_GAP_LIMIT), gapLimits)
        assertTrue(first.await())
        assertTrue(second.await())
        assertTrue(third.await())
        assertEquals(3, discovery.discoveredEpoch.value)
        assertEquals(2, discovery.completedWalks.value)
    }

    @Test
    fun discoverForEpoch_coverageInvalidatedAndTheTrimSettles_walksOnceMore() = runTest {
        val state = FakeCoverage()
        stubDiscovery(wallet, state)
        val discovery = ZcashDiscoveryState()
        val session = session(discovery = discovery)
        advanceUntilIdle()
        assertTrue(session.discoverForEpoch(1))
        assertEquals(1, discovery.completedWalks.value)

        // The very next sync() trims the walk's own note: a real trim, at a fixed generation.
        state.certified = false
        state.trim = 1

        session.discoverForEpoch(1) // first sight of the new trim: observes only
        assertEquals(1, discovery.completedWalks.value)

        session.discoverForEpoch(1) // same settled generation: spends the re-arm
        assertEquals(2, discovery.completedWalks.value)
        assertEquals(1, discovery.discoveredEpoch.value)
    }

    @Test
    fun discoverForEpoch_failedWalk_doesNotBumpCompletedWalks() = runTest {
        coEvery { wallet.transparentCoverage(any()) } returns TransparentCoverage(certified = false, gap = 0, trim = 0)
        coEvery { wallet.discoverTransparentAddresses(any(), any(), any()) } throws IOException("boom")
        val discovery = ZcashDiscoveryState()
        val session = session(discovery = discovery)
        advanceUntilIdle()

        assertFailsWith<IOException> { session.discoverForEpoch(1) }

        assertEquals(0, discovery.completedWalks.value)
    }

    @Test
    fun discoverForEpoch_theSettledTrimWasAlreadyRevalidated_doesNotWalkAgain() = runTest {
        val fixedTrim = 1L
        var walkCount = 0
        coEvery { wallet.transparentCoverage(any()) } answers {
            TransparentCoverage(certified = false, gap = DEEP_GAP_LIMIT, trim = fixedTrim)
        }
        coEvery { wallet.discoverTransparentAddresses(any(), any(), any()) } coAnswers { walkCount++; 0 }
        val session = session()
        advanceUntilIdle()

        session.discoverForEpoch(1) // publishes epoch 1
        session.discoverForEpoch(1) // first sight of this trim: observes only
        session.discoverForEpoch(1) // same settled generation: spends the re-arm
        assertEquals(2, walkCount)

        session.discoverForEpoch(1) // already revalidated at this generation: no third walk

        assertEquals(2, walkCount)
    }

    @Test
    fun discoverForEpoch_theTrimKeepsMoving_doesNotWalkUntilItSettles() = runTest {
        var trim = 0L
        var walkCount = 0
        coEvery { wallet.transparentCoverage(any()) } answers {
            TransparentCoverage(certified = false, gap = DEEP_GAP_LIMIT, trim = trim)
        }
        coEvery { wallet.discoverTransparentAddresses(any(), any(), any()) } coAnswers { walkCount++; 0 }
        val session = session()
        advanceUntilIdle()
        session.discoverForEpoch(1) // first walk, publishes epoch 1
        val walksAfterFirst = walkCount

        // The trim keeps landing: several turns, never settling.
        repeat(3) {
            trim++
            session.discoverForEpoch(1)
        }
        assertEquals(walksAfterFirst, walkCount)

        // The generation finally holds still for one more turn: the re-arm is spent.
        session.discoverForEpoch(1)

        assertEquals(walksAfterFirst + 1, walkCount)
    }

    @Test
    fun discoverForEpoch_theRevalidationWalkFails_spendsTheReArmAgainOnTheSameGeneration() = runTest {
        val fixedTrim = 1L
        var walkCount = 0
        var nextWalkFails = false
        coEvery { wallet.transparentCoverage(any()) } answers {
            TransparentCoverage(certified = false, gap = DEEP_GAP_LIMIT, trim = fixedTrim)
        }
        coEvery { wallet.discoverTransparentAddresses(any(), any(), any()) } coAnswers {
            walkCount++
            if (nextWalkFails) throw IOException("boom")
            0
        }
        val session = session()
        advanceUntilIdle()
        session.discoverForEpoch(1) // publishes epoch 1
        session.discoverForEpoch(1) // observes the trim, no walk

        nextWalkFails = true
        assertFailsWith<IOException> { session.discoverForEpoch(1) } // spends the re-arm; walk fails
        assertEquals(2, walkCount)

        nextWalkFails = false
        session.discoverForEpoch(1) // the re-arm was given back: walks again at the same generation

        assertEquals(3, walkCount)
    }

    @Test
    fun discoverForEpoch_accountWithoutTransparentSpec_returnsWithoutWalking() = runTest {
        val session = session(supportsTransparent = false)
        advanceUntilIdle()

        assertTrue(session.discoverForEpoch(1))

        coVerify(exactly = 0) { wallet.discoverTransparentAddresses(any(), any(), any()) }
        coVerify(exactly = 0) { wallet.transparentCoverage(any()) }
    }

    @Test
    fun discoverForEpoch_firstWalkForTheAccount_usesTheDeepGapLimit() = runTest {
        coEvery { wallet.transparentCoverage(any()) } returns TransparentCoverage(certified = false, gap = 0, trim = 0)
        coEvery { wallet.discoverTransparentAddresses(any(), any(), any()) } returns 0
        val session = session()
        advanceUntilIdle()

        session.discoverForEpoch(1)

        coVerify(exactly = 1) { wallet.discoverTransparentAddresses(DB_ACCOUNT_ID, DEEP_GAP_LIMIT, ZcashSession.DISCOVERY_CONCURRENCY) }
    }

    @Test
    fun discoverForEpoch_afterASuccessfulDeepWalk_usesTheSteadyGapLimit() = runTest {
        stubDiscovery(wallet, FakeCoverage())
        val session = session()
        advanceUntilIdle()
        session.discoverForEpoch(1)

        session.discoverForEpoch(2)

        coVerify(exactly = 1) { wallet.discoverTransparentAddresses(DB_ACCOUNT_ID, DEEP_GAP_LIMIT, ZcashSession.DISCOVERY_CONCURRENCY) }
        coVerify(exactly = 1) { wallet.discoverTransparentAddresses(DB_ACCOUNT_ID, STEADY_GAP_LIMIT, ZcashSession.DISCOVERY_CONCURRENCY) }
    }

    @Test
    fun discoverForEpoch_deepWalkFails_staysDeepOnTheNextAttempt() = runTest {
        coEvery { wallet.transparentCoverage(any()) } returns TransparentCoverage(certified = false, gap = 0, trim = 0)
        coEvery { wallet.discoverTransparentAddresses(any(), any(), any()) } throws IOException("boom")
        val session = session()
        advanceUntilIdle()
        assertFailsWith<IOException> { session.discoverForEpoch(1) }

        assertFailsWith<IOException> { session.discoverForEpoch(1) }

        coVerify(exactly = 2) { wallet.discoverTransparentAddresses(DB_ACCOUNT_ID, DEEP_GAP_LIMIT, ZcashSession.DISCOVERY_CONCURRENCY) }
    }

    @Test
    fun discoverForEpoch_sessionUnavailable_publishesNeitherTheEpochNorTheCoverage() = runTest {
        coEvery { wallet.transparentCoverage(any()) } returns TransparentCoverage(certified = false, gap = 0, trim = 0)
        coEvery { wallet.discoverTransparentAddresses(any(), any(), any()) } returns 0
        val discovery = ZcashDiscoveryState()
        val session = session(discovery = discovery)
        advanceUntilIdle()
        assertTrue(session.drain(DRAIN_TIMEOUT_MS))

        assertFalse(session.discoverForEpoch(1))

        assertEquals(0, discovery.discoveredEpoch.value)
        coVerify(exactly = 0) { wallet.discoverTransparentAddresses(any(), any(), any()) }

        // Draining did not touch the depth question: the next attempt still asks for the deep gap.
        session.reactivate()
        session.discoverForEpoch(1)
        coVerify(exactly = 1) { wallet.discoverTransparentAddresses(DB_ACCOUNT_ID, DEEP_GAP_LIMIT, ZcashSession.DISCOVERY_CONCURRENCY) }
    }

    @Test
    fun discoverForEpoch_walkSucceedsButTheRecordStaysShallow_returnsFalse() = runTest {
        coEvery { wallet.discoverTransparentAddresses(any(), any(), any()) } returns 0
        coEvery { wallet.transparentCoverage(any()) } returns
            TransparentCoverage(certified = false, gap = STEADY_GAP_LIMIT, trim = 0)
        val session = session()
        advanceUntilIdle()

        assertFalse(session.discoverForEpoch(1))
    }

    @Test
    fun discoverForEpoch_recordedGapBelowTheDeepLimit_owesADeepSweepAgain() = runTest {
        coEvery { wallet.transparentCoverage(any()) } returns
            TransparentCoverage(certified = true, gap = DEEP_GAP_LIMIT - 1, trim = 0)
        coEvery { wallet.discoverTransparentAddresses(any(), any(), any()) } returns 0
        val session = session()
        advanceUntilIdle()

        session.discoverForEpoch(1)

        coVerify(exactly = 1) { wallet.discoverTransparentAddresses(DB_ACCOUNT_ID, DEEP_GAP_LIMIT, ZcashSession.DISCOVERY_CONCURRENCY) }
    }

    @Test
    fun discoverForEpoch_recordedGapAtTheDeepLimit_usesTheSteadyWidth() = runTest {
        coEvery { wallet.transparentCoverage(any()) } returns
            TransparentCoverage(certified = true, gap = DEEP_GAP_LIMIT, trim = 0)
        coEvery { wallet.discoverTransparentAddresses(any(), any(), any()) } returns 0
        val session = session()
        advanceUntilIdle()

        session.discoverForEpoch(1)

        coVerify(exactly = 1) { wallet.discoverTransparentAddresses(DB_ACCOUNT_ID, STEADY_GAP_LIMIT, ZcashSession.DISCOVERY_CONCURRENCY) }
    }

    @Test
    fun discoverForEpoch_drainRunsBesideAWalk_seesTheEpochAlreadyPublished() = runTest {
        val walkStarted = CompletableDeferred<Unit>()
        val releaseWalk = CompletableDeferred<Unit>()
        coEvery { wallet.transparentCoverage(any()) } returns TransparentCoverage(certified = false, gap = 0, trim = 0)
        coEvery { wallet.discoverTransparentAddresses(any(), any(), any()) } coAnswers {
            walkStarted.complete(Unit)
            releaseWalk.await()
            0
        }
        val discovery = ZcashDiscoveryState()
        val session = session(discovery = discovery)
        advanceUntilIdle()

        launch { session.discoverForEpoch(1) }
        walkStarted.await()
        val draining = async { session.drain(DRAIN_TIMEOUT_MS) }
        runCurrent()
        assertFalse(draining.isCompleted)

        releaseWalk.complete(Unit)
        assertTrue(draining.await())

        assertEquals(1, discovery.discoveredEpoch.value)
    }

    @Test
    fun init_coverageReadThrows_doesNotEscapeTheSessionScope() = runTest {
        coEvery { wallet.transparentCoverage(any()) } throws IOException("disk")

        session()
        runCurrent()

        coVerify(exactly = 0) { wallet.discoverTransparentAddresses(any(), any(), any()) }
    }

    @Test
    fun discoverForEpoch_coverageReadThrows_propagatesToTheCallerWithoutWalking() = runTest {
        coEvery { wallet.transparentCoverage(any()) } throws IOException("disk")
        val session = session()

        assertFailsWith<IOException> { session.discoverForEpoch(1) }

        coVerify(exactly = 0) { wallet.discoverTransparentAddresses(any(), any(), any()) }
    }

    @Test
    fun discoverForEpoch_reopenedSessionCoveredOnDisk_doesNotWalkBeforeAnyInitializationRan() = runTest {
        // The record and the memo both say covered; nothing has been given a chance to run yet.
        stubDiscovery(wallet, FakeCoverage(certified = true, gap = DEEP_GAP_LIMIT))
        val discovery = ZcashDiscoveryState().apply { discoveredEpoch.value = 1 }
        val session = session(discovery = discovery)

        assertTrue(session.discoverForEpoch(1))

        coVerify(exactly = 0) { wallet.discoverTransparentAddresses(any(), any(), any()) }
    }

    @Test
    fun discoverForEpoch_reopenedSessionWithADeepRecord_walksTheNewEpochAtTheSteadyWidth() = runTest {
        stubDiscovery(wallet, FakeCoverage(certified = true, gap = DEEP_GAP_LIMIT))
        val discovery = ZcashDiscoveryState().apply { discoveredEpoch.value = 1 }
        val session = session(discovery = discovery)

        session.discoverForEpoch(2)

        coVerify(exactly = 1) { wallet.discoverTransparentAddresses(DB_ACCOUNT_ID, STEADY_GAP_LIMIT, ZcashSession.DISCOVERY_CONCURRENCY) }
        coVerify(exactly = 0) { wallet.discoverTransparentAddresses(DB_ACCOUNT_ID, DEEP_GAP_LIMIT, ZcashSession.DISCOVERY_CONCURRENCY) }
    }

    @Test
    fun discoverForEpoch_deepSweepNotRequired_firstWalkUsesTheSteadyGapLimit() = runTest {
        stubDiscovery(wallet, FakeCoverage())
        val discovery = ZcashDiscoveryState()
        val session = session(deepSweepRequired = false, discovery = discovery)
        advanceUntilIdle()

        assertTrue(session.discoverForEpoch(1))

        coVerify(exactly = 1) {
            wallet.discoverTransparentAddresses(DB_ACCOUNT_ID, STEADY_GAP_LIMIT, ZcashSession.DISCOVERY_CONCURRENCY)
        }
        coVerify(exactly = 0) {
            wallet.discoverTransparentAddresses(DB_ACCOUNT_ID, DEEP_GAP_LIMIT, ZcashSession.DISCOVERY_CONCURRENCY)
        }
        assertEquals(1, discovery.discoveredEpoch.value)
    }

    @Test
    fun discoverForEpoch_deepSweepNotRequired_shallowRecord_doesNotWalkACoveredEpochAgain() = runTest {
        stubDiscovery(wallet, FakeCoverage(certified = true, gap = STEADY_GAP_LIMIT))
        val session = session(deepSweepRequired = false)
        advanceUntilIdle()
        session.discoverForEpoch(1)

        session.discoverForEpoch(1)

        coVerify(exactly = 1) { wallet.discoverTransparentAddresses(any(), any(), any()) }
    }
}
