package cash.p.terminal.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import cash.p.terminal.R
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.modules.multiswap.providers.UnstoppableProvider
import cash.p.terminal.network.changenow.api.ChangeNowHelper
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.exolix.api.ExolixHelper
import cash.p.terminal.network.quickex.api.QuickexHelper
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.network.yifi.api.YiFiHelper
import cash.p.terminal.strings.helpers.Translator
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
    // Aggregator sub-provider: Unstoppable api id (e.g. "LETSEXCHANGE") or YiFi exchanger name.
    // Drives the per-sub-provider display name in history.
    val unstoppableSubProviderId: String? = null,
) {
    fun isFinished() = status in FINISHED_STATUSES

    fun toStatusUrl(): Pair<String, String>? = when (provider) {
        SwapProvider.CHANGENOW -> ChangeNowHelper.CHANGE_NOW_URL to ChangeNowHelper.getViewTransactionUrl(transactionId)
        SwapProvider.QUICKEX -> QuickexHelper.QUICKEX_URL to QuickexHelper.getViewTransactionUrl(
            transactionId, addressOut
        )

        SwapProvider.PAYCORE -> Translator.getString(R.string.paycore_support) to AppConfigProvider.payCoreSupportUrl
        SwapProvider.EXOLIX -> ExolixHelper.EXOLIX_URL to ExolixHelper.getViewTransactionUrl(transactionId)
        SwapProvider.YIFI -> YiFiHelper.YIFI_URL to YiFiHelper.getViewTransactionUrl(
            transactionId, addressOut
        )
        SwapProvider.THORCHAIN,
        SwapProvider.MAYA,
        SwapProvider.UNSTOPPABLE -> null
    }

    companion object {
        val FINISHED_STATUSES = listOf(
            TransactionStatusEnum.FINISHED.name.lowercase(),
            TransactionStatusEnum.FAILED.name.lowercase(),
            TransactionStatusEnum.REFUNDED.name.lowercase()
        )
    }
}

fun swapProviderDisplayTitle(provider: SwapProvider, subProviderId: String?): String = when {
    provider == SwapProvider.UNSTOPPABLE -> UnstoppableProvider.displayTitle(subProviderId) ?: provider.title
    provider == SwapProvider.YIFI && !subProviderId.isNullOrBlank() -> "${provider.title} · $subProviderId"
    else -> provider.title
}
