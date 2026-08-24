package cash.p.terminal.modules.send.zcash

import cash.p.terminal.core.ISendZcashAdapter
import cash.p.terminal.entities.PendingTransactionDraft
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

/**
 * The app keeps its outgoing transaction row independently from the SDK's input reservation. The
 * window covers the chain expiry of 40 blocks with room for slow blocks, so a still-valid
 * transaction is never dropped from history early.
 */
private val ZCASH_PENDING_TIME_TO_LIVE_MS = TimeUnit.HOURS.toMillis(3)

/**
 * The single shape of a pending ZEC row: every send path registers through here, so the expiry
 * window and the empty from address — ZEC spends notes, not an account — are defined once.
 */
internal fun IAdapterManager.zcashPendingDraft(
    wallet: Wallet,
    amount: BigDecimal,
    fee: BigDecimal?,
    toAddress: String,
    memo: String? = null,
    txHash: String? = null,
    availableBalance: BigDecimal = BigDecimal.ZERO,
): PendingTransactionDraft = PendingTransactionDraft(
    wallet = wallet,
    token = wallet.token,
    amount = amount,
    fee = fee,
    sdkBalanceAtCreation = getZcashSdkBalance(wallet, availableBalance),
    fromAddress = "",
    toAddress = toAddress,
    memo = memo,
    txHash = txHash,
    timeToLiveMs = ZCASH_PENDING_TIME_TO_LIVE_MS,
)

internal fun getZcashAvailableToSend(adapter: ISendZcashAdapter): BigDecimal =
    calculateZcashAvailableToSend(
        adapterAvailable = adapter.balanceData.available,
        fee = adapter.fee.value,
    )

internal fun calculateZcashAvailableToSend(
    adapterAvailable: BigDecimal,
    fee: BigDecimal,
): BigDecimal {
    return (adapterAvailable - fee).coerceAtLeast(BigDecimal.ZERO)
}

internal fun IAdapterManager.getZcashSdkBalance(
    wallet: Wallet,
    fallback: BigDecimal,
): BigDecimal =
    getBalanceAdapterForWallet(wallet)?.balanceData?.available ?: fallback
