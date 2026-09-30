package cash.p.terminal.core.managers

import cash.p.beam.BeamAddressType
import cash.p.beam.BeamFailure
import cash.p.beam.BeamQuoteRequest
import cash.p.beam.BeamSendAmount
import cash.p.beam.BeamSendContext.Online
import cash.p.beam.BeamSendRequest
import cash.p.beam.BeamSendPreview
import cash.p.beam.BeamSendResolution
import cash.p.beam.BeamWalletState
import cash.p.terminal.core.managers.BeamSendCoordinator.Outcome
import cash.p.terminal.core.managers.BeamSendCoordinator.Reason
import cash.p.terminal.core.managers.BeamSendCoordinator.SendException
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coVerify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class BeamSendCoordinatorTest : BeamSendTestFixture() {
    @Test
    fun preview_withoutConfirmation_neverPreparesOrRecovers() = runTest {
        preview()
        assertTrue(coordinator.recordedFor(session).isEmpty())
        assertTrue(sdk.prepares.isEmpty())
        assertEquals(0, sdk.recoveries)
        assertEquals(0, sdk.registrations)
    }

    @Test
    fun confirm_onlineSend_previewsPreparesAndCommitsWithoutInventorySweep() = runTest {
        val confirmation = preview()
        val outcome = coordinator.confirm(confirmation) as Outcome.Recorded
        assertEquals(listOf(confirmation.operationId), sdk.prepares)
        assertEquals(listOf(confirmation.operationId), sdk.commits)
        assertEquals(0, sdk.recoveries)
        assertEquals(1, sdk.registrations)
        assertEquals(BeamSendResolution.Submitted(TX_ID), outcome.operation.resolution)
    }

    @Test
    fun confirm_marksTheTransactionAsOwnBeforeCoreCreatesIt() = runTest {
        val confirmation = preview()
        var markedBeforeCommit = false
        sdk.beforeCommit = {
            coVerify(exactly = 1) { locallyCreated.markCreated("account", BlockchainType.Beam.uid, TX_ID, any()) }
            markedBeforeCommit = true
        }
        coordinator.confirm(confirmation)
        assertTrue(markedBeforeCommit)
    }

    // A process that died after prepare() never reached settle(); recovery commits without either.
    @Test
    fun recovery_marksAPreparedOperationBeforeCommittingIt() = runTest {
        openSession()
        sdk.seedUnrelatedPrepared()
        var markedBeforeRecovery = false
        sdk.beforeRecovery = {
            coVerify(exactly = 1) { locallyCreated.markCreated("account", BlockchainType.Beam.uid, OTHER_TX_ID, any()) }
            markedBeforeRecovery = true
        }
        coordinator.reconcile(session, retryPending = true)
        assertTrue(markedBeforeRecovery)
        assertEquals(listOf(OTHER_OPERATION_ID), sdk.commits)
    }

    @Test
    fun confirm_lostCommitResponse_resolvesWithoutSecondPrepareOrCommit() = runTest {
        val confirmation = preview()
        // The Core transaction is durable; only the commit answer is lost.
        sdk.afterCommit = { throw secretFailure() }
        expect(Reason.ExternalFailure) { coordinator.confirm(confirmation) }
        sdk.afterCommit = {}
        val outcome = coordinator.confirm(confirmation) as Outcome.Recorded
        assertEquals(BeamSendResolution.Submitted(TX_ID), outcome.operation.resolution)
        assertEquals(listOf(confirmation.operationId), sdk.prepares)
        assertEquals(listOf(confirmation.operationId), sdk.commits)
        assertEquals(0, sdk.recoveries)
        assertEquals(1, sdk.registrations)
    }

    @Test
    fun confirm_unrelatedPreparedOperation_isNeverBroadcast() = runTest {
        val settled = terminal()
        sdk.seedUnrelatedPrepared()
        coordinator.confirm(settled)
        assertEquals(listOf(settled.operationId), sdk.commits)
        assertEquals(0, sdk.recoveries)
        assertEquals(1, sdk.registrations)
        assertFalse(sdk.core.containsKey(OTHER_TX_ID))
        assertEquals(BeamSendResolution.Prepared(OTHER_TX_ID), sdk.resolutions.getValue(OTHER_OPERATION_ID))
    }

    @Test
    fun confirm_unresolvedUnrelatedOperation_defersWithoutCommittingEither() = runTest {
        val confirmation = preview()
        sdk.seedUnrelatedPrepared()
        assertEquals(Outcome.RetryLater, coordinator.confirm(confirmation))
        assertEquals(listOf(confirmation.operationId), sdk.commits)
        assertEquals(0, sdk.recoveries)
        assertEquals(0, sdk.registrations)
        assertTrue(sdk.core.isEmpty())
    }

    @Test
    fun confirm_duplicateConcurrentCalls_registersOneStableIdentity() = runTest {
        val confirmation = preview()
        val outcomes = List(4) { async { coordinator.confirm(confirmation) } }.map { it.await() }
        assertTrue(outcomes.all { it is Outcome.Recorded })
        assertEquals(listOf(confirmation.operationId), sdk.prepares)
        assertEquals(listOf(confirmation.operationId), sdk.commits)
        assertEquals(1, sdk.registrations)
        assertEquals(TX_ID, row().transactionId)
    }

    @Test
    fun confirm_prepareRejectedBeforeAcceptance_retriesSameOperation() = runTest {
        val confirmation = preview()
        sdk.beforePrepare = { throw secretFailure() }
        expect(Reason.ExternalFailure) { coordinator.confirm(confirmation) }
        sdk.beforePrepare = {}
        coordinator.confirm(confirmation)
        assertEquals(listOf(confirmation.operationId), sdk.prepares)
        assertEquals(confirmation.operationId, row().operationId)
        assertEquals(1, sdk.registrations)
    }

    @Test
    fun reconcile_lostAcceptedPrepareResponse_recoversSameIdentityAfterRestart() = runTest {
        val confirmation = preview()
        sdk.afterPrepare = { throw CancellationException("fixture") }
        assertFailsWith<CancellationException> { coordinator.confirm(confirmation) }
        owner.close()
        restart()
        openSession()
        assertEquals(confirmation.operationId, coordinator.recordedFor(session).single().operationId)
        coordinator.reconcile(session, retryPending = true)
        assertEquals(TX_ID, row().transactionId)
        assertEquals(listOf(confirmation.operationId), sdk.prepares)
        assertEquals(listOf(confirmation.operationId), sdk.commits)
        assertEquals(1, sdk.registrations)
    }

    @Test
    fun confirm_lostAcceptedPrepareResponse_discoversOperationWithoutSecondPrepare() = runTest {
        val confirmation = preview()
        sdk.afterPrepare = { throw secretFailure() }
        expect(Reason.ExternalFailure) { coordinator.confirm(confirmation) }
        assertTrue(coordinator.confirm(confirmation) is Outcome.Recorded)
        assertEquals(listOf(confirmation.operationId), sdk.prepares)
        assertEquals(1, sdk.registrations)
    }

    @Test
    fun reconcile_failureBeforeOrAfterCoreAcceptance_recoversWithoutNewPrepare() = runTest {
        for (afterCore in listOf(false, true)) {
            val confirmation = preview()
            if (afterCore) sdk.afterCommit = { throw secretFailure() }
            else sdk.atCommitBoundary = { throw secretFailure() }
            expect(Reason.ExternalFailure) { coordinator.confirm(confirmation) }
            sdk.atCommitBoundary = {}
            sdk.afterCommit = {}
            restart()
            coordinator.reconcile(session, retryPending = true)
            assertEquals(TX_ID, row().transactionId)
            assertEquals(1, sdk.registrations)
            assertEquals(listOf(confirmation.operationId), sdk.prepares)
            clearFixture()
        }
    }

    @Test
    fun confirm_notReadyStates_rejectsWithoutPaymentEffects() = runTest {
        val confirmation = preview()
        for (state in listOf(BeamWalletState.Offline(1), BeamWalletState.Stopped, BeamWalletState.Syncing(1, 2))) {
            sdk.state.value = state
            expect(Reason.NotReady) { coordinator.confirm(confirmation) }
            expect(Reason.NotReady) { coordinator.preview(session, request) }
        }
        assertTrue(sdk.operations.isEmpty())
        assertTrue(sdk.prepares.isEmpty())
    }

    @Test
    fun recordedFor_stoppedOrOffline_listsWithoutRecovery() = runTest {
        val confirmation = prepared()
        for (state in listOf(BeamWalletState.Stopped, BeamWalletState.Offline(1))) {
            sdk.state.value = state
            val recoveries = sdk.recoveries
            assertEquals(confirmation.operationId, coordinator.recordedFor(session).single().operationId)
            assertEquals(recoveries, sdk.recoveries)
            expect(Reason.NotReady) { coordinator.reconcile(session, retryPending = true) }
        }
        assertEquals(0, sdk.registrations)
    }

    @Test
    fun confirm_changedQuote_rejectsBeforePrepare() = runTest {
        val changes: List<(BeamSendPreview) -> BeamSendPreview> = listOf(
            { it.copy(requestHash = "b".repeat(64)) }, { it.copy(fee = 11, total = 111) },
            { it.copy(previewVersion = 2) }, { it.copy(amount = 101, total = 111) },
            { it.copy(receiverType = BeamAddressType.MaxPrivacy) },
        )
        for (change in changes) {
            val confirmation = preview()
            sdk.quoteChange = change
            expect(Reason.QuoteChanged) { coordinator.confirm(confirmation) }
            sdk.quoteChange = { it }
        }
        assertTrue(sdk.prepares.isEmpty())
    }

    @Test
    fun confirm_inventoryDoesNotMatchQuote_rejectsRecovery() = runTest {
        val confirmation = prepared()
        sdk.operations[confirmation.operationId] = row().copy(requestHash = "b".repeat(64))
        expect(Reason.QuoteChanged) { coordinator.confirm(confirmation) }
        assertEquals(0, sdk.registrations)
    }

    @Test
    fun confirm_prepareResponseChangesIdentity_rejectsHandoff() = runTest {
        val confirmation = preview()
        sdk.preparedChange = { it.copy(operationId = "other") }
        expect(Reason.IdentityChanged) { coordinator.confirm(confirmation) }
        assertTrue(sdk.commits.isEmpty())
    }

    @Test
    fun confirm_inventoryChangesTxId_rejectsBeforeRecovery() = runTest {
        val confirmation = prepared()
        sdk.operations[confirmation.operationId] = row().copy(transactionId = OTHER_TX_ID)
        expect(Reason.IdentityChanged) { coordinator.confirm(confirmation) }
        assertEquals(0, sdk.registrations)
    }

    @Test
    fun confirm_admissionDeferred_returnsRetryLaterAndLeavesSdkEvidence() = runTest {
        val confirmation = preview()
        sdk.beforeCommit = { throw BeamFailure.SendAdmissionDeferred("fixture", secretFailure()) }
        assertEquals(Outcome.RetryLater, coordinator.confirm(confirmation))
        sdk.beforeCommit = {}
        coordinator.confirm(confirmation)
        assertEquals(listOf(confirmation.operationId), sdk.prepares)
        assertEquals(1, sdk.registrations)
    }

    @Test
    fun reconcile_terminalThenReorg_usesSdkResolutionWithoutAppHistoryScan() = runTest {
        val confirmation = terminal()
        assertTrue(coordinator.recordedFor(session).single().resolution is BeamSendResolution.Terminal)
        sdk.resolutions[confirmation.operationId] = BeamSendResolution.Submitted(TX_ID)
        assertEquals(BeamSendResolution.Submitted(TX_ID), coordinator.reconcile(session, true).single().resolution)
        assertEquals(1, sdk.commits.size)
        assertTrue(sdk.pages.isEmpty())
    }

    @Test
    fun confirm_indeterminateSdkEvidence_neverCreatesOrPreparesAnOperation() = runTest {
        val confirmation = submitted()
        sdk.core.clear()
        sdk.resolutions[confirmation.operationId] = BeamSendResolution.Indeterminate(TX_ID)
        val result = coordinator.confirm(confirmation) as Outcome.Recorded
        assertEquals(BeamSendResolution.Indeterminate(TX_ID), result.operation.resolution)
        assertEquals(1, sdk.prepares.size)
        assertEquals(1, sdk.commits.size)
    }

    @Test
    fun confirm_corruptInventory_failsClosedBeforePrepareOrRecovery() = runTest {
        val confirmation = preview()
        sdk.beforeInventory = { throw secretFailure() }
        expect(Reason.ExternalFailure) { coordinator.confirm(confirmation) }
        assertTrue(sdk.prepares.isEmpty())
        assertEquals(0, sdk.recoveries)
    }

    @Test
    fun confirm_accountSwitchInvalidatesPreviewEvenWhenSwitchingBack() = runTest {
        val confirmation = preview()
        openSession("other")
        expect(Reason.SessionMismatch) { coordinator.confirm(confirmation) }
        openSession()
        expect(Reason.SessionMismatch) { coordinator.confirm(confirmation) }
        assertTrue(sdk.prepares.isEmpty())
    }

    @Test
    fun confirm_unrelatedWalletInventory_doesNotGateNewAccount() = runTest {
        val old = prepared()
        val replacement = FakeSdk()
        openSession("other", replacement)
        val confirmation = coordinator.preview(session, request)
        coordinator.confirm(confirmation)
        expect(Reason.SessionMismatch) { coordinator.confirm(old) }
        assertEquals(0, sdk.registrations)
        assertEquals(1, replacement.registrations)
        assertEquals(listOf(confirmation.operationId), replacement.prepares)
    }

    @Test
    fun confirm_switchDuringInventory_preventsPrepareInEitherWallet() = runTest {
        val confirmation = preview()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        sdk.beforeInventory = { entered.complete(Unit); release.await() }
        val writer = async { assertFailsWith<SendException> { coordinator.confirm(confirmation) } }
        entered.await()
        val switching = async { openSession("other") }
        runCurrent()
        release.complete(Unit)
        assertEquals(Reason.SessionMismatch, writer.await().reason)
        switching.await()
        assertTrue(sdk.prepares.isEmpty())
    }

    @Test
    fun confirm_closeDuringAcceptedPrepare_neverRecoversInReplacementSession() = runTest {
        val confirmation = preview()
        val accepted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        sdk.afterPrepare = { accepted.complete(Unit); release.await() }
        val writer = async { assertFailsWith<SendException> { coordinator.confirm(confirmation) } }
        accepted.await()
        val closing = async { owner.close() }
        runCurrent()
        assertFalse(closing.isCompleted)
        release.complete(Unit)
        assertEquals(Reason.SessionMismatch, writer.await().reason)
        closing.await()
        assertEquals(1, sdk.prepares.size)
        assertEquals(0, sdk.recoveries)
        assertEquals(1, sdk.closes)
    }

    @Test
    fun reconcile_concurrentConfirmation_waitsAndNeverPreparesAgain() = runTest {
        val confirmation = preview()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        sdk.beforeCommit = { entered.complete(Unit); release.await() }
        val confirming = async { coordinator.confirm(confirmation) }
        entered.await()
        val reconciling = async { coordinator.reconcile(session, retryPending = true) }
        runCurrent()
        assertFalse(reconciling.isCompleted)
        release.complete(Unit)
        assertTrue(confirming.await() is Outcome.Recorded)
        assertEquals(BeamSendResolution.Submitted(TX_ID), reconciling.await().single().resolution)
        assertEquals(listOf(confirmation.operationId), sdk.prepares)
        assertEquals(listOf(confirmation.operationId), sdk.commits)
        assertEquals(1, sdk.registrations)
    }

    @Test
    fun confirm_consumedOperationDisappears_neverPreparesAgain() = runTest {
        val confirmation = terminal()
        coordinator.confirm(confirmation)
        assertEquals(1, sdk.prepares.size)
        sdk.operations.clear()
        expect(Reason.ConfirmationConsumed) { coordinator.confirm(confirmation) }
        assertEquals(1, sdk.registrations)
        assertEquals(1, sdk.prepares.size)
    }

    @Test
    fun confirm_externalFailureAndCancellation_stripRequestDetails() = runTest {
        val confirmation = preview()
        assertFalse(confirmation.toString().contains(request.receiverToken))
        sdk.beforeInventory = { throw secretFailure() }
        expect(Reason.ExternalFailure) { coordinator.confirm(confirmation) }
        sdk.beforeInventory = { throw CancellationException(request.comment).apply { initCause(secretFailure()) } }
        val error = assertFailsWith<CancellationException> { coordinator.confirm(confirmation) }
        assertEquals("BEAM send cancelled", error.message)
        assertNull(error.cause)
        assertTrue(error.suppressed.isEmpty())
    }

    @Test
    fun confirmQuote_carriesTheQuotedCommentIntoTheOperation() = runTest {
        openSession()
        sdk.quotes = { onlineQuote(it) }
        val quote = coordinator.quote(session, BeamQuoteRequest("deposit", BeamSendAmount.Exact(100), Online, "memo"))

        assertTrue(coordinator.confirmQuote(quote) is Outcome.Recorded)

        assertEquals(sdk.previewSend(BeamSendRequest("deposit", 100, "memo")).requestHash, row().requestHash)
    }
}
