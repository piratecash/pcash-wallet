package cash.p.terminal.entities.transactionrecords

import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.modules.transactions.TransactionStatus
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.transaction.TransactionSource
import java.math.BigDecimal

class PendingTransactionRecord(
    uid: String,
    transactionHash: String,
    timestamp: Long,
    source: TransactionSource,
    token: Token,
    val amount: BigDecimal,
    toAddress: String,
    fromAddress: String,
    val expiresAt: Long,
    memo: String?,
    val isIronwoodMigration: Boolean = false,
) : TransactionRecord(
    uid = uid,
    transactionHash = transactionHash,
    transactionIndex = Int.MAX_VALUE, // pending always on top
    blockHeight = null,
    confirmationsThreshold = 1,
    timestamp = timestamp,
    failed = false,
    spam = false,
    source = source,
    transactionRecordType = TransactionRecordType.UNKNOWN, //no matter
    token = token,
    to = listOf(toAddress),
    from = fromAddress,
    sentToSelf = isIronwoodMigration,
    memo = memo
) {
    // A migration keeps the funds in the wallet, so its amount is a transfer, not a spend.
    override val mainValue = TransactionValue.CoinValue(
        token,
        if (isIronwoodMigration) amount else amount.negate(),
    )

    override fun status(lastBlockHeight: Int?) = TransactionStatus.Pending

    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAt
}
