package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.managers.DatabaseKey
import cash.p.terminal.core.storage.AccountDaos
import cash.p.terminal.core.storage.LevelDaos
import cash.p.terminal.core.storage.SharedDaos
import cash.p.terminal.core.storage.desktop.SafeLink
import cash.p.terminal.core.storage.desktop.SharedDatabase
import cash.p.terminal.core.storage.desktop.SharedDatabase_Impl
import cash.p.terminal.core.storage.desktop.VaultSafeDao
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.premium.data.database.PremiumDatabase
import cash.p.terminal.vault.CreateResult
import cash.p.terminal.vault.EnvelopePayload
import cash.p.terminal.vault.Hkdf
import cash.p.terminal.vault.VaultSession
import kotlinx.coroutines.flow.Flow

/**
 * The storage a password opens: its safe as the root, the safes below it, the shared safe and the stores
 * of the accounts they point to. [close] takes all of it down and wipes every key, the vault session included.
 */
class DesktopStorageSession private constructor(
    private val environment: StorageEnvironment,
    private val parts: Parts,
    private val keys: SessionKeys,
) {
    @Volatile
    private var closed = false

    // Changed under the vault lock only.
    private val retired = mutableListOf<RetiredLevels>()

    internal val rootLevel: Int get() = parts.chain.rootLevel

    /** Names the wallet this session opened: levels count from zero in every root. */
    internal val rootSlot: Int get() = parts.chain.root.link.slot
    internal val chain: SafeChain get() = parts.chain
    internal val levels: LevelProvisioner get() = parts.levels
    internal val shared: SharedDaos get() = parts.shared.room
    internal val accounts: List<AccountDaos> get() = parts.stores.all
    internal val accountStores: Flow<List<AccountDaos>> get() = parts.stores.changes

    internal fun level(level: Int): LevelDaos = parts.chain.safe(level)

    internal fun premium(level: Int): PremiumDatabase = parts.chain.safe(level).premium

    internal fun account(accountId: String): AccountStore? = parts.stores.store(accountId)

    internal val accountIds: Set<String> get() = parts.stores.accountIds

    /** A kit key is 256 random bits, generated once and kept in the store of the account. */
    internal fun kitKey(accountId: String, kitName: String): DatabaseKey? =
        parts.stores.store(accountId)?.kitKey(kitName, environment::randomKey)

    internal val rootSafe: VaultSafeDao get() = parts.chain.root.dao

    /** Null for a level this session does not reach, such as the default before the first unlock event. */
    internal fun safeOrNull(level: Int): VaultSafeDao? = parts.chain.safeOrNull(level)?.dao

    internal fun levelOf(accountId: String): Int? = parts.stores.levelOf(accountId)

    internal fun prepareAccountStore(accountId: String) = parts.stores.prepare(accountId)

    /** The pointers go in first: a crash leaves a duplicate that resolves at the next login, never a lost account. */
    internal fun setLevel(accountIds: List<String>, level: Int) {
        val target = parts.chain.safe(level)
        val safes = parts.chain.safes
        val moving = safes.flatMap { it.dao.pointers() }
            .filter { it.accountId in accountIds }
            .distinctBy { it.accountId }
        target.dao.insertPointers(moving)
        moving.forEach { it.storeKey.fill(0) }
        checkpoint(StorageCheckpoint.SET_LEVEL_POINTERS_INSERTED)
        (safes - target).forEach {
            it.dao.deletePointers(accountIds)
            checkpoint(StorageCheckpoint.SET_LEVEL_POINTERS_DELETED_FROM_SAFE)
        }
        parts.stores.refreshLevels()
    }

    internal fun collapseLevelsAbove(level: Int) {
        val safe = parts.chain.safe(level)
        val meta = safe.meta
        if (meta.child == null) return
        try {
            if (meta.collapse == null) {
                val lower = parts.chain.below(safe)
                check(parts.chain.isComplete && lower.none { it.meta.collapse != null }) {
                    "A level below did not open"
                }
                parts.collapse.begin(safe, lower)
                parts.chain.closeBelow(safe)
                parts.collapse.finish(safe)
            } else {
                parts.chain.closeBelow(safe)
                parts.collapse.recover(safe)
            }
        } finally {
            parts.stores.refreshLevels()
        }
    }

    /** Hex of a key every level shares; null once closed. */
    internal fun sharedNamesSecret(): String? = if (closed) null else keys.sharedNames.toHexString()

    internal fun secretFieldsKey(): ByteArray? = if (closed) null else keys.secretFields

    /** The level in this chain that [candidate] opens; null when it belongs to another root or a level above. */
    internal fun levelOpenedBy(candidate: VaultSession): Int? =
        candidate.payload()?.use { parts.chain.levelOpenedBy(it.slotKey) }

    /**
     * Everything above [level] leaves the session's view now, as if [proof]'s password had opened this session.
     * Takes ownership of [proof]; the old vault session and its upper-level key are wiped here. The stores and
     * safes above stay open only until the caller closes what this returns: adapters being stopped still use them.
     */
    internal fun narrowTo(level: Int, proof: VaultSession): AutoCloseable {
        // Owned before anything can fail: a fallback to a cold switch closes them with the session.
        val levels = RetiredLevels(parts.chain.narrowTo(level)).also(retired::add)
        checkpoint(StorageCheckpoint.NARROWING_CHAIN_SPLIT)
        levels.accountIds = parts.stores.retireUnreachable()
        parts.levels.replaceVaultSession(proof)
        return levels
    }

    fun close() {
        if (closed) return
        closed = true
        retired.toList().forEach(RetiredLevels::close)
        parts.close()
        keys.wipe()
        parts.levels.closeVaultSession()
        tryOrNull { environment.slots.alignFileAttributes() }
    }

    private fun checkpoint(checkpoint: StorageCheckpoint) = environment.checkpoints.reached(checkpoint)

    private inner class RetiredLevels(val safes: List<OpenSafe>) : AutoCloseable {
        var accountIds: List<String> = emptyList()

        override fun close() {
            if (!retired.remove(this)) return
            try {
                parts.stores.closeRetired(accountIds)
            } finally {
                safes.forEach(OpenSafe::close)
                tryOrNull { environment.slots.alignFileAttributes() }
            }
        }
    }

    private class Parts(
        val shared: EncryptedDatabase<SharedDatabase>,
        val chain: SafeChain,
        val collapse: CollapseOperation,
        val stores: AccountStores,
        val levels: LevelProvisioner,
    ) {
        fun close() {
            stores.close()
            chain.close()
            shared.close()
        }
    }

    private class SessionKeys(val secretFields: ByteArray, val sharedNames: ByteArray) {
        fun wipe() {
            secretFields.fill(0)
            sharedNames.fill(0)
        }
    }

    companion object {
        private const val KEY_SIZE = 32

        /** Throws when the storage does not open; whatever it had opened is closed, the vault session too. */
        internal fun open(vaultSession: VaultSession, environment: StorageEnvironment): DesktopStorageSession =
            assemble(vaultSession, environment) { opener, link -> opener.open(link) }

        /** A new vault: every slot but the root's starts out as noise. */
        internal fun create(created: CreateResult.Created, environment: StorageEnvironment): DesktopStorageSession {
            val session = created.session
            val rootSlot = checkNotNull(session.payload()) { "The vault session is closed" }.use { it.slot }
            environment.slots.prepareAll(occupiedSlot = rootSlot)
            KitDecoys(environment.files, environment.slots).create()
            return assemble(session, environment) { opener, link ->
                opener.create(link, created.slotPool, created.envelopePool, isHiddenRoot = false)
            }
        }

        private inline fun assemble(
            vaultSession: VaultSession,
            environment: StorageEnvironment,
            openRoot: (SafeOpener, SafeLink) -> OpenSafe,
        ): DesktopStorageSession {
            val payload = checkNotNull(vaultSession.payload()) { "The vault session is closed" }
            val opened = ArrayDeque<AutoCloseable>()
            try {
                val opener = SafeOpener(environment)
                val shared = openShared(environment, payload).also(opened::addFirst)
                val collapse = CollapseOperation(environment, opener)
                val chain = SafeChain(opener, collapse).also(opened::addFirst)
                val rootLink = SafeLink(payload.slot, payload.slotKey.copyOf(), vaultSession.envelopeIndex)
                chain.open(openRoot(opener, rootLink))
                val stores = AccountStores(environment, chain).also(opened::addFirst)
                stores.openAll()
                val levels = LevelProvisioner(environment, opener, chain, vaultSession)
                val keys = SessionKeys(
                    secretFields = checkNotNull(vaultSession.secretFieldsKey()),
                    sharedNames = checkNotNull(vaultSession.sharedNamesKey()),
                )
                tryOrNull { environment.slots.alignFileAttributes() }
                return DesktopStorageSession(
                    environment,
                    Parts(shared, chain, collapse, stores, levels),
                    keys,
                )
            } catch (e: Throwable) {
                // An Error from the SQLCipher native load must not leak an open database either.
                opened.forEach { tryOrNull { it.close() } }
                vaultSession.close()
                throw e
            } finally {
                payload.close()
            }
        }

        private fun openShared(
            environment: StorageEnvironment,
            payload: EnvelopePayload,
        ): EncryptedDatabase<SharedDatabase> {
            val key = Hkdf.expand(payload.sharedKey, "shared-db".toByteArray(Charsets.UTF_8), KEY_SIZE)
            try {
                val file = environment.files.sharedDatabaseFile
                val shared = environment.openDatabase<SharedDatabase>(file, key, ::SharedDatabase_Impl)
                try {
                    shared.openNow()
                } catch (e: Throwable) {
                    shared.close()
                    throw e
                }
                return shared
            } finally {
                key.fill(0)
            }
        }
    }
}
