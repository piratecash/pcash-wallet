package cash.p.terminal.modules.restoreaccount.restoreshamir

import cash.p.terminal.core.managers.SeedPhraseQrCrypto
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.wallet.slip39.Slip39Shamir

/** A scanned SLIP-39 share that opens the Shamir restore as its first part. */
data class ShamirPrefill(val words: List<String>, val passphrase: String) {
    override fun toString(): String = "ShamirPrefill(words=${words.size})"
}

/** Plain text that is a valid SLIP-39 share of any scheme. */
fun String.toShamirPrefillOrNull(): ShamirPrefill? =
    tryOrNull { Slip39Shamir.parseShare(this) }?.let { ShamirPrefill(it.words(), passphrase = "") }

fun SeedPhraseQrCrypto.DecryptedSeed.toShamirPrefillOrNull(): ShamirPrefill? =
    takeIf { kind == SeedPhraseQrCrypto.KIND_SLIP39 }?.let { ShamirPrefill(words, passphrase) }
