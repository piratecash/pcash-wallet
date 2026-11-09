package cash.p.terminal.modules.transactions

import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import java.math.BigDecimal

fun BigDecimal?.positiveOrNull(): BigDecimal? = this?.takeIf { it > BigDecimal.ZERO }

fun TransactionStatusEnum?.toUniversalStatus() = when (this) {
    null,
    TransactionStatusEnum.NEW,
    TransactionStatusEnum.WAITING,
    TransactionStatusEnum.CREATED_OR_WAIT_USER -> TransactionStatus.Pending

    TransactionStatusEnum.CONFIRMING,
    TransactionStatusEnum.EXCHANGING,
    TransactionStatusEnum.SENDING,
    TransactionStatusEnum.VERIFYING -> TransactionStatus.Processing(
        (ordinal + 1) * (1f / (TransactionStatusEnum.FINISHED.ordinal + 1))
    )

    TransactionStatusEnum.FINISHED -> TransactionStatus.Completed

    TransactionStatusEnum.UNKNOWN,
    TransactionStatusEnum.FAILED,
    TransactionStatusEnum.REFUNDED -> TransactionStatus.Failed
}
