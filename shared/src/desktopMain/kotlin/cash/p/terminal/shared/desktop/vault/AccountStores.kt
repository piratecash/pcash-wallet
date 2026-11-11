package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.managers.DatabaseKey
import cash.p.terminal.core.storage.AccountDaos
import cash.p.terminal.core.storage.AccountsDao
import cash.p.terminal.core.storage.desktop.AccountDatabase
import cash.p.terminal.core.storage.desktop.AccountDatabase_Impl
import cash.p.terminal.core.storage.desktop.AccountPointer
import cash.p.terminal.core.storage.desktop.AccountSetting
import cash.p.terminal.core.storage.desktop.AccountStoreMeta
import cash.p.terminal.wallet.entities.AccountRecord
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val logger = Logger.withTag("AccountStores")

internal class AccountStore(
    private val database: EncryptedDatabase<AccountDatabase>,
    accountId: String,
    val storageId: String,
    stores: AccountStores,
) : AccountDaos by database.room, AutoCloseable {
    private val accounts = PointerLevelAccountsDao(database.room.accountsDao(), accountId, stores)
    private val storeDao = database.room.accountStoreDao()

    // Blocking callers over a blocking DAO: nothing inside suspends.
    private val kitKeysLock = ReentrantLock()

    override fun accountsDao(): AccountsDao = accounts

    fun openNow() = database.openNow()

    /** Lives with the account, so it is the same from whichever level the account is visible. */
    fun kitKey(kitName: String, newKey: () -> ByteArray): DatabaseKey = kitKeysLock.withLock {
        val keys = storeDao.meta()?.kitKeys.orEmpty()
        keys[kitName]?.let { return DatabaseKey(it, isNew = false) }
        val key = newKey()
        storeDao.putMeta(AccountStoreMeta(kitKeys = keys + (kitName to key)))
        DatabaseKey(key, isNew = true)
    }

    fun hasKitKey(kitName: String): Boolean =
        kitKeysLock.withLock { storeDao.meta()?.kitKeys?.containsKey(kitName) == true }

    fun removeKitKey(kitName: String) = kitKeysLock.withLock {
        val meta = storeDao.meta() ?: return@withLock
        storeDao.putMeta(AccountStoreMeta(kitKeys = meta.kitKeys - kitName))
    }

    fun setting(key: String): String? = storeDao.setting(key)

    fun putSetting(key: String, value: String) = storeDao.putSetting(AccountSetting(key, value))

    override fun close() = database.close()
}

/** The level of an account is where its pointer lies; the column in its store is not used here. */
private class PointerLevelAccountsDao(
    private val delegate: AccountsDao,
    private val accountId: String,
    private val stores: AccountStores,
) : AccountsDao by delegate {

    override fun loadAccount(id: String): AccountRecord? = delegate.loadAccount(id)?.atPointerLevel()

    override fun getAll(accountsMinLevel: Int): List<AccountRecord> =
        visibleRecords().filter { it.level >= accountsMinLevel }

    override fun getAccountForLevel(level: Int): List<AccountRecord> = visibleRecords().filter { it.level == level }

    override fun getLevel(id: String): Int? = delegate.getLevel(id)?.let { stores.levelOf(accountId) }

    override fun getCountByLevel(level: Int): Int = getAccountForLevel(level).size

    /** Once the row is gone the account is too: nothing may keep its store. */
    override fun clearDeleted(accountIds: List<String>) {
        delegate.clearDeleted(accountIds)
        if (accountId in accountIds && delegate.getIds().isEmpty()) stores.remove(accountId)
    }

    private fun visibleRecords() = delegate.getAll(Int.MIN_VALUE).mapNotNull { it.atPointerLevel() }

    private fun AccountRecord.atPointerLevel(): AccountRecord? {
        val pointerLevel = stores.levelOf(accountId) ?: return null
        val record = this
        return copy(level = pointerLevel).also { it.deleted = record.deleted }
    }
}

/** The stores reachable through the pointers of the open chain. */
internal class AccountStores(
    private val environment: StorageEnvironment,
    private val chain: SafeChain,
) : AutoCloseable {
    private val random = SecureRandom()
    private val stores = MutableStateFlow<Map<String, AccountStore>>(emptyMap())

    @Volatile
    private var levels: Map<String, Int> = emptyMap()

    // Out of the session's view but still open: an adapter being stopped may still write to its store.
    @Volatile
    private var retiring: Map<String, AccountStore> = emptyMap()

    val all: List<AccountStore> get() = stores.value.values.toList()
    val changes: Flow<List<AccountDaos>> = stores.map { it.values.toList() }

    val accountIds: Set<String> get() = stores.value.keys

    fun store(accountId: String): AccountStore? = stores.value[accountId] ?: retiring[accountId]

    /** Null for a pointer whose store is not open: that account is not visible now. */
    fun levelOf(accountId: String): Int? = levels[accountId].takeIf { accountId in stores.value }

    fun openAll() {
        val pointers = chain.safes.map { it.dao.pointers() }
        try {
            val visible = resolve(pointers)
            deleteUpperDuplicates(pointers, visible)
            // Registered one by one, so close() owns whatever is open if a later step fails.
            visible.forEach { (id, pointer) -> openVisible(pointer)?.let { stores.value += id to it } }
            refreshLevels()
        } finally {
            pointers.wipeKeys()
        }
    }

    fun refreshLevels() {
        val pointers = chain.safes.map { it.dao.pointers() }
        val rootLevel = chain.rootLevel
        levels = pointers.flatMapIndexed { index, safe -> safe.map { it.accountId to rootLevel + index } }.toMap()
        pointers.wipeKeys()
    }

    /** After the chain narrowed: stores it no longer points to leave the view and wait for [closeRetired]. */
    fun retireUnreachable(): List<String> {
        refreshLevels()
        val (kept, unreachable) = stores.value.toList().partition { (id, _) -> id in levels }
        retiring += unreachable
        stores.value = kept.toMap()
        return unreachable.map { (id, _) -> id }
    }

    fun closeRetired(accountIds: List<String>) {
        val closing = accountIds.mapNotNull(retiring::get)
        retiring -= accountIds.toSet()
        closing.forEach(AccountStore::close)
    }

    /** The durable pointer decides which store an account has: a second one would be lost at the next login. */
    fun prepare(accountId: String) {
        if (accountId in stores.value) return
        val store = chain.durablePointer(accountId)?.wipingKey(::openNamedStore) ?: createStoreWithPointer(accountId)
        try {
            environment.checkpoints.reached(StorageCheckpoint.PREPARE_POINTER_WRITTEN)
            refreshLevels()
        } catch (e: Throwable) {
            store.close()
            throw e
        }
        stores.value += accountId to store
    }

    /** Takes the lock itself: it is called from a DAO, outside any vault operation. */
    fun remove(accountId: String) = environment.lock.exclusive {
        val store = stores.value[accountId] ?: return@exclusive
        val storageId = chain.durablePointer(accountId)?.wipingKey { it.storageId }
        stores.value -= accountId
        chain.deletePointers(listOf(accountId))
        refreshLevels()
        store.close()
        storageId?.let(::deleteStoreFiles)
    }

    override fun close() {
        val closing = stores.value.values + retiring.values
        stores.value = emptyMap()
        retiring = emptyMap()
        closing.forEach(AccountStore::close)
    }

    /** Lower safes come later, so a duplicate left by a crash resolves to the lowest level. */
    private fun resolve(pointers: List<List<AccountPointer>>): Map<String, AccountPointer> =
        pointers.flatten().associateBy { it.accountId }

    private fun deleteUpperDuplicates(pointers: List<List<AccountPointer>>, visible: Map<String, AccountPointer>) {
        chain.safes.zip(pointers).forEach { (safe, safePointers) ->
            val duplicates = safePointers.filter { visible[it.accountId] !== it }.map { it.accountId }
            if (duplicates.isNotEmpty()) safe.dao.deletePointers(duplicates)
        }
    }

    /**
     * A missing or unreadable store is skipped with its pointer kept: the pointer holds the only copy of the key,
     * and `exists()` is also false on an access error. One that opens and has no row is an unfinished create or delete.
     */
    private fun openVisible(pointer: AccountPointer): AccountStore? {
        if (!environment.files.accountStoreFile(pointer.storageId).exists()) return null
        var store: AccountStore? = null
        val hasAccount = try {
            store = openStore(pointer)
            store.accountsDao().getIds().isNotEmpty()
        } catch (e: Exception) {
            store?.close()
            logger.w { "An account store did not open: ${e::class.simpleName}" }
            return null
        } catch (e: Error) {
            store?.close()
            throw e
        }
        if (hasAccount) return store
        store?.close()
        chain.deletePointers(listOf(pointer.accountId))
        deleteStoreFiles(pointer.storageId)
        return null
    }

    private fun openStore(pointer: AccountPointer): AccountStore {
        val file = environment.files.accountStoreFile(pointer.storageId)
        return AccountStore(
            environment.openDatabase<AccountDatabase>(file, pointer.storeKey, ::AccountDatabase_Impl),
            pointer.accountId,
            pointer.storageId,
            this,
        )
    }

    /** Never deletes on failure: the pointer may name data that is only out of reach for now. */
    private fun openNamedStore(pointer: AccountPointer): AccountStore {
        environment.files.accountStoreDirectory(pointer.storageId).mkdirs()
        val store = openStore(pointer)
        try {
            store.openNow()
        } catch (e: Throwable) {
            store.close()
            throw e
        }
        return store
    }

    /** The store first: a crash before the pointer leaves an encrypted file nobody has the key to. */
    private fun createStoreWithPointer(accountId: String): AccountStore {
        val storageId = ByteArray(STORAGE_ID_SIZE).also(random::nextBytes).toHexString()
        return AccountPointer(accountId, storageId, environment.randomKey()).wipingKey { pointer ->
            var store: AccountStore? = null
            try {
                val created = openNamedStore(pointer)
                store = created
                chain.root.dao.insertPointers(listOf(pointer))
                created
            } catch (e: Throwable) {
                store?.close()
                deleteStoreFiles(storageId)
                throw e
            }
        }
    }

    private fun deleteStoreFiles(storageId: String) {
        environment.files.accountStoreDirectory(storageId).deleteRecursively()
    }

    private inline fun <T> AccountPointer.wipingKey(block: (AccountPointer) -> T): T = try {
        block(this)
    } finally {
        storeKey.fill(0)
    }

    private fun List<List<AccountPointer>>.wipeKeys() = forEach { safe -> safe.forEach { it.storeKey.fill(0) } }

    private companion object {
        const val STORAGE_ID_SIZE = 16
    }
}
