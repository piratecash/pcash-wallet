package cash.p.terminal.entities.transactionrecords.thorchain

import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.modules.transactions.TransactionStatus
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.thorchainkit.models.Transaction

class ThorchainTransactionRecord(
    uid: String,
    transaction: Transaction,
    spam: Boolean,
    source: TransactionSource,
    token: Token,
    val type: Type,
    // the native fee of the user's own outgoing transaction
    val fee: TransactionValue?,
) : TransactionRecord(
    uid = uid,
    transactionHash = transaction.hash,
    transactionIndex = 0,
    blockHeight = if (transaction.isPending) null else transaction.blockHeight.toInt(),
    confirmationsThreshold = 1,
    timestamp = transaction.timestamp,
    failed = transaction.isFailed,
    spam = spam,
    source = source,
    transactionRecordType = type.transactionRecordType,
    token = token,
    to = (type as? Type.Outgoing)?.to?.let(::listOf),
    from = (type as? Type.Incoming)?.from,
    sentToSelf = (type as? Type.Outgoing)?.sentToSelf == true,
    memo = transaction.memo,
) {
    override val mainValue: TransactionValue = type.value

    sealed class Type {
        abstract val value: TransactionValue

        data class Incoming(override val value: TransactionValue, val from: String?) : Type()

        data class Outgoing(
            override val value: TransactionValue,
            val to: String?,
            val sentToSelf: Boolean,
        ) : Type()

        val transactionRecordType: TransactionRecordType
            get() = when (this) {
                is Incoming -> TransactionRecordType.THORCHAIN_INCOMING
                is Outgoing -> TransactionRecordType.THORCHAIN_OUTGOING
            }
    }

    companion object {
        // The bank denom, not Midgard's notation: the send side knows only the denom it spent.
        fun uid(transactionHash: String, denom: String) = "$transactionHash-$denom"
    }

    // Midgard indexes committed blocks only and Cosmos finality is instant, so a block means done.
    override fun status(lastBlockHeight: Int?) = when {
        failed -> TransactionStatus.Failed
        blockHeight == null -> TransactionStatus.Pending
        else -> TransactionStatus.Completed
    }
}
