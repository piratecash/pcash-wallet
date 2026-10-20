package cash.p.terminal.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.SwapProvider
import java.math.BigDecimal

@Entity
data class SwapProviderTransaction(
    @PrimaryKey
    val date: Long = System.currentTimeMillis(),
    val outgoingRecordUid: String?,
    val transactionId: String,
    val status: String,
    val provider: SwapProvider,

    val coinUidIn: String,
    val blockchainTypeIn: String,
    val amountIn: BigDecimal,
    val addressIn: String,

    val coinUidOut: String,
    val blockchainTypeOut: String,
    val amountOut: BigDecimal,
    val addressOut: String,

    val amountOutReal: BigDecimal? = null,
    val finishedAt: Long? = null,
    val incomingRecordUid: String? = null,
    @ColumnInfo(defaultValue = "''")
    val accountId: String = "",
    // Canonical inbound (deposit/burn) tx hash. Supplies inboundTxHash to the Unstoppable /track call
    // (required for EVM sub-providers). Null for providers that track purely by their order id.
    val depositTransactionHash: String? = null,
    // Aggregator sub-provider: Unstoppable api id (e.g. "LETSEXCHANGE"), YiFi exchanger name or
    // p.cash backend provider name (e.g. "changelly"). Drives the per-sub-provider display name in history.
    val subProviderId: String? = null,
    // p.cash backend only: provider order id (track link) and the EVM address authorising status lookups.
    val providerExternalId: String? = null,
    val providerWalletAddress: String? = null,
    // TokenQuery.id of each side; null for the PayCore RUB side and for rows saved before it existed.
    val tokenQueryIdIn: String? = null,
    val tokenQueryIdOut: String? = null,
) {
    fun isFinished() = status in FINISHED_STATUSES

    companion object {
        val FINISHED_STATUSES = listOf(
            TransactionStatusEnum.FINISHED.name.lowercase(),
            TransactionStatusEnum.FAILED.name.lowercase(),
            TransactionStatusEnum.REFUNDED.name.lowercase()
        )
    }
}
