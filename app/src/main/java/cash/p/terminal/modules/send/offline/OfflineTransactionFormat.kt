package cash.p.terminal.modules.send.offline

import cash.p.terminal.entities.OfflineSignedTransaction

enum class OfflineTransactionFormat {
    Pcash,
    Raw,
}

internal fun OfflineTransactionFormat.content(transaction: OfflineSignedTransaction): String =
    when (this) {
        OfflineTransactionFormat.Pcash -> transaction.pcashPayload
        OfflineTransactionFormat.Raw -> transaction.rawHex
    }
