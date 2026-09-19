package cash.p.terminal.entities.transactionrecords.beam

import cash.p.beam.BeamTransaction
import cash.p.beam.BeamTransactionDirection
import cash.p.terminal.modules.send.beam.BeamAmount
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.transaction.TransactionSource
import java.math.BigDecimal

class BeamTransactionRecordConverter(
    private val source: TransactionSource,
    private val token: Token,
) {
    fun convert(transaction: BeamTransaction): BeamTransactionRecord {
        val amount = toBeam(transaction.amount)
        return BeamTransactionRecord(
            transaction = transaction,
            source = source,
            token = token,
            uid = recordUid(source.account.id, transaction.id),
            blockHeight = transaction.proofHeight?.takeIf { it in 1L..Int.MAX_VALUE.toLong() }?.toInt(),
            amount = if (transaction.direction == BeamTransactionDirection.Incoming) amount else amount.negate(),
            fee = toBeam(transaction.fee),
        )
    }

    private fun toBeam(value: Long): BigDecimal {
        require(value >= 0) { "BEAM atomic amounts must be non-negative" }
        return BigDecimal.valueOf(value, BeamAmount.DECIMALS)
    }

    companion object {
        fun recordUid(accountId: String, transactionId: String) = "beam:${accountId.length}:$accountId:$transactionId"
    }
}
