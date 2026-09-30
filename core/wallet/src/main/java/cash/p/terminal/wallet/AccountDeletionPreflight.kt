package cash.p.terminal.wallet

interface AccountDeletionPreflight {
    suspend fun ensureCanDelete(accountIds: List<String>)
    suspend fun cleanupDeleted(accountIds: List<String>)
    suspend fun prepareExplicitReset()
    suspend fun resumePendingReset()
    suspend fun ensureExplicitResetCleaned()
    suspend fun finishExplicitReset()
    // Automatic keystore recovery must never authorize deletion.
    // Synchronous: it only reads prefs/files/enabled wallets, so callers on the keystore's
    // synchronous contract (e.g. KeyStoreCleaner.cleanApp()) do not need to bridge into a coroutine.
    fun ensureCanReset()
}

class AccountDeletionBlockedException(cause: Throwable? = null) :
    IllegalStateException("Wallet data must be retained because safe deletion cannot be established", cause)
