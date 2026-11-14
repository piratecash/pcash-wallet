package cash.p.terminal.entities.transactionrecords.tron

import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.entities.transactionrecords.evm.TransferEvent
import cash.p.terminal.modules.transactions.TransactionStatus
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.tronkit.models.Transaction
import io.horizontalsystems.tronkit.models.TriggerSmartContract

class TronTransactionRecord(
    from: String? = null,
    to: String? = null,
    sentToSelf: Boolean = false,
    token: Token,
    source: TransactionSource,
    spam: Boolean = false,
    transactionRecordType: TransactionRecordType,
    val spender: String? = null,
    val value: TransactionValue? = null,
    val contractAddress: String? = null,
    val method: String? = null,
    val incomingEvents: List<TransferEvent>? = null,
    val outgoingEvents: List<TransferEvent>? = null,
    val transaction: Transaction,
    val foreignTransaction: Boolean = false,
) : TransactionRecord(
    uid = transaction.hashString,
    transactionHash = transaction.hashString,
    transactionIndex = 0,
    blockHeight = transaction.blockNumber?.toInt(),
    confirmationsThreshold = CONFIRMATIONS_THRESHOLD,
    timestamp = transaction.timestamp / 1000,
    failed = transaction.isFailed,
    spam = spam,
    source = source,
    transactionRecordType = transactionRecordType,
    token = token,
    to = to?.let(::listOf),
    from = from,
    sentToSelf = sentToSelf,
) {

    val fee: TransactionValue?

    val isOwnOutgoing: Boolean
        get() = !foreignTransaction && transactionRecordType in OWN_OUTGOING_TYPES

    /** TRC-20 transfers, approvals and other contract calls; only these spend Energy. */
    val isSmartContractCall: Boolean
        get() = transaction.contract is TriggerSmartContract

    /** Net usage and fee both zero is a placeholder from the explorer, not a real receipt. */
    val isReceiptMissing: Boolean
        get() = with(transaction) {
            listOf(netUsage, netFee, energyUsage, energyFee, energyUsageTotal).any { it == null } ||
                    (netUsage == 0L && netFee == 0L) ||
                    (isSmartContractCall && originEnergyUsage == null)
        }

    init {
        val feeAmount: Long? = transaction.fee
        fee = if (feeAmount != null) {
            val feeDecimal = feeAmount.toBigDecimal()
                .movePointLeft(token.decimals).stripTrailingZeros()

            TransactionValue.CoinValue(token, feeDecimal)
        } else {
            null
        }
    }

    override fun status(lastBlockHeight: Int?): TransactionStatus {
        when {
            failed -> {
                return TransactionStatus.Failed
            }

            transaction.confirmed -> {
                return TransactionStatus.Completed
            }

            blockHeight != null && lastBlockHeight != null -> {
                val threshold = confirmationsThreshold ?: 1
                val confirmations = lastBlockHeight - blockHeight.toInt() + 1

                return if (confirmations >= threshold) {
                    TransactionStatus.Completed
                } else {
                    TransactionStatus.Processing(confirmations.toFloat() / threshold.toFloat())
                }
            }

            else -> return TransactionStatus.Pending
        }
    }

    val needsReceipt: Boolean
        get() = isOwnOutgoing && blockHeight != null && isReceiptMissing

    override val mainValue: TransactionValue?
        get() {
            return if (transactionRecordType == TransactionRecordType.TRON_APPROVE ||
                transactionRecordType == TransactionRecordType.TRON_INCOMING ||
                transactionRecordType == TransactionRecordType.TRON_OUTGOING
            ) {
                value
            } else if (transactionRecordType == TransactionRecordType.TRON_CONTRACT_CALL ||
                transactionRecordType == TransactionRecordType.TRON_EXTERNAL_CONTRACT_CALL
            ) {
                val (incomingValues, outgoingValues) = EvmTransactionRecord.combined(
                    incomingEvents!!,
                    outgoingEvents!!
                )

                when {
                    (incomingValues.isEmpty() && outgoingValues.size == 1) -> outgoingValues.first()
                    (incomingValues.size == 1 && outgoingValues.isEmpty()) -> incomingValues.first()
                    else -> null
                }
            } else {
                null
            }
        }

    companion object {
        const val CONFIRMATIONS_THRESHOLD = 19

        private val OWN_OUTGOING_TYPES = setOf(
            TransactionRecordType.TRON_OUTGOING,
            TransactionRecordType.TRON_APPROVE,
            TransactionRecordType.TRON_CONTRACT_CALL,
        )
    }
}
