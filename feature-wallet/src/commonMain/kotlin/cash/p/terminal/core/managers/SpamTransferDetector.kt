package cash.p.terminal.core.managers

import cash.p.terminal.core.providers.SharedAppConfig
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.evm.TransferEvent
import cash.p.terminal.wallet.entities.TokenType
import java.math.BigDecimal

object SpamTransferDetector {

    /** Senders whose transfers in [events] look like spam. */
    fun spamSenders(events: List<TransferEvent>): List<String> {
        val spamTokenSenders = mutableListOf<String>()
        val nativeSenders = mutableListOf<String>()
        var totalNativeTransactionValue: TransactionValue? = null
        var hasOwnNativeDebit = false

        events.forEach { event ->
            val value = event.value
            val address = event.address
            if (value is TransactionValue.CoinValue && value.token.type == TokenType.Native) {
                // Debiting the user's own coin needs their signature: such a transaction is not dust.
                if (value.value.signum() < 0) {
                    hasOwnNativeDebit = true
                    return@forEach
                }
                val totalNativeValue =
                    totalNativeTransactionValue?.decimalValue ?: BigDecimal.ZERO
                totalNativeTransactionValue = TransactionValue.CoinValue(
                    value.token,
                    value.value + totalNativeValue
                )
                address?.let { nativeSenders.add(it) }
            } else {
                if (address != null && isSpam(value)) {
                    spamTokenSenders.add(address)
                }
            }
        }

        if (hasOwnNativeDebit) return spamTokenSenders

        if (totalNativeTransactionValue != null && isSpam(
                totalNativeTransactionValue
            ) && nativeSenders.isNotEmpty()
        ) {
            spamTokenSenders.addAll(nativeSenders)
        }

        return spamTokenSenders
    }

    private fun isSpam(transactionValue: TransactionValue): Boolean {
        val spamCoinLimits = SharedAppConfig.spamCoinValueLimits
        val value = transactionValue.decimalValue?.abs()

        var limit: BigDecimal = BigDecimal.ZERO
        when (transactionValue) {
            is TransactionValue.CoinValue,
            is TransactionValue.JettonValue -> {
                if (value?.signum() == 0) return true
                limit = spamCoinLimits[transactionValue.coinCode] ?: BigDecimal.ZERO
            }

            is TransactionValue.NftValue -> {
                if (transactionValue.value > BigDecimal.ZERO)
                    return false
            }

            is TransactionValue.RawValue -> return transactionValue.tokenQuery == null
            is TransactionValue.TokenValue -> return true
        }

        return limit > value
    }

    fun isSpam(events: List<TransferEvent>): Boolean {
        return spamSenders(events).isNotEmpty()
    }
}
