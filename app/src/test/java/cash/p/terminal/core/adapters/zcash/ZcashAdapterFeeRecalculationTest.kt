package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import cash.p.zcash.Balance
import cash.p.zcash.Pool
import cash.p.zcash.PoolBalance
import cash.p.zcash.PreparedTransaction
import cash.p.zcash.Recipient
import cash.p.zcash.SyncState
import cash.p.zcash.TransactionPlan
import io.mockk.coEvery
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.nio.ByteBuffer

/**
 * Tests for ZcashAdapter fee recalculation and unified balance after the Ironwood (NU6.3) switch.
 *
 * The harness is the shared [ZcashAdapterTestFixture]: the session state is an in-memory flow, so
 * the "sync -> funds arrive" sequence plays out deterministically on virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZcashAdapterFeeRecalculationTest : ZcashAdapterTestFixture() {

    /** Amounts the adapter planned a spend for — they expose every fee calculation. */
    private val plannedAmounts = mutableListOf<Long>()
    private val plannedFees = mutableListOf<Long>()

    /** Fee the plan reports, or `null` — then planning fails the way the SDK fails it. */
    private var feeFor: (Long) -> Long? = { MINERS_FEE_ZAT }

    /** What `prepare` does before returning — the test uses it to keep a calculation in flight. */
    private var beforePrepare: suspend (Long) -> Unit = { }

    override fun stubWallet() {
        coEvery { zcashWallet.prepare(any(), any(), any()) } coAnswers {
            val requested = secondArg<List<Recipient>>().single().amount
            plannedAmounts += requested
            val fee = feeFor(requested)
            beforePrepare(requested)
            PreparedTransaction(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(fee ?: -1).array())
        }
        coEvery { zcashWallet.plan(any()) } coAnswers {
            val fee = ByteBuffer.wrap(firstArg<ByteArray>()).long
                .takeIf { it >= 0 } ?: error("Insufficient funds")
            plannedFees += fee
            TransactionPlan(
                height = 0,
                inputs = emptyList(),
                outputs = emptyList(),
                fee = fee,
                canSign = true,
                canBroadcast = true,
            )
        }
    }

    // region Unified balance

    @Test
    fun balanceData_unifiedSpec_sumsOrchardAndIronwoodPools() = runTest(dispatcher) {
        adapter = createAdapter(AddressSpecType.Unified)
        adapter.start()
        advanceUntilIdle()
        emitSessionBalance(
            poolBalance(
                Pool.ORCHARD to Balance(
                    available = 100_000_000,
                    changePending = 1_000,
                    valuePending = 2_000,
                ),
                Pool.IRONWOOD to Balance(
                    available = 50_000_000,
                    changePending = 3_000,
                    valuePending = 4_000,
                ),
            )
        )
        advanceUntilIdle()

        // 1.5 ZEC available, 0.0001 in flight — the sum of both pools, not Orchard alone.
        assertBigDecimalEquals("1.5", adapter.balanceData.available)
        assertBigDecimalEquals("0.0001", adapter.balanceData.pending)
    }

    @Test
    fun balanceData_unifiedSpec_ignoresSaplingAndTransparentPools() = runTest(dispatcher) {
        adapter = createAdapter(AddressSpecType.Unified)
        adapter.start()
        advanceUntilIdle()
        emitSessionBalance(
            poolBalance(
                Pool.SAPLING to Balance(700_000_000),
                Pool.ORCHARD to Balance(100_000_000),
                Pool.IRONWOOD to Balance(50_000_000),
                Pool.TRANSPARENT to Balance(900_000_000),
            )
        )
        advanceUntilIdle()

        assertBigDecimalEquals("1.5", adapter.balanceData.available)
    }

    // endregion

    // region Fee recalculation

    @Test
    fun fee_ironwoodFundsArriveAfterSync_isRecalculated() = runTest(dispatcher) {
        feeFor = { MINERS_FEE_ZAT }
        startSyncedAdapter()

        emitBalance(orchard = 100_000_000)
        assertEquals(1, plannedAmounts.size)
        assertEquals(100_000_000L, plannedAmounts.last())

        // After NU6.3 activation change arrives in Ironwood: the available balance grew and the
        // fee must be recalculated, now across two bundles.
        feeFor = { 30_000L }
        emitBalance(orchard = 100_000_000, ironwood = 25_000_000)

        assertEquals(2, plannedAmounts.size)
        assertEquals(125_000_000L, plannedAmounts.last())
        assertFeeEquals(30_000L)
    }

    @Test
    fun fee_balanceUnchanged_isNotRecalculated() = runTest(dispatcher) {
        feeFor = { 30_000L }
        startSyncedAdapter()
        emitBalance(orchard = 100_000_000)
        assertEquals(1, plannedAmounts.size)

        // A repeated Synced without a balance change must not spawn another calculation.
        resync()

        assertEquals(1, plannedAmounts.size)
    }

    @Test
    fun fee_stillTheDefaultMinersFee_isRecalculatedOnResync() = runTest(dispatcher) {
        feeFor = { MINERS_FEE_ZAT }
        startSyncedAdapter()
        emitBalance(orchard = 100_000_000)
        assertEquals(1, plannedAmounts.size)

        // While the published fee is still the default one, the balance is not enough to conclude
        // the fee is current: ZIP-317 also depends on the proposal target height, which changes at
        // NU6.3 activation without touching any balance field.
        resync()

        assertEquals(2, plannedAmounts.size)
    }

    @Test
    fun fee_fundsMoveBetweenPoolsAtEqualAvailable_isRecalculated() = runTest(dispatcher) {
        feeFor = { MINERS_FEE_ZAT }
        startSyncedAdapter()
        emitBalance(orchard = 100_000_000)
        assertEquals(1, plannedAmounts.size)

        // The turnstile moves funds from Orchard to Ironwood: the total is the same but the pool
        // composition differs, and under ZIP-317 the per-bundle fee changes.
        feeFor = { 20_000L }
        emitBalance(ironwood = 100_000_000)

        assertEquals(2, plannedAmounts.size)
        assertEquals(100_000_000L, plannedAmounts.last())
        assertFeeEquals(20_000L)
    }

    @Test
    fun fee_planningFails_keepsTheDefaultMinersFee() = runTest(dispatcher) {
        feeFor = { null }
        startSyncedAdapter()
        emitBalance(orchard = 100_000_000)

        assertEquals(1, plannedAmounts.size)
        assertBigDecimalEquals(ZcashAdapter.MINERS_FEE.toPlainString(), adapter.fee.value)

        // Nothing was published, so the next trigger calculates again instead of treating the
        // fee as already known.
        resync()

        assertEquals(2, plannedAmounts.size)
    }

    @Test
    fun fee_supersededCalculation_doesNotPublishItsStaleResult() = runTest(dispatcher) {
        val stuck = CompletableDeferred<Unit>()
        feeFor = { 10_000L }
        startSyncedAdapter()

        // The calculation for the old balance goes "to the network" and gets stuck past its last
        // cancellation point: on a real IO dispatcher the thread is preempted exactly here.
        beforePrepare = { withContext(NonCancellable) { stuck.await() } }
        emitBalance(orchard = 100_000_000)
        assertEquals(1, plannedAmounts.size)

        // Change arrived in Ironwood — this calculation supersedes the stuck one and publishes
        // its own fee.
        beforePrepare = { }
        feeFor = { 50_000L }
        emitBalance(orchard = 100_000_000, ironwood = 25_000_000)
        assertFeeEquals(50_000L)

        // The stuck calculation returns with the fee for a balance that is no longer current.
        stuck.complete(Unit)
        advanceUntilIdle()

        // It must not overwrite the fresh result: otherwise the fee stays understated until the
        // next balance change.
        assertFeeEquals(50_000L)
    }

    @Test
    fun fee_sameBalanceCalculationSuperseded_doesNotPublishItsStaleResult() = runTest(dispatcher) {
        val stuck = CompletableDeferred<Unit>()
        feeFor = { 10_000L }
        startSyncedAdapter()

        beforePrepare = { withContext(NonCancellable) { stuck.await() } }
        emitBalance(orchard = 100_000_000)
        assertEquals(1, plannedAmounts.size)

        // A later chain target can change ZIP-317 planning while the pool balance stays equal.
        beforePrepare = { }
        feeFor = { 50_000L }
        resync()
        assertEquals(2, plannedAmounts.size)
        assertEquals(listOf(50_000L), plannedFees)
        assertFeeEquals(50_000L)

        stuck.complete(Unit)
        advanceUntilIdle()

        assertFeeEquals(50_000L)
    }

    @Test
    fun fee_zeroBalance_resetsToTheDefaultMinersFee() = runTest(dispatcher) {
        feeFor = { 30_000L }
        startSyncedAdapter()
        emitBalance(orchard = 100_000_000)
        assertFeeEquals(30_000L)

        emitBalance(orchard = 0)

        // At a zero balance nothing is planned — the fee falls back to the base one.
        assertEquals(1, plannedAmounts.size)
        assertBigDecimalEquals(ZcashAdapter.MINERS_FEE.toPlainString(), adapter.fee.value)
    }

    // endregion

    // region Harness

    /** Brings the adapter up to Synced — only then does a balance change move the fee. */
    private fun TestScope.startSyncedAdapter() {
        adapter = createAdapter(AddressSpecType.Unified)
        adapter.start()
        advanceUntilIdle()
        emitSessionSyncState(SyncState.Synced)
        advanceUntilIdle()
        plannedAmounts.clear()
    }

    private fun TestScope.emitBalance(orchard: Long = 0, ironwood: Long = 0) {
        emitSessionBalance(
            poolBalance(
                Pool.ORCHARD to Balance(orchard),
                Pool.IRONWOOD to Balance(ironwood),
            )
        )
        advanceUntilIdle()
    }

    /** Cycles the sync state Syncing -> Synced without touching the balance. */
    private fun TestScope.resync() {
        emitSessionSyncState(SyncState.Syncing(current = 1, target = 2))
        advanceUntilIdle()
        emitSessionSyncState(SyncState.Synced)
        advanceUntilIdle()
    }

    private fun poolBalance(vararg pools: Pair<Pool, Balance>) = PoolBalance(pools.toMap())

    private fun assertFeeEquals(zatoshi: Long) =
        assertBigDecimalEquals(zatoshi.convertZatoshiToZec().toPlainString(), adapter.fee.value)

    private fun assertBigDecimalEquals(expected: String, actual: BigDecimal) {
        assertEquals(BigDecimal(expected).stripTrailingZeros(), actual.stripTrailingZeros())
    }

    private companion object {
        const val MINERS_FEE_ZAT = 10_000L
    }

    // endregion
}
