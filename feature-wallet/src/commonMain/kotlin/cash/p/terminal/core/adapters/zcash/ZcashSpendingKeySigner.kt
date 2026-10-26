package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.UnsupportedException
import cash.p.zcash.PreparedTransaction
import cash.p.zcash.ZcashNetwork
import cash.p.zcash.ZcashSdk
import cash.p.zcash.ZcashWallet
import cash.p.zcash.deriveSpendingKey
import cash.p.zcash.importSpendingKey

/** Signs with the account's own spending key, wiping it from memory once signing is done. */
class ZcashSpendingKeySigner(private val zcashKey: ZcashKey) : ZcashTransactionSigner {

    override suspend fun sign(
        wallet: ZcashWallet,
        account: Int,
        transaction: PreparedTransaction,
    ): PreparedTransaction {
        val key = deriveSpendingKeyBytes(zcashKey)
        return try {
            wallet.sign(account = account, transaction = transaction, spendingKey = key)
        } finally {
            key.fill(0)
        }
    }
}

internal suspend fun deriveSpendingKeyBytes(zcashKey: ZcashKey): ByteArray = when (zcashKey) {
    // The wallet database was restored without an explicit account index, so index 0 is
    // the only key that matches it.
    is ZcashKey.Phrase -> ZcashSdk.deriveSpendingKey(
        phrase = zcashKey.words.joinToString(" "),
        network = ZcashNetwork.MAIN,
        passphrase = zcashKey.passphrase,
    )

    is ZcashKey.SpendingKey -> ZcashSdk.importSpendingKey(zcashKey.key, ZcashNetwork.MAIN)

    is ZcashKey.ViewingKey -> throw UnsupportedException("Zcash spending requires a spending key")
}
