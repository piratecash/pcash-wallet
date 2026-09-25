package cash.p.terminal.modules.send.beam

import cash.p.beam.BeamBalance
import cash.p.terminal.R
import cash.p.terminal.core.managers.BeamSendCoordinator
import cash.p.terminal.core.managers.BeamSendCoordinator.Outcome
import cash.p.terminal.core.managers.BeamSendCoordinator.Reason
import cash.p.terminal.core.managers.BeamSendCoordinator.SendException
import cash.p.terminal.modules.send.SendResult
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/**
 * Next snapshots the quote for the confirmation screen, as every other chain does; the form's live
 * quote keeps moving underneath it. Kept apart from [BeamSendViewModelTest], which is at detekt's size ceiling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BeamSendReviewSnapshotTest : BeamSendTestFixture() {
    private val issued = mutableListOf<BeamSendCoordinator.Quote>()
    private var fee = 29L

    @Before
    fun setUp() {
        setUpFixture()
        coEvery { access.quote(any()) } answers {
            BeamSendCoordinator.Quote(session, firstArg(), quote.copy(fee = fee)).also { issued += it }
        }
    }

    @After
    fun tearDown() = tearDownFixture()

    /** Takes the snapshot, then lets a changed balance re-quote the form at a different fee. */
    private fun reviewThenRequoteUnderneath(): BeamSendCoordinator.Quote {
        model.proceed()
        val reviewed = issued.single()
        fee = 30
        balance.value = BeamBalance(400_000_000, isAuthoritative = true)
        return reviewed
    }

    @Test
    fun backgroundInvalidation_keepsTheReviewedQuote() = runTest(dispatcher) {
        model.proceed()
        balance.value = BeamBalance(400_000_000, isAuthoritative = true)
        assertNull(model.quote)
        assertEquals(29L, model.reviewedQuote?.fee)
        assertEquals(BeamConfirmationCommand.Confirm, model.confirmationCommand())
    }

    @Test
    fun backgroundRequote_doesNotReplaceWhatIsSent() = runTest(dispatcher) {
        coEvery { access.confirm(any()) } returns Outcome.RetryLater
        val reviewed = reviewThenRequoteUnderneath()
        advanceUntilIdle()
        assertEquals(30L, model.quote?.fee)
        assertEquals(29L, model.reviewedQuote?.fee)
        assertEquals(BigDecimal("0.00000029"), model.confirmationData().fee)
        model.confirm()
        coVerify(exactly = 1) { access.confirm(match { it === reviewed }) }
    }

    @Test
    fun nextAgain_snapshotsTheCurrentQuote() = runTest(dispatcher) {
        model.proceed()
        fee = 30
        model.onAmountChanged("2")
        advanceUntilIdle()
        model.proceed()
        assertEquals(30L, model.reviewedQuote?.fee)
    }

    @Test
    fun nextAfterAttempt_keepsTheAttemptedOperation() = runTest(dispatcher) {
        coEvery { access.confirm(any()) } returns Outcome.RetryLater
        val reviewed = reviewThenRequoteUnderneath()
        advanceUntilIdle()
        model.confirm()
        model.proceed()
        assertEquals(29L, model.reviewedQuote?.fee)
        model.retry()
        coVerify(exactly = 2) { access.confirm(match { it === reviewed }) }
    }

    @Test
    fun quoteChangedOnSend_surfacesAsASendError() = runTest(dispatcher) {
        coEvery { access.confirm(any()) } throws SendException(Reason.QuoteChanged)
        model.proceed()
        model.confirm()
        assertEquals(R.string.beam_send_quote_changed, model.message)
        assertTrue(model.sendResult is SendResult.Failed)
        assertNotNull(model.reviewedQuote)
    }
}
