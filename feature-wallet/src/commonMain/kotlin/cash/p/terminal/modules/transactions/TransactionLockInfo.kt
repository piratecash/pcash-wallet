package cash.p.terminal.modules.transactions

import io.horizontalsystems.hodler.LockTimeInterval
import java.math.BigDecimal
import java.util.Date

data class TransactionLockInfo(
    val lockedUntil: Date,
    val originalAddress: String,
    val amount: BigDecimal?,
    val lockTimeInterval: LockTimeInterval
)
