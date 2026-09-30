package cash.p.terminal.core.managers

import cash.p.beam.BeamAddress
import cash.p.beam.BeamFailure
import cash.p.beam.BeamOfflineSigningState
import cash.p.beam.BeamOfflineSignResult
import cash.p.beam.BeamQuoteRequest
import cash.p.beam.BeamSendAmount
import cash.p.beam.BeamSendQuote
import cash.p.beam.BeamAddressType
import cash.p.beam.BeamBalance
import cash.p.beam.BeamSendOperation
import cash.p.beam.BeamSendPreview
import cash.p.beam.BeamSendRequest
import cash.p.beam.BeamSendResolution
import cash.p.beam.BeamTransaction
import cash.p.beam.BeamTransactionDirection
import cash.p.beam.BeamTransactionPage
import cash.p.beam.BeamTransactionStatus
import cash.p.beam.BeamWalletSession
import cash.p.beam.BeamWalletState
import cash.p.beam.PreparedBeamSend
import cash.p.terminal.core.managers.BeamSendCoordinator.Reason
import cash.p.terminal.core.managers.BeamSendCoordinator.SendException
import cash.p.terminal.wallet.Account
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.security.MessageDigest
import kotlin.test.assertFailsWith
import cash.p.beam.BeamNetwork as SdkBeamNetwork

open class BeamSendTestFixture {
    protected val sdk = FakeSdk()
    protected val factory = mockk<BeamSessionFactory>(relaxUnitFun = true)
    protected val owner = BeamSessionOwner(factory)
    protected val locallyCreated = mockk<LocallyCreatedTransactionRepository>(relaxed = true)
    protected var coordinator = BeamSendCoordinator(owner, locallyCreated)
    protected lateinit var session: BeamSessionOwner.Session
    protected val request = BeamSendRequest("private-receiver-token", 100, "private-comment")

    protected suspend fun openSession(id: String = "account", wallet: FakeSdk = sdk) {
        val account = mockk<Account> { every { this@mockk.id } returns id }
        coEvery { factory.open(account, BeamNetwork.Mainnet) } returns wallet
        session = owner.acquire(account)
    }

    protected suspend fun preview(): BeamSendCoordinator.Confirmation {
        openSession()
        return coordinator.preview(session, request)
    }

    protected suspend fun prepared(): BeamSendCoordinator.Confirmation {
        val confirmation = preview()
        sdk.beforeCommit = { throw secretFailure() }
        expect(Reason.ExternalFailure) { coordinator.confirm(confirmation) }
        sdk.beforeCommit = {}
        return confirmation
    }

    protected suspend fun submitted() = preview().also { coordinator.confirm(it) }

    protected suspend fun terminal() = submitted().also {
        sdk.finish(it.operationId)
        coordinator.reconcile(session)
    }

    protected fun restart() { coordinator = BeamSendCoordinator(owner, locallyCreated) }

    protected fun clearFixture() {
        sdk.operations.clear()
        sdk.resolutions.clear()
        sdk.core.clear()
        sdk.prepares.clear()
        sdk.commits.clear()
        sdk.registrations = 0
        sdk.allocatedTransactionIds = 0
        restart()
    }

    protected suspend fun row() = sdk.sendOperations().single()

    protected suspend fun expect(reason: Reason, block: suspend () -> Any?) {
        val error = assertFailsWith<SendException> { block() }
        assertEquals(reason, error.reason)
        assertEquals("BEAM send: ${reason.name}", error.message)
        assertNull(error.cause)
        assertTrue(error.suppressed.isEmpty())
    }

    protected fun secretFailure() = IllegalStateException(request.receiverToken, Exception(request.comment)).apply {
        addSuppressed(Exception(request.receiverToken))
    }

    protected class FakeSdk : BeamWalletSession {
        override val offlineSigningState =
            MutableStateFlow<BeamOfflineSigningState>(BeamOfflineSigningState.Unavailable)
        var quotes: (BeamQuoteRequest) -> BeamSendQuote = { error("Unexpected new quote") }
        override suspend fun quoteSend(request: BeamQuoteRequest): BeamSendQuote = quotes(request)
        override suspend fun signOffline(
            operationId: String,
            request: BeamQuoteRequest,
            quoteVersion: String,
        ): BeamOfflineSignResult = error("Unexpected offline signing")
        override suspend fun exportSignedTransaction(operationId: String): ByteArray =
            error("Unexpected offline export")
        override val state = MutableStateFlow<BeamWalletState>(BeamWalletState.Ready(1))
        override val balance = MutableStateFlow(BeamBalance(available = 1000, isAuthoritative = true))
        override val transactions = MutableStateFlow(emptyList<BeamTransaction>())
        val operations = linkedMapOf<String, BeamSendOperation>()
        var recoveries = 0
        var beforeInventory: suspend () -> Unit = {}
        var beforeRecovery: suspend () -> Unit = {}
        val resolutions = mutableMapOf<String, BeamSendResolution>()
        val core = linkedMapOf<String, BeamTransaction>()
        val prepares = mutableListOf<String>()
        val commits = mutableListOf<String>()
        val pages = mutableListOf<Int>()
        var registrations = 0
        var stops = 0
        var closes = 0
        var beforePrepare: suspend () -> Unit = {}
        var afterPrepare: suspend () -> Unit = {}
        var beforeCommit: suspend () -> Unit = {}
        var atCommitBoundary: suspend () -> Unit = {}
        var afterCommit: suspend () -> Unit = {}
        var quoteChange: (BeamSendPreview) -> BeamSendPreview = { it }
        var preparedChange: (PreparedBeamSend) -> PreparedBeamSend = { it }
        var allocatedTransactionIds = 0

        override suspend fun start() = Unit
        override suspend fun stop() { stops++ }
        override suspend fun close() { closes++ }
        override suspend fun receiveAddress(type: BeamAddressType) =
            BeamAddress("receive", type, SdkBeamNetwork.Mainnet)

        override suspend fun previewSend(request: BeamSendRequest): BeamSendPreview {
            val material = "${request.receiverToken}/${request.amount}/${request.comment}".toByteArray()
            val hash = MessageDigest.getInstance("SHA-256").digest(material).joinToString("") { "%02x".format(it) }
            return quoteChange(
                BeamSendPreview(hash, 1, request.amount, 10, request.amount + 10, BeamAddressType.PublicOffline),
            )
        }

        override suspend fun prepareSend(
            operationId: String,
            request: BeamSendRequest,
            previewVersion: Long,
        ): PreparedBeamSend {
            beforePrepare()
            prepares.add(operationId)
            val quote = previewSend(request)
            // Prepare mints one stable TxID per operation and creates no Core transaction.
            val txId = operations[operationId]?.transactionId ?: nextTransactionId()
            operations.putIfAbsent(operationId, BeamSendOperation(
                operationId, txId, quote.requestHash, quote.amount, quote.fee, BeamSendResolution.Prepared(txId),
            ))
            resolutions.putIfAbsent(operationId, BeamSendResolution.Prepared(txId))
            afterPrepare()
            return preparedChange(PreparedBeamSend(operationId, txId))
        }

        /**
         * Mirrors the native commit: admission is re-checked before the first Core transaction,
         * Prepared flushes through Committing, Committing creates the recorded TxID exactly once,
         * and Submitted/Indeterminate/Terminal records are only observed, never replayed.
         */
        override suspend fun commitSend(operationId: String): BeamSendResolution {
            beforeCommit()
            commits.add(operationId)
            val current = resolutions[operationId] ?: return BeamSendResolution.NotPrepared
            val txId = current.transactionId() ?: return BeamSendResolution.NotPrepared
            if (current is BeamSendResolution.Prepared || current is BeamSendResolution.Committing) {
                if (admissionDeferred(operationId)) {
                    throw BeamFailure.SendAdmissionDeferred("Another Beam send is unresolved")
                }
                resolutions[operationId] = BeamSendResolution.Committing(txId)
                atCommitBoundary()
                if (core.putIfAbsent(txId, transaction(txId)) == null) registrations++
                resolutions[operationId] = BeamSendResolution.Submitted(txId)
            }
            afterCommit()
            return resolveSend(operationId)
        }

        /** Observation only: it never creates a Core transaction and stays safe to repeat. */
        override suspend fun resolveSend(operationId: String): BeamSendResolution {
            return resolutions[operationId] ?: BeamSendResolution.NotPrepared
        }

        /** Any other record without a terminal Core transaction defers admission, as Core does. */
        private fun admissionDeferred(operationId: String): Boolean =
            resolutions.any { (id, resolution) ->
                val status = resolution.transactionId()?.let(core::get)?.status
                id != operationId && status !in TERMINAL_STATUSES
            }

        private fun nextTransactionId(): String = when (++allocatedTransactionIds) {
            1 -> TX_ID
            2 -> OTHER_TX_ID
            else -> "%032x".format(allocatedTransactionIds)
        }

        override suspend fun sendOperations(): List<BeamSendOperation> {
            beforeInventory()
            return operations.values.map { it.copy(resolution = resolutions.getValue(it.operationId)) }
        }

        override suspend fun recoverSendOperations(): List<BeamSendOperation> {
            check(state.value is BeamWalletState.Ready)
            recoveries++
            beforeRecovery()
            for (operation in sendOperations()) {
                if (operation.resolution is BeamSendResolution.Prepared ||
                    operation.resolution is BeamSendResolution.Committing) commitSend(operation.operationId)
            }
            return sendOperations()
        }

        override suspend fun abortPrepared(operationId: String): Boolean =
            error("Coordinator must not abort ambiguous sends")

        override suspend fun transactionPage(offset: Int, limit: Int): BeamTransactionPage {
            pages.add(offset)
            val items = core.values.drop(offset).take(limit)
            val next = (offset + items.size).takeIf { it < core.size }
            return BeamTransactionPage(items, next)
        }

        fun finish(operationId: String) {
            val txId = resolutions.getValue(operationId).transactionId() ?: TX_ID
            resolutions[operationId] = BeamSendResolution.Terminal(txId, BeamTransactionStatus.Completed)
            core[txId] = transaction(txId, status = BeamTransactionStatus.Completed)
        }

        /** Seeds an unrelated durable operation that is prepared but has no Core transaction yet. */
        fun seedUnrelatedPrepared(
            operationId: String = OTHER_OPERATION_ID,
            txId: String = OTHER_TX_ID,
        ) {
            operations[operationId] = BeamSendOperation(
                operationId, txId, "c".repeat(64), 100, 10, BeamSendResolution.Prepared(txId),
            )
            resolutions[operationId] = BeamSendResolution.Prepared(txId)
        }
    }

    companion object {
        const val OPERATION_ID = "recorded-operation"
        const val OTHER_OPERATION_ID = "unrelated-operation"
        const val TX_ID = "11111111111111111111111111111111"
        const val OTHER_TX_ID = "22222222222222222222222222222222"


        /** An online quote the fake's own preview agrees with (its fee is always 10). */
        fun onlineQuote(request: BeamQuoteRequest, version: String = "v1"): BeamSendQuote {
            val amount = (request.amount as BeamSendAmount.Exact).amount
            return BeamSendQuote(amount, 10, amount + 10, 10, 0, 0, 1, 0, BeamAddressType.PublicOffline,
                version, "context", "rules")
        }

        fun transaction(
            id: String = TX_ID,
            direction: BeamTransactionDirection = BeamTransactionDirection.Outgoing,
            status: BeamTransactionStatus = BeamTransactionStatus.Registering,
        ) = BeamTransaction(id, direction, 100, 10, 1, null, null, null, status, null)
    }
}

val TERMINAL_STATUSES = setOf(
    BeamTransactionStatus.Completed, BeamTransactionStatus.Failed, BeamTransactionStatus.Canceled,
)

fun BeamSendResolution.transactionId(): String? = when (this) {
    BeamSendResolution.NotPrepared -> null
    is BeamSendResolution.Prepared -> transactionId
    is BeamSendResolution.Committing -> transactionId
    is BeamSendResolution.Submitted -> transactionId
    is BeamSendResolution.Indeterminate -> transactionId
    is BeamSendResolution.Terminal -> transactionId
}
