package cash.p.terminal.core.managers

import cash.p.beam.BeamFailure
import cash.p.beam.BeamQuoteRequest
import cash.p.beam.BeamSendContext
import cash.p.beam.BeamSendDeliveryMode
import cash.p.beam.BeamSendQuote
import cash.p.beam.BeamSendOperation
import cash.p.beam.BeamSendPreview
import cash.p.beam.BeamSendRequest
import cash.p.beam.BeamSendResolution
import cash.p.beam.BeamWalletSession
import cash.p.beam.BeamWalletState
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.UUID

/** SDK WalletDB owns send durability and recovery; the owner serializes originating-session access. */
class BeamSendCoordinator(
    private val owner: BeamSessionOwner,
    private val locallyCreated: LocallyCreatedTransactionRepository,
) {
    // Not a data class: request material must never appear in generated toString/copy methods.
    class Confirmation internal constructor(
        internal val coordinator: BeamSendCoordinator,
        internal val session: BeamSessionOwner.Session,
        internal val request: BeamSendRequest,
        val quote: BeamSendPreview,
        val operationId: String = UUID.randomUUID().toString(),
    ) {
        internal var attempted = false
        internal var transactionId: String? = null
    }

    class Quote internal constructor(
        internal val session: BeamSessionOwner.Session,
        internal val request: BeamQuoteRequest,
        val value: BeamSendQuote,
        val operationId: String = UUID.randomUUID().toString(),
    ) {
        internal var onlineConfirmation: Confirmation? = null
    }

    suspend fun quote(session: BeamSessionOwner.Session, request: BeamQuoteRequest): Quote =
        serialized(session) { wallet -> Quote(session, request, wallet.quoteSend(request)) }

    suspend fun refreshQuote(quote: Quote): Quote = serialized(quote.session) { wallet ->
        demand(wallet.sendOperations().none { it.operationId == quote.operationId }, Reason.ConfirmationConsumed)
        Quote(quote.session, quote.request, wallet.quoteSend(quote.request), quote.operationId)
    }

    suspend fun confirmQuote(quote: Quote): Outcome {
        demand(quote.request.context == BeamSendContext.Online, Reason.IdentityChanged)
        val confirmation = quote.onlineConfirmation ?: serialized(quote.session) { wallet ->
            ensureReady(quote.session, wallet)
            demand(wallet.quoteSend(quote.request) == quote.value, Reason.QuoteChanged)
            val request = BeamSendRequest(quote.request.receiverToken, quote.value.amount, quote.request.comment)
            val preview = wallet.previewSend(request)
            verifyQuote(request, preview)
            demand(preview.amount == quote.value.amount && preview.fee == quote.value.fee, Reason.QuoteChanged)
            Confirmation(this, quote.session, request, preview, quote.operationId)
                .also { quote.onlineConfirmation = it }
        }
        return confirm(confirmation)
    }

    suspend fun signOffline(quote: Quote) = serialized(quote.session) { wallet ->
        demand(quote.request.context is BeamSendContext.Offline, Reason.IdentityChanged)
        demand(wallet.state.value == BeamWalletState.Stopped, Reason.NotReady)
        wallet.signOffline(quote.operationId, quote.request, quote.value.version)
            .also { markCreated(quote.session, it.transactionId) }
    }

    suspend fun abortOffline(session: BeamSessionOwner.Session, operationId: String): Boolean =
        serialized(session) { wallet ->
            demand(wallet.state.value == BeamWalletState.Stopped, Reason.NotReady)
            val operation = wallet.sendOperations().findOperation(operationId) ?: return@serialized true
            demand(operation.deliveryMode == BeamSendDeliveryMode.Offline, Reason.IdentityChanged)
            wallet.abortPrepared(operationId)
        }

    sealed interface Outcome {
        data class Recorded(val operation: BeamSendOperation) : Outcome
        data object RetryLater : Outcome
    }

    suspend fun preview(session: BeamSessionOwner.Session, request: BeamSendRequest): Confirmation =
        serialized(session) { wallet ->
            ensureReady(session, wallet)
            val quote = wallet.previewSend(request)
            verifyQuote(request, quote)
            Confirmation(this, session, request, quote)
        }

    suspend fun confirm(confirmation: Confirmation): Outcome = sendAction(confirmation.session) { wallet ->
        demand(confirmation.coordinator === this, Reason.SessionMismatch)
        val operation = wallet.sendOperations().findOperation(confirmation.operationId) ?: prepare(confirmation, wallet)
        confirmation.attempted = true
        verifyConfirmation(confirmation, operation)
        val settled = settle(confirmation.session, wallet, operation)
        verifyConfirmation(confirmation, settled)
        Outcome.Recorded(settled)
    }

    /** Recovery may broadcast previously confirmed operations, so callers must hold a network lease. */
    suspend fun reconcile(
        session: BeamSessionOwner.Session,
        retryPending: Boolean = false,
    ): List<BeamSendOperation> = serialized(session) { wallet ->
        val operations = wallet.sendOperations()
        val hasOnline = operations.any { it.deliveryMode == BeamSendDeliveryMode.Online }
        if (retryPending && (operations.isEmpty() || hasOnline)) {
            ensureReady(session, wallet)
            // Recovery can commit an operation whose prepare-time process died before settle() ran.
            operations.forEach { markCreated(session, it.transactionId) }
            wallet.recoverSendOperations()
        } else operations
    }

    /** Local SDK inventory, including stopped/offline sessions; listing never broadcasts. */
    suspend fun recordedFor(session: BeamSessionOwner.Session): List<BeamSendOperation> = reconcile(session)

    private suspend fun prepare(confirmation: Confirmation, wallet: BeamWalletSession): BeamSendOperation {
        // A lost accepted response must be discovered through inventory, never a second prepare.
        demand(!confirmation.attempted || confirmation.transactionId == null, Reason.ConfirmationConsumed)
        demand(wallet.previewSend(confirmation.request) == confirmation.quote, Reason.QuoteChanged)
        ensureReady(confirmation.session, wallet)
        confirmation.attempted = true
        val prepared = wallet.prepareSend(
            confirmation.operationId, confirmation.request, confirmation.quote.previewVersion,
        )
        demand(prepared.operationId == confirmation.operationId && prepared.transactionId.matches(TX_ID),
            Reason.IdentityChanged)
        confirmation.transactionId = prepared.transactionId
        return wallet.sendOperations().findOperation(confirmation.operationId) ?: fail(Reason.MissingOperation)
    }

    /**
     * Drives exactly one durable operation forward. `commitSend` is the only step that may create a
     * Core transaction; the SDK re-checks admission, reuses the stored TxID and never starts a second
     * Core transaction for an operation that already has one, so repeating it after a lost response is
     * safe. Inventory-wide `recoverSendOperations()` is never used here: it would also broadcast
     * unrelated prepared operations.
     */
    private suspend fun settle(
        session: BeamSessionOwner.Session,
        wallet: BeamWalletSession,
        operation: BeamSendOperation,
    ): BeamSendOperation {
        ensureReady(session, wallet)
        demand(operation.deliveryMode == BeamSendDeliveryMode.Online, Reason.IdentityChanged)
        val operationId = operation.operationId
        // commitSend creates the Core transaction; marked first so history never shows it as someone else's.
        markCreated(session, operation.transactionId)
        val resolution = if (operation.resolution.awaitsCoreTransaction()) wallet.commitSend(operationId)
        else wallet.resolveSend(operationId)
        val transactionId = resolution.transactionId() ?: fail(Reason.MissingOperation)
        demand(transactionId == operation.transactionId, Reason.IdentityChanged)
        return wallet.sendOperations().findOperation(operationId) ?: fail(Reason.MissingOperation)
    }

    /**
     * Only Prepared and Committing can still create the recorded Core transaction. Indeterminate means
     * a durably Submitted record without Core evidence: it stays unresolved and must never be replayed
     * as a new payment, so it is only observed through [BeamWalletSession.resolveSend].
     */
    private fun BeamSendResolution.awaitsCoreTransaction(): Boolean =
        this is BeamSendResolution.Prepared || this is BeamSendResolution.Committing

    private fun BeamSendResolution.transactionId(): String? = when (this) {
        BeamSendResolution.NotPrepared -> null
        is BeamSendResolution.Prepared -> transactionId
        is BeamSendResolution.Committing -> transactionId
        is BeamSendResolution.Submitted -> transactionId
        is BeamSendResolution.Indeterminate -> transactionId
        is BeamSendResolution.Terminal -> transactionId
    }

    private fun verifyConfirmation(confirmation: Confirmation, operation: BeamSendOperation) {
        demand(operation.deliveryMode == BeamSendDeliveryMode.Online, Reason.IdentityChanged)
        val quote = confirmation.quote
        demand(
            operation.requestHash == quote.requestHash &&
                operation.amount == quote.amount && operation.fee == quote.fee,
            Reason.QuoteChanged,
        )
        demand(operation.transactionId.matches(TX_ID) && (confirmation.transactionId == null ||
            confirmation.transactionId == operation.transactionId), Reason.IdentityChanged)
        confirmation.transactionId = operation.transactionId
    }

    private fun verifyQuote(request: BeamSendRequest, quote: BeamSendPreview) {
        demand(quote.requestHash.matches(HASH) && quote.amount == request.amount && quote.amount > 0 &&
            quote.fee >= 0 && quote.amount <= Long.MAX_VALUE - quote.fee &&
            quote.total == quote.amount + quote.fee, Reason.QuoteChanged)
    }

    /**
     * Core's TxID is the history record's transactionHash, which is what the poison badge looks up.
     * Online marks precede the only two calls that create a Core transaction: commitSend and recoverSendOperations.
     */
    private suspend fun markCreated(session: BeamSessionOwner.Session, transactionId: String) =
        locallyCreated.markCreated(session.accountId, BlockchainType.Beam.uid, transactionId)

    private fun List<BeamSendOperation>.findOperation(operationId: String): BeamSendOperation? =
        singleOrNull { it.operationId == operationId }

    private suspend fun ensureReady(session: BeamSessionOwner.Session, wallet: BeamWalletSession) {
        currentCoroutineContext().ensureActive()
        demand(owner.current === session, Reason.SessionMismatch)
        demand(wallet.state.value is BeamWalletState.Ready, Reason.NotReady)
    }

    private suspend fun <T> serialized(
        session: BeamSessionOwner.Session,
        block: suspend (BeamWalletSession) -> T,
    ): T = sanitized {
        demand(owner.current === session, Reason.SessionMismatch)
        owner.withSession(session, block)
    }

    private suspend fun sendAction(
        session: BeamSessionOwner.Session,
        block: suspend (BeamWalletSession) -> Outcome,
    ): Outcome = try {
        serialized(session) { wallet ->
            ensureReady(session, wallet)
            block(wallet)
        }
    } catch (error: SendException) {
        if (error.reason == Reason.RetryLater) Outcome.RetryLater else throw error
    }

    private suspend fun <T> sanitized(block: suspend () -> T): T = try {
        block()
    } catch (_: CancellationException) {
        throw CancellationException("BEAM send cancelled")
    } catch (error: SendException) {
        throw error
    } catch (_: BeamFailure.SendAdmissionDeferred) {
        fail(Reason.RetryLater)
    } catch (_: BeamFailure.ContextUnavailable) {
        fail(Reason.ContextUnavailable)
    } catch (_: BeamFailure.StaleQuote) {
        fail(Reason.QuoteChanged)
    } catch (_: BeamFailure.InsufficientFunds) {
        fail(Reason.InsufficientFunds)
    } catch (_: Exception) {
        fail(Reason.ExternalFailure)
    }

    enum class Reason {
        SessionMismatch, NotReady, ConfirmationConsumed, MissingOperation, QuoteChanged,
        IdentityChanged, RetryLater, ExternalFailure, ContextUnavailable, InsufficientFunds,
    }

    class SendException internal constructor(val reason: Reason) : Exception("BEAM send: ${reason.name}")

    private fun demand(condition: Boolean, reason: Reason) { if (!condition) fail(reason) }

    private fun fail(reason: Reason): Nothing = throw SendException(reason)

    private companion object {
        val HASH = Regex("[0-9a-f]{64}")
        val TX_ID = Regex("[0-9a-f]{32}")
    }
}
