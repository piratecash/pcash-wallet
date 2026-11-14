package cash.p.terminal.modules.send.zcash

import cash.p.terminal.core.ISendZcashAdapter
import cash.p.terminal.core.ZcashFeeEstimate
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.amount.SendAmountService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class ZcashSendFeeQuoteTest {

    private val dispatcher = StandardTestDispatcher()
    private val adapterFee = MutableStateFlow(ADAPTER_FEE)
    private val balanceUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val adapter = mockk<ISendZcashAdapter>(relaxed = true) {
        every { fee } returns adapterFee
        every { balanceUpdatedFlow } returns balanceUpdates
        every { maxSpendableBalance } returns LOWER_ESTIMATE
        coEvery { estimateFee(any(), any(), any()) } returns ZcashFeeEstimate.Planned(ESTIMATE)
        coEvery { maxSendable(any(), any()) } returns MAX_SENDABLE
    }

    @Test
    fun quote_noAmountOrAddress_isTheAdapterFeeWithoutPlanning() = runTest(dispatcher) {
        val quote = startedQuote()

        quote.send(amount = null, address = ADDRESS)
        settle()
        assertQuote(quote, ADAPTER_FEE, isCurrent = true)

        quote.send(amount = AMOUNT, address = null)
        settle()
        assertQuote(quote, ADAPTER_FEE, isCurrent = true)

        coVerify(exactly = 0) { adapter.estimateFee(any(), any(), any()) }
    }

    @Test
    fun quote_amountAndAddressKnown_isTheEstimateForThem() = runTest(dispatcher) {
        val quote = startedQuote()

        quote.send(AMOUNT, ADDRESS, memo = "memo")
        settle()

        assertQuote(quote, ESTIMATE, isCurrent = true)
        coVerify(exactly = 1) { adapter.estimateFee(AMOUNT, ADDRESS, "memo") }
    }

    @Test
    fun quote_requestChanged_isNotCurrentFromTheSameCall() = runTest(dispatcher) {
        val quote = startedQuote()
        quote.send(AMOUNT, ADDRESS)
        settle()

        quote.send(OTHER_AMOUNT, ADDRESS)

        assertFalse(quote.quote.value.isCurrent)
    }

    @Test
    fun quote_sameRequestAgain_staysCurrentWithoutPlanning() = runTest(dispatcher) {
        val quote = startedQuote()
        quote.send(AMOUNT, ADDRESS)
        settle()

        quote.send(AMOUNT, ADDRESS)
        settle()

        assertTrue(quote.quote.value.isCurrent)
        coVerify(exactly = 1) { adapter.estimateFee(any(), any(), any()) }
    }

    @Test
    fun quote_rapidAmountChanges_plansOnlyTheLastOne() = runTest(dispatcher) {
        val quote = startedQuote()

        quote.send(AMOUNT, ADDRESS)
        advanceTimeBy(BELOW_DEBOUNCE_MS)
        quote.send(OTHER_AMOUNT, ADDRESS)
        settle()

        coVerify(exactly = 0) { adapter.estimateFee(AMOUNT, any(), any()) }
        coVerify(exactly = 1) { adapter.estimateFee(OTHER_AMOUNT, any(), any()) }
    }

    @Test
    fun quote_planningFails_isTheAdapterFeeAndCurrent() = runTest(dispatcher) {
        coEvery { adapter.estimateFee(any(), any(), any()) } returns ZcashFeeEstimate.Unavailable
        val quote = startedQuote()

        quote.send(AMOUNT, ADDRESS)
        settle()

        assertQuote(quote, ADAPTER_FEE, isCurrent = true)
    }

    @Test
    fun quote_noFeasiblePlan_isFlaggedWithTheAdapterFee() = runTest(dispatcher) {
        coEvery { adapter.estimateFee(any(), any(), any()) } returns ZcashFeeEstimate.InsufficientBalance
        val quote = startedQuote()

        quote.send(AMOUNT, ADDRESS)
        settle()

        assertEquals(ZcashSendFeeQuote.Quote(ADAPTER_FEE, isCurrent = true, insufficientBalance = true), quote.quote.value)
    }

    @Test
    fun quote_amountChangedAfterNoFeasiblePlan_clearsTheFlag() = runTest(dispatcher) {
        coEvery { adapter.estimateFee(AMOUNT, any(), any()) } returns ZcashFeeEstimate.InsufficientBalance
        val quote = startedQuote()
        quote.send(AMOUNT, ADDRESS)
        settle()

        quote.send(OTHER_AMOUNT, ADDRESS)
        settle()

        assertQuote(quote, ESTIMATE, isCurrent = true)
    }

    @Test
    fun quote_amountNoLongerValidAfterNoFeasiblePlan_clearsTheFlag() = runTest(dispatcher) {
        coEvery { adapter.estimateFee(any(), any(), any()) } returns ZcashFeeEstimate.InsufficientBalance
        val quote = startedQuote()
        quote.send(AMOUNT, ADDRESS)
        settle()

        quote.send(amount = null, address = ADDRESS)
        settle()

        assertQuote(quote, ADAPTER_FEE, isCurrent = true)
    }

    @Test
    fun quote_supersededEstimateReturnsLate_isDropped() = runTest(dispatcher) {
        val stuck = CompletableDeferred<ZcashFeeEstimate>()
        coEvery { adapter.estimateFee(AMOUNT, any(), any()) } coAnswers { stuck.await() }
        coEvery { adapter.estimateFee(OTHER_AMOUNT, any(), any()) } returns ZcashFeeEstimate.Planned(OTHER_ESTIMATE)
        val quote = startedQuote()
        quote.send(AMOUNT, ADDRESS)
        settle()

        quote.send(OTHER_AMOUNT, ADDRESS)
        stuck.complete(ZcashFeeEstimate.Planned(ESTIMATE))
        settle()

        assertQuote(quote, OTHER_ESTIMATE, isCurrent = true)
    }

    @Test
    fun quote_estimateInFlight_isNotCurrent() = runTest(dispatcher) {
        val inFlight = CompletableDeferred<ZcashFeeEstimate>()
        coEvery { adapter.estimateFee(any(), any(), any()) } coAnswers { inFlight.await() }
        val quote = startedQuote()

        quote.send(AMOUNT, ADDRESS)
        settle()
        assertFalse(quote.quote.value.isCurrent)

        inFlight.complete(ZcashFeeEstimate.Planned(ESTIMATE))
        settle()
        assertQuote(quote, ESTIMATE, isCurrent = true)
    }

    @Test
    fun quote_balanceUpdated_plansAgainAndIsNotCurrentMeanwhile() = runTest(dispatcher) {
        val quote = startedQuote()
        quote.send(AMOUNT, ADDRESS)
        settle()
        coEvery { adapter.estimateFee(any(), any(), any()) } returns ZcashFeeEstimate.Planned(OTHER_ESTIMATE)

        balanceUpdates.emit(Unit)
        runCurrent()
        assertFalse(quote.quote.value.isCurrent)

        settle()
        assertQuote(quote, OTHER_ESTIMATE, isCurrent = true)
    }

    @Test
    fun quote_adapterFeeChangedAtRateBoundary_plansAgain() = runTest(dispatcher) {
        val quote = startedQuote()
        quote.send(AMOUNT, ADDRESS)
        settle()
        coEvery { adapter.estimateFee(any(), any(), any()) } returns ZcashFeeEstimate.Planned(OTHER_ESTIMATE)

        adapterFee.value = OTHER_ESTIMATE
        settle()

        assertQuote(quote, OTHER_ESTIMATE, isCurrent = true)
    }

    @Test
    fun availableBalance_noAddress_isTheLowerEstimate() = runTest(dispatcher) {
        val quote = startedQuote()
        settle()

        assertEquals(LOWER_ESTIMATE, quote.availableBalance.value)
        coVerify(exactly = 0) { adapter.maxSendable(any(), any()) }
    }

    @Test
    fun availableBalance_addressKnown_isTheMaxForThatRecipientAndMemo() = runTest(dispatcher) {
        val quote = startedQuote()

        quote.send(amount = null, address = ADDRESS, memo = "memo")
        settle()

        assertEquals(MAX_SENDABLE, quote.availableBalance.value)
        coVerify(exactly = 1) { adapter.maxSendable(ADDRESS, "memo") }
    }

    @Test
    fun availableBalance_planningFails_isTheLowerEstimate() = runTest(dispatcher) {
        coEvery { adapter.maxSendable(any(), any()) } returns null
        val quote = startedQuote()

        quote.send(amount = null, address = ADDRESS)
        settle()

        assertEquals(LOWER_ESTIMATE, quote.availableBalance.value)
    }

    @Test
    fun availableBalance_amountChanged_doesNotPlanTheMaximumAgain() = runTest(dispatcher) {
        val quote = startedQuote()
        quote.send(AMOUNT, ADDRESS)
        settle()

        quote.send(OTHER_AMOUNT, ADDRESS)
        settle()

        coVerify(exactly = 1) { adapter.maxSendable(any(), any()) }
    }

    @Test
    fun availableBalance_balanceUpdated_plansTheMaximumAgain() = runTest(dispatcher) {
        val quote = startedQuote()
        quote.send(amount = null, address = ADDRESS)
        settle()
        coEvery { adapter.maxSendable(any(), any()) } returns OTHER_AMOUNT

        balanceUpdates.emit(Unit)
        settle()

        assertEquals(OTHER_AMOUNT, quote.availableBalance.value)
    }

    @Test
    fun availableBalance_adapterFeeChangedAtRateBoundary_plansTheMaximumAgain() = runTest(dispatcher) {
        val quote = startedQuote()
        quote.send(amount = null, address = ADDRESS)
        settle()
        coEvery { adapter.maxSendable(any(), any()) } returns OTHER_AMOUNT

        adapterFee.value = OTHER_ESTIMATE
        settle()

        assertEquals(OTHER_AMOUNT, quote.availableBalance.value)
    }

    @Test
    fun quote_planningFailsThenRecovers_updatesTheFeeForTheUnchangedInput() = runTest(dispatcher) {
        coEvery { adapter.estimateFee(any(), any(), any()) } returns ZcashFeeEstimate.Unavailable
        val quote = startedQuote()
        quote.send(AMOUNT, ADDRESS)
        settle()
        coEvery { adapter.estimateFee(any(), any(), any()) } returns ZcashFeeEstimate.Planned(ESTIMATE)

        advanceTimeBy(BELOW_RETRY_MS)
        runCurrent()
        assertQuote(quote, ADAPTER_FEE, isCurrent = true)

        advanceTimeBy(RETRY_MS)
        runCurrent()
        assertQuote(quote, ESTIMATE, isCurrent = true)
    }

    @Test
    fun quote_stillFailingOnRetry_keepsTheAdapterFeeCurrentAndTriesAgain() = runTest(dispatcher) {
        coEvery { adapter.estimateFee(any(), any(), any()) } returns ZcashFeeEstimate.Unavailable
        val quote = startedQuote()
        quote.send(AMOUNT, ADDRESS)
        settle()

        advanceTimeBy(2 * RETRY_MS)
        runCurrent()

        assertQuote(quote, ADAPTER_FEE, isCurrent = true)
        coVerify(exactly = 3) { adapter.estimateFee(AMOUNT, any(), any()) }
    }

    @Test
    fun quote_recoversToNoFeasiblePlan_flagsTheAmount() = runTest(dispatcher) {
        coEvery { adapter.estimateFee(any(), any(), any()) } returns ZcashFeeEstimate.Unavailable
        val quote = startedQuote()
        quote.send(AMOUNT, ADDRESS)
        settle()
        coEvery { adapter.estimateFee(any(), any(), any()) } returns ZcashFeeEstimate.InsufficientBalance

        advanceTimeBy(RETRY_MS)
        runCurrent()

        assertEquals(ZcashSendFeeQuote.Quote(ADAPTER_FEE, isCurrent = true, insufficientBalance = true), quote.quote.value)
    }

    @Test
    fun quote_newInputDuringTheWait_cancelsTheRetry() = runTest(dispatcher) {
        coEvery { adapter.estimateFee(AMOUNT, any(), any()) } returns ZcashFeeEstimate.Unavailable
        val quote = startedQuote()
        quote.send(AMOUNT, ADDRESS)
        settle()

        quote.send(OTHER_AMOUNT, ADDRESS)
        settle()
        advanceTimeBy(2 * RETRY_MS)
        runCurrent()

        assertQuote(quote, ESTIMATE, isCurrent = true)
        coVerify(exactly = 1) { adapter.estimateFee(AMOUNT, any(), any()) }
        coVerify(exactly = 1) { adapter.estimateFee(OTHER_AMOUNT, any(), any()) }
    }

    @Test
    fun quote_plannedResult_doesNotRetry() = runTest(dispatcher) {
        val quote = startedQuote()
        quote.send(AMOUNT, ADDRESS)
        settle()

        advanceTimeBy(2 * RETRY_MS)
        runCurrent()

        coVerify(exactly = 1) { adapter.estimateFee(any(), any(), any()) }
    }

    @Test
    fun quote_noFeasiblePlan_doesNotRetry() = runTest(dispatcher) {
        coEvery { adapter.estimateFee(any(), any(), any()) } returns ZcashFeeEstimate.InsufficientBalance
        val quote = startedQuote()
        quote.send(AMOUNT, ADDRESS)
        settle()

        advanceTimeBy(2 * RETRY_MS)
        runCurrent()

        coVerify(exactly = 1) { adapter.estimateFee(any(), any(), any()) }
    }

    @Test
    fun quote_paymentRefused_carriesTheReasonWithTheAdapterFeeAndDoesNotRetry() = runTest(dispatcher) {
        coEvery { adapter.estimateFee(any(), any(), any()) } returns ZcashFeeEstimate.Refused(REFUSAL)
        val quote = startedQuote()
        quote.send(AMOUNT, ADDRESS)
        settle()

        advanceTimeBy(2 * RETRY_MS)
        runCurrent()

        assertEquals(ZcashSendFeeQuote.Quote(ADAPTER_FEE, isCurrent = true, refusal = REFUSAL), quote.quote.value)
        coVerify(exactly = 1) { adapter.estimateFee(any(), any(), any()) }
    }

    @Test
    fun availableBalance_planningFailsThenRecovers_updatesTheMaximumForTheUnchangedInput() = runTest(dispatcher) {
        coEvery { adapter.maxSendable(any(), any()) } returns null
        val quote = startedQuote()
        quote.send(amount = null, address = ADDRESS)
        settle()
        coEvery { adapter.maxSendable(any(), any()) } returns MAX_SENDABLE

        advanceTimeBy(BELOW_RETRY_MS)
        runCurrent()
        assertEquals(LOWER_ESTIMATE, quote.availableBalance.value)

        advanceTimeBy(RETRY_MS)
        runCurrent()
        assertEquals(MAX_SENDABLE, quote.availableBalance.value)
    }

    @Test
    fun availableBalance_newRecipientDuringTheWait_cancelsTheRetry() = runTest(dispatcher) {
        coEvery { adapter.maxSendable(ADDRESS, any()) } returns null
        val quote = startedQuote()
        quote.send(amount = null, address = ADDRESS)
        settle()

        quote.send(amount = null, address = OTHER_ADDRESS)
        settle()
        advanceTimeBy(2 * RETRY_MS)
        runCurrent()

        assertEquals(MAX_SENDABLE, quote.availableBalance.value)
        coVerify(exactly = 1) { adapter.maxSendable(ADDRESS, any()) }
        coVerify(exactly = 1) { adapter.maxSendable(OTHER_ADDRESS, any()) }
    }

    @Test
    fun availableBalance_realResult_doesNotRetry() = runTest(dispatcher) {
        val quote = startedQuote()
        quote.send(amount = null, address = ADDRESS)
        settle()

        advanceTimeBy(2 * RETRY_MS)
        runCurrent()

        coVerify(exactly = 1) { adapter.maxSendable(any(), any()) }
    }

    private fun TestScope.startedQuote() = ZcashSendFeeQuote(adapter).also {
        it.start(backgroundScope)
        runCurrent()
    }

    private fun TestScope.settle() {
        advanceTimeBy(ABOVE_DEBOUNCE_MS)
        runCurrent()
    }

    private fun ZcashSendFeeQuote.send(amount: BigDecimal?, address: String?, memo: String = "") = update(
        amount = SendAmountService.State(amount, null, BigDecimal.TEN, canBeSend = amount != null),
        address = SendZCashAddressService.State(
            address = address?.let(::Address),
            addressType = null,
            addressError = null,
            canBeSend = address != null,
        ),
        memo = memo,
    )

    private fun assertQuote(quote: ZcashSendFeeQuote, fee: BigDecimal, isCurrent: Boolean) {
        assertEquals(ZcashSendFeeQuote.Quote(fee, isCurrent), quote.quote.value)
    }

    private companion object {
        const val ADDRESS = "u1recipient"
        const val BELOW_DEBOUNCE_MS = 10L
        const val ABOVE_DEBOUNCE_MS = 5_000L
        const val RETRY_MS = 15_000L
        const val BELOW_RETRY_MS = 5_000L
        const val OTHER_ADDRESS = "u1other"
        val AMOUNT = BigDecimal("0.0005")
        val OTHER_AMOUNT = BigDecimal("0.0006")
        val ADAPTER_FEE = BigDecimal("0.00025")
        val ESTIMATE = BigDecimal("0.0001")
        val OTHER_ESTIMATE = BigDecimal("0.00002")
        val LOWER_ESTIMATE = BigDecimal("0.00045")
        val MAX_SENDABLE = BigDecimal("0.00065")
        val REFUSAL = IllegalStateException("too large for the device")
    }
}
