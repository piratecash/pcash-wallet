package cash.p.terminal.core.adapters.zcash.session

/**
 * A SQLCipher key for one account's wallet database. [newlyGenerated] means the previous key is
 * gone, so any database that already exists under it is unreadable and has to be rebuilt.
 */
class ZcashDbKey(val bytes: ByteArray, val newlyGenerated: Boolean)

interface ZcashDbKeyProvider {
    fun keyFor(accountId: String): ZcashDbKey

    /** False means the key survives, so the database it opens must not be reported as erased. */
    fun drop(accountId: String): Boolean
}
