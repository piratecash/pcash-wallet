package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.HardwarePublicKey

/** How an account proves ownership to the SDK: a phrase, or a key the SDK classifies itself. */
sealed interface ZcashKey {
    data class Phrase(val words: List<String>, val passphrase: String) : ZcashKey

    /** An imported key, passed to the SDK verbatim; only spendability matters to the app. */
    sealed interface Standalone : ZcashKey {
        val key: String
    }

    data class ViewingKey(override val key: String) : Standalone
    data class SpendingKey(override val key: String) : Standalone
}

fun AccountType.zcashKey(hardwarePublicKey: HardwarePublicKey? = null): ZcashKey? = when (this) {
    is AccountType.Mnemonic -> ZcashKey.Phrase(words, passphrase)
    is AccountType.ZCashUfvKey -> ZcashKey.ViewingKey(key)
    is AccountType.ZCashSaplingKey -> key.asKey(spendable = isSpendingKey)
    is AccountType.HdExtendedKey -> keySerialized.asKey(spendable = !hdExtendedKey.isPublic)
    is AccountType.TrezorDevice -> hardwarePublicKey?.key?.value?.let(ZcashKey::ViewingKey)
    else -> null
}

fun Wallet.zcashKey(): ZcashKey? = account.type.zcashKey(hardwarePublicKey)

private fun String.asKey(spendable: Boolean): ZcashKey.Standalone =
    if (spendable) ZcashKey.SpendingKey(this) else ZcashKey.ViewingKey(this)
