package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.R
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.OfflineZcashSignRequest
import cash.p.terminal.core.managers.NotBroadcastException
import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import cash.p.zcash.Balance
import cash.p.zcash.BroadcastResult
import cash.p.zcash.PaymentOptions
import cash.p.zcash.Pool
import cash.p.zcash.PoolBalance
import cash.p.zcash.PoolSet
import cash.p.zcash.PreparedTransaction
import cash.p.zcash.SyncState
import cash.p.zcash.TransactionPlan
import cash.p.zcash.ZcashException
import cash.p.zcash.ZcashSdk
import cash.p.zcash.deriveSpendingKey
import cash.p.zcash.transactionId
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * Every plan a Zcash wallet makes may spend only from the pools its address spec owns: a
 * transparent wallet never touches shielded notes, and a shortfall is refused rather than
 * topped up from another pool.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZcashAdapterSourcePoolsTest : ZcashAdapterTestFixture() {

    /** Options of every plan the adapter asked for — they expose the source pools it restricts to. */
    private val plannedOptions = mutableListOf<PaymentOptions>()

    /** What `prepare` does instead of returning — the insufficient-funds cases fail here. */
    private var prepareFailure: (() -> Nothing)? = null

    override fun stubWallet() {
        coEvery { zcashWallet.prepare(any(), any(), any()) } coAnswers {
            plannedOptions += thirdArg<PaymentOptions>()
            prepareFailure?.invoke() ?: PREPARED
        }
        coEvery { zcashWallet.plan(any()) } returns TransactionPlan(
            height = HEIGHT,
            inputs = emptyList(),
            outputs = emptyList(),
            fee = FEE_ZAT,
            canSign = true,
            canBroadcast = true,
        )
        coEvery { zcashWallet.sign(any(), any(), any()) } returns PREPARED
        coEvery { zcashWallet.extract(any()) } returns RAW
        coEvery { zcashWallet.broadcast(any(), any(), any(), any()) } returns
            BroadcastResult(errorCode = 0, message = TX_ID)
    }

    @Before
    fun stubSdkKeyDerivation() {
        mockkStatic("cash.p.zcash.ZcashSdkKt")
        coEvery { ZcashSdk.deriveSpendingKey(any(), any(), any(), any()) } returns ByteArray(32)
        coEvery { ZcashSdk.transactionId(any()) } returns TX_ID
    }

    // region Spec -> pools

    @Test
    fun pools_transparentSpec_isTransparentOnly() {
        assertEquals(PoolSet.of(Pool.TRANSPARENT), AddressSpecType.Transparent.pools())
    }

    @Test
    fun pools_shieldedSpec_isSaplingOnly() {
        assertEquals(PoolSet.of(Pool.SAPLING), AddressSpecType.Shielded.pools())
    }

    @Test
    fun pools_unifiedSpec_isOrchardAndIronwood() {
        assertEquals(
            PoolSet.of(Pool.ORCHARD, Pool.IRONWOOD),
            AddressSpecType.Unified.pools(),
        )
    }

    @Test
    fun pools_noSpec_isSaplingOnly() {
        assertEquals(PoolSet.of(Pool.SAPLING), null.pools())
    }

    // endregion

    // region Pool-restricted planning

    // Each site gets a different spec on purpose: a hardcoded pool set would pass at most one.
    // Shielding is the exception — it always spends transparent funds, whatever the spec.

    @Test
    fun fee_transparentSpec_plansFromTransparentPoolOnly() = runTest(dispatcher) {
        startAdapter(AddressSpecType.Transparent)
        emitSessionSyncState(SyncState.Synced)
        advanceUntilIdle()
        emitSessionBalance(poolBalance(Pool.TRANSPARENT to Balance(available = 100_000_000)))
        advanceUntilIdle()

        val options = plannedOptions.single()
        assertEquals(PoolSet.of(Pool.TRANSPARENT), options.sourcePools)
        assertTrue("the fee probe spends the whole balance", options.recipientPaysFee)
    }

    @Test
    fun send_shieldedSpec_plansFromSaplingPoolOnly() = runTest(dispatcher) {
        startAdapter(AddressSpecType.Shielded)

        adapter.send(AMOUNT, RECIPIENT, memo = "")

        assertEquals(PoolSet.of(Pool.SAPLING), plannedOptions.single().sourcePools)
    }

    @Test
    fun signOffline_unifiedSpec_plansFromOrchardAndIronwoodPools() = runTest(dispatcher) {
        startAdapter(AddressSpecType.Unified)

        adapter.signOffline(OfflineZcashSignRequest(AMOUNT, RECIPIENT, memo = ""))

        assertEquals(
            PoolSet.of(Pool.ORCHARD, Pool.IRONWOOD),
            plannedOptions.single().sourcePools,
        )
    }

    @Test
    fun proposeShielding_shieldedSpec_stillPlansFromTheTransparentPool() = runTest(dispatcher) {
        startAdapter(AddressSpecType.Shielded)

        adapter.proposeShielding(
            ZcashAdapter.ShieldingTarget(address = SHIELDED_RECIPIENT, amount = 100_000_000L)
        )

        assertEquals(PoolSet.of(Pool.TRANSPARENT), plannedOptions.single().sourcePools)
    }

    // endregion

    // region Shortfall is refused, never topped up from another pool

    @Test
    fun send_ownTransaction_reservesAndBroadcastsRequiringOwnInputs() = runTest(dispatcher) {
        startAdapter(AddressSpecType.Transparent)

        adapter.send(AMOUNT, RECIPIENT, memo = "")

        coVerifyOrder {
            session.reserveForBroadcast(any(), requireOwnInputs = true)
            zcashWallet.broadcast(DB_ACCOUNT_ID, any(), HEIGHT, requireOwnInputs = true)
        }
    }

    @Test
    fun send_noFeasibleNoteSelection_reportsInsufficientBalance() = runTest(dispatcher) {
        prepareFailure = { throw ZcashException("No feasible note selection found") }
        startAdapter(AddressSpecType.Transparent)

        val failure = failureOf { adapter.send(AMOUNT, RECIPIENT, memo = "") }

        assertTrue("planning fails before the broadcast", failure is NotBroadcastException)
        assertEquals(
            R.string.Swap_ErrorInsufficientBalance,
            (failure.cause as LocalizedException).errorTextRes,
        )
    }

    @Test
    fun send_otherPlanningFailure_keepsTheNativeMessage() = runTest(dispatcher) {
        prepareFailure = { throw ZcashException(OTHER_FAILURE) }
        startAdapter(AddressSpecType.Transparent)

        val failure = failureOf { adapter.send(AMOUNT, RECIPIENT, memo = "") }

        assertEquals(OTHER_FAILURE, failure.cause?.message)
    }

    @Test
    fun signOffline_noFeasibleNoteSelection_reportsInsufficientBalance() = runTest(dispatcher) {
        prepareFailure = { throw ZcashException("No feasible note selection found") }
        startAdapter(AddressSpecType.Unified)

        val failure = failureOf {
            adapter.signOffline(OfflineZcashSignRequest(AMOUNT, RECIPIENT, memo = ""))
        }

        assertEquals(
            R.string.Swap_ErrorInsufficientBalance,
            (failure as LocalizedException).errorTextRes,
        )
    }

    // endregion

    // region Maximum spendable

    @Test
    fun maxSpendableBalance_beforeTheFirstSnapshot_isZero() = runTest(dispatcher) {
        startAdapter(AddressSpecType.Transparent)

        assertBigDecimalEquals("0", adapter.maxSpendableBalance)
    }

    @Test
    fun maxSpendableBalance_snapshotForOwnPools_isTheAdvertisedAmount() = runTest(dispatcher) {
        startAdapter(AddressSpecType.Transparent)

        emitMaxSpendable(
            PoolSet.of(Pool.TRANSPARENT) to 70_000_000L,
            PoolSet.of(Pool.SAPLING) to 5_000_000L,
        )

        assertBigDecimalEquals("0.7", adapter.maxSpendableBalance)
    }

    @Test
    fun maxSpendableBalance_snapshotWithoutOwnPools_isZero() = runTest(dispatcher) {
        startAdapter(AddressSpecType.Unified)

        emitMaxSpendable(PoolSet.of(Pool.TRANSPARENT) to 70_000_000L)

        assertBigDecimalEquals("0", adapter.maxSpendableBalance)
    }

    // endregion

    // region Harness

    private fun TestScope.startAdapter(spec: AddressSpecType?) {
        adapter = createAdapter(spec)
        adapter.start()
        advanceUntilIdle()
        plannedOptions.clear()
    }

    /** A maximum arrives with the balance it was computed from, as the session publishes it. */
    private fun TestScope.emitMaxSpendable(vararg maxSpendable: Pair<PoolSet, Long>) {
        emitSessionBalance(
            balance = poolBalance(Pool.TRANSPARENT to Balance(available = 100_000_000)),
            maxSpendable = maxSpendable.toMap(),
        )
        advanceUntilIdle()
    }

    private fun poolBalance(vararg pools: Pair<Pool, Balance>) = PoolBalance(pools.toMap())

    /** `assertThrows` cannot call a suspend function, so the failure is captured here. */
    private suspend fun failureOf(block: suspend () -> Unit): Throwable = try {
        block()
        throw AssertionError("Expected a failure, but the call succeeded")
    } catch (e: AssertionError) {
        throw e
    } catch (e: Throwable) {
        e
    }

    private fun assertBigDecimalEquals(expected: String, actual: BigDecimal) {
        assertEquals(BigDecimal(expected).stripTrailingZeros(), actual.stripTrailingZeros())
    }

    private companion object {
        const val HEIGHT = 3_428_200
        const val FEE_ZAT = 10_000L
        const val RECIPIENT = "t1RecipientAddress"
        const val SHIELDED_RECIPIENT = "u1ShieldedRecipient"
        const val OTHER_FAILURE = "Wallet database is locked"
        val AMOUNT: BigDecimal = BigDecimal("0.1")
        val RAW = byteArrayOf(1, 2, 3, 4)
        val PREPARED = PreparedTransaction(byteArrayOf(9, 9, 9, 9))
        const val TX_ID =
            "b7c4a1f0d3e2b5a698877665544332211ffeeddccbbaa99887766554433221100"
    }

    // endregion
}
