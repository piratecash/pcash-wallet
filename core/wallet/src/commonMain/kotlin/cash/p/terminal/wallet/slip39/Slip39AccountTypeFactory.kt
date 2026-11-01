package cash.p.terminal.wallet.slip39

import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext

class Slip39AccountTypeFactory(private val dispatcherProvider: DispatcherProvider) {

    /** Decryption cost grows with the share's iteration exponent, so it never runs on the caller's thread. */
    suspend fun create(canonicalShare: List<String>, passphrase: String): AccountType.Slip39 =
        withContext(dispatcherProvider.default) {
            val share = Slip39Shamir.parseShare(canonicalShare.joinToString(" "))
            require(share.isSingleShare) { "Not a 1-of-1 SLIP-39 share" }
            val seed = Slip39Shamir.combine(listOf(share)).decrypt(passphrase)
            AccountType.Slip39(share.words(), passphrase, seed)
        }
}
