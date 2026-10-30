package cash.p.terminal.network.unstoppable.domain.entity

import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import java.math.BigDecimal

data class UnstoppableTrack(
    val status: TransactionStatusEnum?,
    val actionRequired: Boolean,
    val pauseReason: String?,
    val toAmount: BigDecimal?,
    val legs: List<UnstoppableTrackLeg>,
)

data class UnstoppableTrackLeg(
    val type: Type,
    val status: Status,
    val hash: String?,
    val chainId: String?,
    val fromAsset: String?,
    val fromAmount: BigDecimal?,
    val toAsset: String?,
    val toAmount: BigDecimal?,
    val toAddress: String?,
) {
    enum class Type { NativeSend, Swap, Other }

    enum class Status { NotStarted, InProgress, Completed, Failed }
}
