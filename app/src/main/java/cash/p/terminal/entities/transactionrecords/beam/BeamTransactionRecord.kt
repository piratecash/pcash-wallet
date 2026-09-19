package cash.p.terminal.entities.transactionrecords.beam

import cash.p.beam.BeamTransaction
import cash.p.beam.BeamTransactionDirection
import cash.p.beam.BeamTransactionStatus
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.modules.transactions.TransactionStatus
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.transaction.TransactionSource
import java.math.BigDecimal

class BeamTransactionRecord internal constructor(
    transaction: BeamTransaction,
    source: TransactionSource,
    token: Token,
    uid: String,
    blockHeight: Int?,
    amount: BigDecimal,
    fee: BigDecimal,
) : TransactionRecord(
    uid = uid,
    transactionHash = transaction.id,
    transactionIndex = 0,
    blockHeight = blockHeight,
    confirmationsThreshold = 1,
    timestamp = transaction.createdAtEpochSeconds,
    failed = transaction.status == BeamTransactionStatus.Failed || transaction.status == BeamTransactionStatus.Canceled,
    source = source,
    transactionRecordType = if (transaction.direction == BeamTransactionDirection.Incoming) {
        TransactionRecordType.BEAM_INCOMING
    } else {
        TransactionRecordType.BEAM_OUTGOING
    },
    token = token,
    sentToSelf = transaction.direction == BeamTransactionDirection.Self,
) {
    val direction = transaction.direction
    val counterparty = transaction.counterparty
    val sdkStatus = transaction.status
    val kernelId = transaction.kernelId
    val failureReason = transaction.failureReason
    override val mainValue = TransactionValue.CoinValue(token, amount)
    val fee = TransactionValue.CoinValue(token, fee)

    private val transactionStatus = when (sdkStatus) {
        BeamTransactionStatus.Completed -> TransactionStatus.Completed
        BeamTransactionStatus.Failed, BeamTransactionStatus.Canceled -> TransactionStatus.Failed
        // A presentation ordering over BEAM's lifecycle, not a measured confirmation count. The
        // generic mapping already renders Pending as 0.15f, so these rise above it and stop short of 1.
        BeamTransactionStatus.InProgress -> TransactionStatus.Processing(0.3f)
        BeamTransactionStatus.Registering -> TransactionStatus.Processing(0.5f)
        BeamTransactionStatus.Confirming -> TransactionStatus.Processing(0.8f)
        BeamTransactionStatus.Pending, BeamTransactionStatus.Unknown -> TransactionStatus.Pending
    }

    // A reorg can revert SDK status while retaining the old proof height.
    override fun status(lastBlockHeight: Int?): TransactionStatus = transactionStatus
}
