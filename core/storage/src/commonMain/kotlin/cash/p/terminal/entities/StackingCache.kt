package cash.p.terminal.entities

import androidx.room.Entity
import java.math.BigDecimal

@Entity(primaryKeys = ["coin", "address"])
data class StackingCache(
    val coin: String,
    val address: String,
    val unpaid: BigDecimal,
    val totalIncome: BigDecimal,
    val nextAccrualAt: Long?,
    val nextPayoutAt: Long?,
    val cachedBalance: BigDecimal,
    val timestamp: Long,
)
