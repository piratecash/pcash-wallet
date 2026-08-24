package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Wallet

/** How an account proves ownership to the SDK: a spendable phrase, or a watch-only viewing key. */
sealed interface ZcashKey {
    data class Phrase(val words: List<String>, val passphrase: String) : ZcashKey
    data class ViewingKey(val key: String) : ZcashKey
}

fun Wallet.zcashKey(): ZcashKey? = when (val type = account.type) {
    is AccountType.Mnemonic -> ZcashKey.Phrase(type.words, type.passphrase)
    is AccountType.ZCashUfvKey -> ZcashKey.ViewingKey(type.key)
    is AccountType.TrezorDevice -> hardwarePublicKey?.key?.value?.let(ZcashKey::ViewingKey)
    else -> null
}
