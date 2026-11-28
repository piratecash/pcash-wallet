package cash.p.terminal.core.managers

import cash.p.terminal.core.ITokenTransferCostEstimator
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.TokenTransferCost
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class TokenTransferCostCacheTest {

    private val timeSource = TestTimeSource()

    private class FakeEstimator(var cost: TokenTransferCost?) : ITokenTransferCostEstimator {
        var calls = 0
        var dispatcher: CoroutineDispatcher? = null

        override suspend fun estimateTransferCost(): TokenTransferCost {
            calls++
            dispatcher = currentCoroutineContext()[ContinuationInterceptor] as CoroutineDispatcher
            return cost ?: error("rpc failed")
        }
    }

    private fun TestScope.cache(io: CoroutineDispatcher? = null): TokenTransferCostCache {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return TokenTransferCostCache(
            TestDispatcherProvider(dispatcher, backgroundScope, io = io ?: dispatcher),
            timeSource,
        )
    }

    @Test
    fun get_withinTtl_reusesEstimate() = runTest {
        val cache = cache()
        val estimator = FakeEstimator(COST)

        cache.get(KEY, estimator)
        timeSource += 9.minutes
        val result = cache.get(KEY, estimator)

        assertEquals(COST, result?.cost)
        assertEquals(1, estimator.calls)
    }

    @Test
    fun get_afterTtl_estimatesAgain() = runTest {
        val cache = cache()
        val estimator = FakeEstimator(COST)

        cache.get(KEY, estimator)
        timeSource += 10.minutes + 1.seconds
        cache.get(KEY, estimator)

        assertEquals(2, estimator.calls)
    }

    @Test
    fun get_estimateFails_returnsNullAndRetriesNextTime() = runTest {
        val cache = cache()
        val estimator = FakeEstimator(null)

        assertNull(cache.get(KEY, estimator))
        estimator.cost = COST

        assertEquals(COST, cache.get(KEY, estimator)?.cost)
        assertEquals(2, estimator.calls)
    }

    @Test
    fun get_differentKeys_estimateSeparately() = runTest {
        val cache = cache()
        val estimator = FakeEstimator(COST)

        cache.get(KEY, estimator)
        cache.get("other", estimator)

        assertEquals(2, estimator.calls)
    }

    @Test
    fun get_nullKey_neverCaches() = runTest {
        val cache = cache()
        val estimator = FakeEstimator(COST)

        cache.get(null, estimator)
        cache.get(null, estimator)

        assertEquals(2, estimator.calls)
    }

    @Test
    fun get_estimate_runsOnIoDispatcher() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val estimator = FakeEstimator(COST)

        cache(io).get(KEY, estimator)

        assertSame(io, estimator.dispatcher)
    }

    private companion object {
        const val KEY = "account|token"
        val COST = TokenTransferCost(fee = BigDecimal("0.01"), requiredBalance = BigDecimal("0.05"))
    }
}
