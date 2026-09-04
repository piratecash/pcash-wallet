package cash.p.terminal.entities

import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.meta
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.TimeUnit

data class PendingTransactionDraft(
    val id: String = UUID.randomUUID().toString(),
    val wallet: Wallet,
    val token: Token,
    val amount: BigDecimal,
    val fee: BigDecimal?,
    val sdkBalanceAtCreation: BigDecimal,
    val fromAddress: String,
    val toAddress: String,
    val meta: String? = token.type.meta,
    val memo: String? = null,
    val txHash: String? = null,
    val nonce: Long? = null,
    val timestamp: Long = System.currentTimeMillis(),
    /** How long the row stays in history and holds back the available balance. */
    val timeToLiveMs: Long = DEFAULT_TIME_TO_LIVE_MS,
) {
    companion object {
        val DEFAULT_TIME_TO_LIVE_MS: Long = TimeUnit.HOURS.toMillis(1)
    }
}
