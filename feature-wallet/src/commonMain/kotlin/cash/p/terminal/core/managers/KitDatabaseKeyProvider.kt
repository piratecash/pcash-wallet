package cash.p.terminal.core.managers

class DatabaseKey(val bytes: ByteArray, val isNew: Boolean)

/** Per-account key of a kit's encrypted database. */
interface KitDatabaseKeyProvider {
    suspend fun awaitKey(accountId: String): ByteArray = awaitDatabaseKey(accountId).bytes

    /** [DatabaseKey.isNew] is true when this call created and stored the key. */
    suspend fun awaitDatabaseKey(accountId: String): DatabaseKey

    fun remove(accountId: String)
}

interface BitcoinKitDatabaseKeyProvider : KitDatabaseKeyProvider
interface TonKitDatabaseKeyProvider : KitDatabaseKeyProvider
interface TronKitDatabaseKeyProvider : KitDatabaseKeyProvider
interface EvmKitDatabaseKeyProvider : KitDatabaseKeyProvider
interface SolanaKitDatabaseKeyProvider : KitDatabaseKeyProvider
interface StellarKitDatabaseKeyProvider : KitDatabaseKeyProvider

/** Key of an account's BEAM wallet database. */
interface BeamKitDatabaseKeyProvider {
    /** Throws when the account is deleted or awaiting cleanup. */
    fun ensureAvailable(accountId: String)

    /** [DatabaseKey.isNew] is true when this call created and stored the key. */
    fun keyForInitialization(accountId: String, network: BeamNetwork): DatabaseKey

    fun hasAnyKey(): Boolean

    fun storageIds(): Set<String>

    /** Requires the storage to be erased first: a partially erased wallet keeps its key for the retry. */
    fun removeStorageKey(id: String)

    /** Explicit reset only: drops the key while the ciphertext may still be on disk. */
    fun shredStorageKey(id: String)
}
