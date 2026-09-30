package cash.p.terminal.modules.send.beam

import cash.p.beam.BeamOfflineSendState
import cash.p.beam.BeamSendDeliveryMode
import cash.p.beam.BeamSendOperation
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.core.toRawHexString
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.entities.OfflineBeamMetadata
import cash.p.terminal.entities.OfflineSignedTransaction
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class BeamOfflineOperations(
    private val owner: BeamSessionOwner,
    private val payloadEncoder: OfflineTransactionPayloadEncoder,
    private val dispatcherProvider: DispatcherProvider,
) {
    data class Inventory(val operations: List<BeamSendOperation>)

    fun observe(accountId: String) = flow {
        while (true) {
            val session = owner.current?.takeIf { it.accountId == accountId }
            val inventory = try {
                if (session == null) Inventory(emptyList())
                else withContext(dispatcherProvider.io) {
                    owner.withSession(session) { sdk -> Inventory(sdk.sendOperations()) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Inventory(emptyList())
            }
            emit(inventory)
            delay(2_000)
        }
    }.distinctUntilChanged()

    suspend fun export(wallet: Wallet, operationId: String): OfflineSignedTransaction =
        withContext(dispatcherProvider.io) {
            val session = checkNotNull(owner.current?.takeIf { it.accountId == wallet.account.id })
            owner.withSession(session) { sdk ->
                val operation = ownSigned(sdk.sendOperations()).single { it.operationId == operationId }
                // SDK flushes Exported before exposing bytes. A lost response is safe to retry.
                val bytes = sdk.exportSignedTransaction(operationId)
                val draft = draft(wallet, operation, bytes, session.network.name.lowercase())
                OfflineSignedTransaction(draft.rawHex, payloadEncoder.encode(draft), draft.txHash, draft.createdAt)
            }
        }

    private fun draft(wallet: Wallet, operation: BeamSendOperation, bytes: ByteArray, network: String) =
        OfflineSignedTransactionDraft(
            wallet = wallet,
            amount = BigDecimal.valueOf(operation.amount, 8),
            fee = BigDecimal.valueOf(operation.fee, 8),
            toAddress = "",
            rawHex = bytes.toRawHexString(),
            txHash = checkNotNull(operation.mainKernelId),
            inputOutpoints = emptyList(),
            beamMetadata = OfflineBeamMetadata(
                version = 1,
                network = network,
                rulesSignature = checkNotNull(operation.rules),
                mainKernelId = checkNotNull(operation.mainKernelId),
                coreTxId = operation.transactionId,
            ),
        )

    companion object {
        fun ownSigned(operations: List<BeamSendOperation>): List<BeamSendOperation> = operations.filter {
            it.deliveryMode == BeamSendDeliveryMode.Offline &&
                it.offlineState in setOf(BeamOfflineSendState.Signed, BeamOfflineSendState.Exported)
        }

        fun ownExported(operations: List<BeamSendOperation>): List<BeamSendOperation> =
            ownSigned(operations).filter { it.offlineState == BeamOfflineSendState.Exported }
    }
}
