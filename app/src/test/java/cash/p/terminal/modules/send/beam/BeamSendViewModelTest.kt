package cash.p.terminal.modules.send.beam

import androidx.lifecycle.viewModelScope
import cash.p.beam.BeamAddressType
import cash.p.beam.BeamQuoteRequest
import cash.p.beam.BeamBalance
import cash.p.beam.BeamOfflineSigningState
import cash.p.beam.BeamOfflineSignResult
import cash.p.beam.BeamOfflineSendState
import cash.p.beam.BeamSendAmount
import cash.p.beam.BeamSendContext
import cash.p.beam.BeamSendDeliveryMode
import cash.p.beam.BeamSendOperation
import cash.p.beam.BeamSendResolution
import cash.p.beam.BeamWalletState
import cash.p.terminal.R
import cash.p.terminal.core.managers.BeamSendCoordinator
import cash.p.terminal.entities.OfflineSignedTransaction
import cash.p.terminal.modules.send.SendFragment.ProceedActionData
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.offline.OfflineSignState
import cash.p.terminal.modules.send.offline.OfflineTransactionFormat
import cash.p.terminal.modules.sendtokenselect.PrefilledData
import cash.p.terminal.strings.helpers.TranslatableString
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class BeamSendViewModelTest : BeamSendTestFixture() {
    @Before
    fun setUp() = setUpFixture()

    @After
    fun tearDown() = tearDownFixture()

    private fun <T> record(events: SharedFlow<T>): List<T> {
        val seen = mutableListOf<T>()
        model.viewModelScope.launch { events.collect { seen += it } }
        return seen
    }

    private fun recordNavigations(): List<ProceedActionData> = record(model.proceedRequests)

    @Test
    fun localBalance_stoppedWithoutReady_remainsVisible() {
        state.value = BeamWalletState.Stopped
        balance.value = balance.value.copy(isAuthoritative = false)
        assertEquals(BigDecimal("5.00000000"), model.balance)
        assertFalse(model.ready)
        assertFalse(model.canQuote)
        assertTrue(model.canSignOffline)
        assertTrue(model.canProceed)
    }

    @Test
    fun initialize_prefillHidden_preservesRecipientAndExactAmount() {
        model.initialize(PrefilledData("MaxPrivacy", BigDecimal("1.23456789")), true)
        assertEquals("MaxPrivacy", model.recipient)
        assertEquals("1.23456789", model.amount)
        assertTrue(model.hideAddress)
        model.initialize(PrefilledData("Offline", BigDecimal.TEN), false)
        assertEquals("MaxPrivacy", model.recipient)
    }

    @Test
    fun recipient_allTypesWithoutReadyOrAmount_displaysStatelessMetadata() {
        state.value = BeamWalletState.Stopped
        model.onAmountChanged("")
        BeamAddressType.entries.forEach {
            model.onRecipientChanged(it.name)
            assertEquals(it, model.receiverType)
        }
        coVerify(exactly = 0) { access.quote(any()) }
    }

    @Test
    fun recipientEntered_readyWallet_prefetchesSdkMaxAsAvailableBalance() = runTest(dispatcher) {
        advanceUntilIdle()
        coVerify { access.quote(match { it.amount == BeamSendAmount.Max && it.context == BeamSendContext.Online }) }
        assertEquals(BigDecimal("1.00000000"), model.maxSendable)
        assertEquals(BigDecimal("1.00000000"), model.availableToSend)
    }

    @Test
    fun unreadyWallet_recipientEntered_neverQuotesMax() = runTest(dispatcher) {
        state.value = BeamWalletState.Stopped
        model.onRecipientChanged("MaxPrivacy")
        advanceUntilIdle()
        coVerify(exactly = 0) { access.quote(any()) }
        assertNull(model.maxSendable)
    }

    @Test
    fun unreadyWallet_availableToSend_isBalanceLessTheSendFee() = runTest(dispatcher) {
        state.value = BeamWalletState.Stopped
        advanceUntilIdle()
        assertNull(model.maxSendable)
        assertEquals(BigDecimal("4.98900000"), model.availableToSend)
        balance.value = BeamBalance(1_000_000, isAuthoritative = true)
        advanceUntilIdle()
        assertEquals(BigDecimal.ZERO, model.availableToSend.stripTrailingZeros())
    }

    @Test
    fun recipientTypedRepeatedly_debounces_quotesOnce() = runTest(dispatcher) {
        listOf("MaxPrivacy", "PublicOffline", "Offline").forEach { model.onRecipientChanged(it) }
        advanceUntilIdle()
        coVerify(exactly = 1) { access.quote(match { it.amount == BeamSendAmount.Max }) }
    }

    @Test
    fun amountChanged_doesNotRequoteMax() = runTest(dispatcher) {
        advanceUntilIdle()
        coVerify(exactly = 1) { access.quote(match { it.amount == BeamSendAmount.Max }) }
        model.onAmountChanged("2")
        model.onAmountChanged("3")
        advanceUntilIdle()
        coVerify(exactly = 1) { access.quote(match { it.amount == BeamSendAmount.Max }) }
    }

    @Test
    fun balanceChanged_sameRecipient_refetchesMax() = runTest(dispatcher) {
        advanceUntilIdle()
        assertEquals(BigDecimal("1.00000000"), model.maxSendable)
        coEvery { access.quote(any()) } answers {
            BeamSendCoordinator.Quote(session, firstArg(), quote.copy(amount = 200_000_000))
        }
        balance.value = BeamBalance(400_000_000, isAuthoritative = true)
        advanceUntilIdle()
        assertEquals(BigDecimal("2.00000000"), model.maxSendable)
        coVerify(exactly = 2) { access.quote(match { it.amount == BeamSendAmount.Max }) }
    }

    @Test
    fun walletClosed_dropsPrefetchedMax() = runTest(dispatcher) {
        advanceUntilIdle()
        assertEquals(BigDecimal("1.00000000"), model.maxSendable)
        state.value = BeamWalletState.Closed
        advanceUntilIdle()
        assertNull(model.maxSendable)
        assertEquals(BigDecimal.ZERO, model.availableToSend)
    }

    @Test
    fun proceedFailed_staleMax_refetches() = runTest(dispatcher) {
        advanceUntilIdle()
        assertEquals(BigDecimal("1.00000000"), model.maxSendable)
        var exactFailed = false
        coEvery { access.quote(any()) } answers {
            val request = firstArg<BeamQuoteRequest>()
            if (request.amount is BeamSendAmount.Exact && !exactFailed) {
                exactFailed = true
                throw BeamSendCoordinator.SendException(BeamSendCoordinator.Reason.InsufficientFunds)
            }
            BeamSendCoordinator.Quote(session, request, quote.copy(amount = 50_000_000))
        }
        // The edit drops the quote the background pipeline already delivered, so the tap has to
        // fetch one itself; without it proceed() would just navigate with the stale figure in hand.
        model.onAmountChanged("2")
        model.proceed()
        advanceUntilIdle()
        assertEquals(BigDecimal("0.50000000"), model.maxSendable)
    }

    @Test
    fun exact_overflowOrTooManyDecimals_doesNotQuote() {
        for (value in listOf("1.000000001", "92233720368.54775808", "-1")) {
            model.onAmountChanged(value)
            model.proceed()
        }
        coVerify(exactly = 0) { access.quote(any()) }
    }

    @Test
    fun quote_recipientAndAmountChanged_lateResultCannotWin() = runTest(dispatcher) {
        val reply = CompletableDeferred<Unit>()
        coEvery { access.quote(any()) } coAnswers {
            val request = firstArg<BeamQuoteRequest>()
            withContext(NonCancellable) { reply.await() }
            BeamSendCoordinator.Quote(session, request, quote)
        }
        val navigations = recordNavigations()
        model.proceed()
        model.onRecipientChanged("MaxPrivacy")
        model.onAmountChanged("2")
        reply.complete(Unit)
        assertNull(model.quote)
        assertTrue(navigations.isEmpty())
    }

    @Test
    fun quote_contextOrBalanceChange_invalidatesDisplayedResult() = runTest(dispatcher) {
        model.proceed()
        context.value = BeamOfflineSigningState.Ready("new-context", 11, 21)
        assertNull(model.quote)
        model.proceed()
        balance.value = BeamBalance(400_000_000, isAuthoritative = true)
        assertNull(model.quote)
    }

    @Test
    fun quote_accountSwitch_rejectsLateResult() = runTest(dispatcher) {
        coEvery { access.quote(any()) } answers {
            every { access.current } returns false
            BeamSendCoordinator.Quote(session, firstArg(), quote)
        }
        val navigations = recordNavigations()
        model.proceed()
        assertNull(model.quote)
        assertTrue(navigations.isEmpty())
    }

    @Test
    fun retry_changedOnlineQuote_refreshesSameUuidForNewConfirmation() = runTest(dispatcher) {
        coEvery { access.confirm(any()) } throws
            BeamSendCoordinator.SendException(BeamSendCoordinator.Reason.QuoteChanged)
        coEvery { access.refreshQuote(any()) } answers {
            val previous = firstArg<BeamSendCoordinator.Quote>()
            BeamSendCoordinator.Quote(
                session, previous.request, quote.copy(fee = 30, total = 100_000_030), previous.operationId
            )
        }
        model.proceed()
        model.confirm()
        val id = model.operationId
        model.retry()
        assertFalse(model.attempted)
        assertEquals(30L, model.reviewedQuote?.fee)
        assertEquals(30L, model.quote?.fee)
        coEvery { access.confirm(any()) } returns BeamSendCoordinator.Outcome.RetryLater
        model.confirm()
        assertEquals(id, model.operationId)
    }

    @Test
    fun retry_onlineAdmissionDeferred_reusesConfirmationUuid() = runTest(dispatcher) {
        coEvery { access.confirm(any()) } returns BeamSendCoordinator.Outcome.RetryLater
        model.proceed()
        model.confirm()
        val id = model.operationId
        model.retry()
        assertEquals(id, model.operationId)
        coVerify(exactly = 2) { access.confirm(match { it.operationId == id }) }
    }

    @Test
    fun sendResult_freshState_isNull() {
        assertNull(model.sendResult)
    }

    @Test
    fun sendResult_confirmInFlight_reportsSending() = runTest(dispatcher) {
        val reply = CompletableDeferred<BeamSendCoordinator.Outcome>()
        coEvery { access.confirm(any()) } coAnswers { withContext(NonCancellable) { reply.await() } }
        model.proceed()
        model.confirm()
        assertTrue(model.busy)
        assertEquals(SendResult.Sending, model.sendResult)
        reply.complete(BeamSendCoordinator.Outcome.RetryLater)
    }

    @Test
    fun sendResult_confirmRecorded_reportsSent() = runTest(dispatcher) {
        val operation = BeamSendOperation(
            "op", "tx", "hash", 100_000_000, 29, BeamSendResolution.Submitted("tx"),
            deliveryMode = BeamSendDeliveryMode.Online,
        )
        coEvery { access.confirm(any()) } returns BeamSendCoordinator.Outcome.Recorded(operation)
        model.proceed()
        model.confirm()
        assertTrue(model.recorded)
        assertEquals(SendResult.Sent(), model.sendResult)
    }

    @Test
    fun confirmRecorded_confirmationKeepsTheRecipientUntilItCloses() = runTest(dispatcher) {
        // The shared confirmation screen stays up for a moment after Sent; its address row must not blank.
        coEvery { access.confirm(any()) } answers {
            BeamSendCoordinator.Outcome.Recorded(
                BeamSendOperation(
                    firstArg<BeamSendCoordinator.Quote>().operationId, "tx", "hash", 100_000_000, 29,
                    BeamSendResolution.Submitted("tx"), deliveryMode = BeamSendDeliveryMode.Online,
                )
            )
        }
        model.proceed()

        model.confirm()

        assertTrue(model.recorded)
        assertEquals("Offline", model.confirmationData().address.hex)
    }

    @Test
    fun confirmRecorded_balanceDropsDuringTheSend_confirmationKeepsTheBalanceAtTheTap() = runTest(dispatcher) {
        // The spent input is locked at once while its change stays "receiving" until the block, so the SDK
        // reports nothing available for the moment the confirmation screen is still up.
        coEvery { access.confirm(any()) } answers {
            balance.value = BeamBalance(0, isAuthoritative = true)
            BeamSendCoordinator.Outcome.Recorded(
                BeamSendOperation(
                    firstArg<BeamSendCoordinator.Quote>().operationId, "tx", "hash", 100_000_000, 29,
                    BeamSendResolution.Submitted("tx"), deliveryMode = BeamSendDeliveryMode.Online,
                )
            )
        }
        model.proceed()

        model.confirm()
        advanceUntilIdle()

        assertTrue(model.recorded)
        assertEquals(BigDecimal("0E-8"), model.balance)
        assertEquals(BigDecimal("5.00000000"), model.confirmationBalance)
    }

    @Test
    fun confirmNotRecorded_balanceDropsDuringTheSend_confirmationShowsTheLiveBalance() = runTest(dispatcher) {
        coEvery { access.confirm(any()) } answers {
            balance.value = BeamBalance(0, isAuthoritative = true)
            BeamSendCoordinator.Outcome.RetryLater
        }
        model.proceed()

        model.confirm()
        advanceUntilIdle()

        assertFalse(model.recorded)
        assertEquals(BigDecimal("0E-8"), model.confirmationBalance)
    }

    @Test
    fun sendResult_unresolvedOutcomeMessage_reportsFailedWithSameMessage() = runTest(dispatcher) {
        coEvery { access.confirm(any()) } returns BeamSendCoordinator.Outcome.RetryLater
        model.proceed()
        model.confirm()
        assertTrue(model.attempted)
        assertFalse(model.recorded)
        assertEquals(R.string.beam_send_uncertain, model.message)
        val result = model.sendResult
        assertTrue(result is SendResult.Failed)
        val caution = (result as SendResult.Failed).caution.s
        assertTrue(caution is TranslatableString.ResString)
        assertEquals(R.string.beam_send_uncertain, (caution as TranslatableString.ResString).id)
    }

    @Test
    fun sendResult_quoteChangedMessageWithoutAttempt_reportsFailedWithSameMessage() = runTest(dispatcher) {
        // retry() after a QuoteChanged failure clears `attempted` back to false (a fresh confirmation
        // is expected), but the explanatory message must still reach the user on the confirm screen.
        val quoteChanged = BeamSendCoordinator.SendException(BeamSendCoordinator.Reason.QuoteChanged)
        coEvery { access.confirm(any()) } throws quoteChanged
        coEvery { access.refreshQuote(any()) } answers {
            val previous = firstArg<BeamSendCoordinator.Quote>()
            val refreshed = quote.copy(fee = 30, total = 100_000_030)
            BeamSendCoordinator.Quote(session, previous.request, refreshed, previous.operationId)
        }
        model.proceed()
        model.confirm()
        model.retry()
        assertFalse(model.attempted)
        assertEquals(R.string.beam_send_quote_changed, model.message)
        val result = model.sendResult
        assertTrue(result is SendResult.Failed)
        val caution = (result as SendResult.Failed).caution.s
        assertTrue(caution is TranslatableString.ResString)
        assertEquals(R.string.beam_send_quote_changed, (caution as TranslatableString.ResString).id)
    }

    // ---- Preview merged into Next: the background fee pipeline ----

    @Test
    fun amountEntered_onlineReadyWallet_quotesInBackgroundAndExposesFee() = runTest(dispatcher) {
        // Asserted before the clock advances: it pins the invalidate -> refresh order, without which
        // the cell would still read Idle here and the test would pass on a pipeline that never ran.
        assertEquals(BeamFeeState.Loading, model.feeState)
        assertTrue(model.feeLoading)
        advanceUntilIdle()
        coVerify(exactly = 1) {
            access.quote(match { it.amount is BeamSendAmount.Exact && it.context == BeamSendContext.Online })
        }
        assertEquals(BigDecimal("0.00000029"), model.feeAmount)
        assertEquals(BeamFeeState.Ready, model.feeState)
        assertFalse(model.feeLoading)
    }

    @Test
    fun amountTypedRepeatedly_debouncesToOneExactQuote() = runTest(dispatcher) {
        listOf("2", "23", "234").forEach { model.onAmountChanged(it) }
        advanceUntilIdle()
        coVerify(exactly = 1) { access.quote(match { it.amount is BeamSendAmount.Exact }) }
    }

    @Test
    fun backgroundQuoteFailed_marksFeeFailedWithoutRaisingAMessage() = runTest(dispatcher) {
        coEvery { access.quote(match { it.amount is BeamSendAmount.Exact }) } throws
            BeamSendCoordinator.SendException(BeamSendCoordinator.Reason.InsufficientFunds)
        model.onAmountChanged("4")
        advanceUntilIdle()
        assertEquals(BeamFeeState.Failed, model.feeState)
        assertFalse(model.feeLoading)
        assertNull(model.message)
        assertFalse(model.busy)
    }

    @Test
    fun backgroundQuoteLanded_doesNotNavigate() = runTest(dispatcher) {
        val navigations = recordNavigations()
        advanceUntilIdle()
        assertNotNull(model.quote)
        assertTrue(navigations.isEmpty())
    }

    // ---- Next ----

    @Test
    fun proceedWithQuoteInHand_navigatesWithoutRequoting() = runTest(dispatcher) {
        advanceUntilIdle()
        coVerify(exactly = 1) { access.quote(match { it.amount is BeamSendAmount.Exact }) }
        val navigations = recordNavigations()
        model.proceed()
        assertEquals(1, navigations.size)
        coVerify(exactly = 1) { access.quote(match { it.amount is BeamSendAmount.Exact }) }
    }

    @Test
    fun proceed_walletNotReady_isEnabledAndExplainsItselfOnTap() = runTest(dispatcher) {
        state.value = BeamWalletState.Stopped
        val navigations = recordNavigations()
        val errors = record(model.errorEvents)
        assertTrue(model.canProceed)
        model.proceed()
        assertEquals(listOf(R.string.beam_send_not_ready), errors)
        assertTrue(navigations.isEmpty())
        assertNull(model.message)
        coVerify(exactly = 0) { access.quote(match { it.amount is BeamSendAmount.Exact }) }
    }

    @Test
    fun proceedFailed_raisesAnErrorEventAndDoesNotNavigate() = runTest(dispatcher) {
        val navigations = recordNavigations()
        val errors = record(model.errorEvents)
        coEvery { access.quote(match { it.amount is BeamSendAmount.Exact }) } throws
            BeamSendCoordinator.SendException(BeamSendCoordinator.Reason.InsufficientFunds)
        model.onAmountChanged("4")
        model.proceed()
        assertEquals(listOf(R.string.Swap_ErrorInsufficientBalance), errors)
        assertNull(model.message)
        assertEquals(BeamFeeState.Failed, model.feeState)
        assertTrue(navigations.isEmpty())
        assertFalse(model.busy)
    }

    // A fresh Quote mints a new random operationId, so a later quote replacing a confirmed one would
    // abandon the real Core operation and let retry() prepare a second payment. Every input is inert
    // once `attempted` is set, and retry() reuses the confirmed id.
    @Test
    fun afterConfirm_noLaterQuoteCanReplaceTheConfirmedOperation() = runTest(dispatcher) {
        coEvery { access.confirm(any()) } returns BeamSendCoordinator.Outcome.RetryLater
        model.proceed()
        val confirmed = requireNotNull(model.reviewedQuote)
        model.confirm()
        val id = requireNotNull(model.operationId)
        model.onAmountChanged("2")
        model.onRecipientChanged("MaxPrivacy")
        balance.value = BeamBalance(400_000_000, isAuthoritative = true)
        advanceUntilIdle()
        assertSame(confirmed, model.reviewedQuote)
        assertEquals(id, model.operationId)
        model.retry()
        coVerify(exactly = 2) { access.confirm(match { it.operationId == id }) }
    }

    // ---- Sign offline: the cell quotes under the offline context on tap and opens the shared sign page ----

    private val exported = OfflineSignedTransaction("ab", "payload", "kernel", 1)

    private fun openSignPage(): List<Unit> {
        val opened = record(model.offlineSignRequests)
        model.signOffline()
        return opened
    }

    private fun signAndExportWith(export: () -> OfflineSignedTransaction) {
        coEvery { access.sign(any()) } returns BeamOfflineSignResult("tx", BeamOfflineSendState.Signed)
        coEvery { operations.export(any(), any()) } answers { export() }
        openSignPage()
        model.onClickSignOffline(OfflineTransactionFormat.Raw)
    }

    @Test
    fun signOffline_readyContext_quotesOfflineAndOpensTheSignPageOnce() = runTest(dispatcher) {
        val navigations = recordNavigations()
        val opened = openSignPage()
        assertEquals(listOf(Unit), opened)
        assertTrue(navigations.isEmpty())
        val offline = BeamSendContext.Offline("context")
        coVerify(exactly = 1) {
            access.quoteOffline(match { it.amount is BeamSendAmount.Exact && it.context == offline })
        }
        assertNotNull(model.reviewedQuote)
        assertFalse(model.busy)
        assertFalse(model.editable)
        verify(exactly = 0) { access.releaseOffline() }
    }

    @Test
    fun signOffline_contextUnavailable_reportsItWithoutPausingTheNetwork() = runTest(dispatcher) {
        context.value = BeamOfflineSigningState.Unavailable
        val errors = record(model.errorEvents)
        val opened = openSignPage()
        assertEquals(listOf(R.string.beam_offline_context_unavailable), errors)
        assertTrue(opened.isEmpty())
        coVerify(exactly = 0) { access.quoteOffline(any()) }
        assertTrue(model.editable)
    }

    @Test
    fun signOffline_quoteFailed_releasesTheNetworkAndReopensTheForm() = runTest(dispatcher) {
        coEvery { access.quoteOffline(any()) } throws
            BeamSendCoordinator.SendException(BeamSendCoordinator.Reason.InsufficientFunds)
        val errors = record(model.errorEvents)
        val opened = openSignPage()
        assertEquals(listOf(R.string.Swap_ErrorInsufficientBalance), errors)
        assertTrue(opened.isEmpty())
        verify(exactly = 1) { access.releaseOffline() }
        assertTrue(model.editable)
        assertFalse(model.busy)
    }

    @Test
    fun signOffline_quoteCancelledMidFlight_releasesTheNetworkOnce() = runTest(dispatcher) {
        coEvery { access.quoteOffline(any()) } coAnswers { awaitCancellation() }
        openSignPage()
        assertTrue(model.busy)
        model.viewModelScope.cancel()
        verify(exactly = 1) { access.releaseOffline() }
        assertFalse(model.busy)
        assertTrue(model.editable)
    }

    @Test
    fun signOffline_walletChangesWhileQuoting_neitherCancelNorUnlock() = runTest(dispatcher) {
        val reply = CompletableDeferred<Unit>()
        coEvery { access.quoteOffline(any()) } coAnswers {
            reply.await()
            BeamSendCoordinator.Quote(session, firstArg(), quote)
        }
        val opened = openSignPage()
        assertFalse(model.editable)
        state.value = BeamWalletState.Stopped
        balance.value = BeamBalance(400_000_000, isAuthoritative = true)
        context.value = BeamOfflineSigningState.Ready("new-context", 11, 21)
        model.onAmountChanged("2")
        assertTrue(model.busy)
        assertEquals("1", model.amount)
        reply.complete(Unit)
        assertEquals(listOf(Unit), opened)
        verify(exactly = 0) { access.releaseOffline() }
    }

    @Test
    fun signOffline_midDebounce_cancelsThePendingExactQuote() = runTest(dispatcher) {
        advanceTimeBy(200)
        openSignPage()
        advanceUntilIdle()
        assertNull(model.quote)
        coVerify(exactly = 0) { access.quote(match { it.amount is BeamSendAmount.Exact }) }
    }

    @Test
    fun signOffline_whileExactQuoteInFlight_dropsTheResult() = runTest(dispatcher) {
        val reply = CompletableDeferred<Unit>()
        coEvery { access.quote(match { it.amount is BeamSendAmount.Exact }) } coAnswers {
            val request = firstArg<BeamQuoteRequest>()
            withContext(NonCancellable) { reply.await() }
            BeamSendCoordinator.Quote(session, request, quote)
        }
        model.onAmountChanged("2")
        advanceUntilIdle()
        coVerify(exactly = 1) { access.quote(match { it.amount is BeamSendAmount.Exact }) }
        openSignPage()
        reply.complete(Unit)
        advanceUntilIdle()
        assertNull(model.quote)
        assertEquals(BeamFeeState.Idle, model.feeState)
    }

    @Test
    fun onClickSignOffline_oneTap_signsThenExportsIntoTheTransfer() = runTest(dispatcher) {
        signAndExportWith { exported }
        val id = requireNotNull(model.operationId)
        assertEquals(OfflineSignState.Signed(OfflineTransactionFormat.Raw), model.signing.signState)
        assertSame(exported, model.signing.signedTransaction)
        coVerifyOrder {
            access.sign(match { it.operationId == id })
            operations.export(wallet, id)
        }
        coVerify(exactly = 0) { access.confirm(any()) }
        coVerify(exactly = 0) { repository.save(any(), any()) }
    }

    @Test
    fun onClickSignOffline_exportFailedAfterSign_nextTapExportsWithoutSigningAgain() = runTest(dispatcher) {
        signAndExportWith { throw IllegalStateException("lost response") }
        assertTrue(model.signing.signState is OfflineSignState.Failed)
        model.signing.resetSignState()
        coEvery { operations.export(any(), any()) } returns exported
        model.onClickSignOffline(OfflineTransactionFormat.Pcash)
        assertEquals(OfflineSignState.Signed(OfflineTransactionFormat.Pcash), model.signing.signState)
        coVerify(exactly = 1) { access.sign(any()) }
        coVerify(exactly = 2) { operations.export(wallet, requireNotNull(model.operationId)) }
    }

    @Test
    fun onClickSignOffline_noOfflineQuoteOrSigner_reportsNotReadyAndLeavesSigningAlone() = runTest(dispatcher) {
        val errors = record(model.errorEvents)
        model.onClickSignOffline(OfflineTransactionFormat.Raw)
        assertEquals(listOf(R.string.beam_send_not_ready), errors)
        openSignPage()
        every { access.canSign } returns false
        model.onClickSignOffline(OfflineTransactionFormat.Raw)
        assertEquals(listOf(R.string.beam_send_not_ready, R.string.beam_send_not_ready), errors)
        assertEquals(OfflineSignState.Idle, model.signing.signState)
        assertFalse(model.attempted)
        coVerify(exactly = 0) { access.sign(any()) }
    }

    @Test
    fun leaveOfflineSign_whileSigning_staysAndAbandonsNothing() = runTest(dispatcher) {
        val reply = CompletableDeferred<BeamOfflineSignResult>()
        coEvery { access.sign(any()) } coAnswers { reply.await() }
        coEvery { operations.export(any(), any()) } returns exported
        openSignPage()
        model.onClickSignOffline(OfflineTransactionFormat.Raw)
        assertFalse(model.leaveOfflineSign())
        assertEquals(OfflineSignState.Signing, model.signing.signState)
        coVerify(exactly = 0) { access.abort(any()) }
        verify(exactly = 0) { access.releaseOffline() }
        reply.complete(BeamOfflineSignResult("tx", BeamOfflineSendState.Signed))
        assertEquals(OfflineSignState.Signed(OfflineTransactionFormat.Raw), model.signing.signState)
    }

    @Test
    fun leaveOfflineSign_afterSign_abortsThatOperationBeforeTheNetworkResumes() = runTest(dispatcher) {
        val aborted = CompletableDeferred<Boolean>()
        coEvery { access.abort(any()) } coAnswers { aborted.await() }
        signAndExportWith { throw IllegalStateException("export failed") }
        val id = requireNotNull(model.operationId)
        assertTrue(model.leaveOfflineSign())
        verify(exactly = 0) { access.releaseOffline() }
        aborted.complete(true)
        coVerifyOrder {
            access.abort(id)
            access.releaseOffline()
        }
        verify(exactly = 1) { access.releaseOffline() }
    }

    @Test
    fun leaveOfflineSign_abortFailed_stillResumesTheNetwork() = runTest(dispatcher) {
        coEvery { access.abort(any()) } throws IllegalStateException("abort failed")
        signAndExportWith { throw IllegalStateException("export failed") }
        assertTrue(model.leaveOfflineSign())
        verify(exactly = 1) { access.releaseOffline() }
        assertTrue(model.editable)
    }

    @Test
    fun leaveOfflineSign_afterExport_resetsTheFormToEditable() = runTest(dispatcher) {
        advanceUntilIdle()
        coVerify(exactly = 1) { access.quote(match { it.amount is BeamSendAmount.Exact }) }
        coEvery { access.abort(any()) } returns false
        signAndExportWith { exported }
        val id = requireNotNull(model.operationId)
        assertTrue(model.leaveOfflineSign())
        assertTrue(model.editable)
        assertFalse(model.attempted)
        assertNull(model.operationId)
        assertEquals(OfflineSignState.Idle, model.signing.signState)
        coVerify(exactly = 1) { access.abort(id) }
        verify(exactly = 1) { access.releaseOffline() }
        advanceUntilIdle()
        coVerify(exactly = 2) {
            access.quote(match { it.amount is BeamSendAmount.Exact && it.context == BeamSendContext.Online })
        }
    }
}
