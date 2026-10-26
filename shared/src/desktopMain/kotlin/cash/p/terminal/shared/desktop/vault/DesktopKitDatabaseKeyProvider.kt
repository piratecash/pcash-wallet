package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.StorageLockedException
import cash.p.terminal.core.adapters.zcash.session.ZcashDbKey
import cash.p.terminal.core.adapters.zcash.session.ZcashDbKeyProvider
import cash.p.terminal.core.managers.BeamDeletionState
import cash.p.terminal.core.managers.BeamKitDatabaseKeyProvider
import cash.p.terminal.core.managers.BeamNetwork
import cash.p.terminal.core.managers.BeamStorageLocator
import cash.p.terminal.core.managers.BitcoinKitDatabaseKeyProvider
import cash.p.terminal.core.managers.DatabaseKey
import cash.p.terminal.core.managers.EvmKitDatabaseKeyProvider
import cash.p.terminal.core.managers.KitDatabaseKeyProvider
import cash.p.terminal.core.managers.SolanaKitDatabaseKeyProvider
import cash.p.terminal.core.managers.StellarKitDatabaseKeyProvider
import cash.p.terminal.core.managers.TonKitDatabaseKeyProvider
import cash.p.terminal.core.managers.TronKitDatabaseKeyProvider
import io.horizontalsystems.core.DesktopSettingsStore
import java.nio.file.Files
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Keys are derived from the open vault session, so none is stored: [remove] has nothing to erase, and
 * [kitName] must never change, or the kit's existing databases no longer open.
 */
open class DesktopKitDatabaseKeyProvider(
    private val vault: DesktopVault,
    private val kitName: String,
) : KitDatabaseKeyProvider {

    override suspend fun awaitDatabaseKey(accountId: String): DatabaseKey {
        val key = vault.openSession()?.kitDatabaseKey(kitName, accountId) ?: throw StorageLockedException()
        return DatabaseKey(key, isNew = false)
    }

    override fun remove(accountId: String) = Unit
}

class DesktopBitcoinKitDatabaseKeyProvider(vault: DesktopVault) :
    DesktopKitDatabaseKeyProvider(vault, "bitcoin"), BitcoinKitDatabaseKeyProvider

class DesktopTonKitDatabaseKeyProvider(vault: DesktopVault) :
    DesktopKitDatabaseKeyProvider(vault, "ton"), TonKitDatabaseKeyProvider

class DesktopTronKitDatabaseKeyProvider(vault: DesktopVault) :
    DesktopKitDatabaseKeyProvider(vault, "tron"), TronKitDatabaseKeyProvider

class DesktopEvmKitDatabaseKeyProvider(vault: DesktopVault) :
    DesktopKitDatabaseKeyProvider(vault, "evm"), EvmKitDatabaseKeyProvider

class DesktopSolanaKitDatabaseKeyProvider(vault: DesktopVault) :
    DesktopKitDatabaseKeyProvider(vault, "solana"), SolanaKitDatabaseKeyProvider

class DesktopStellarKitDatabaseKeyProvider(vault: DesktopVault) :
    DesktopKitDatabaseKeyProvider(vault, "stellar"), StellarKitDatabaseKeyProvider

/** Derived like the other kit keys: the key always reopens the database, and dropping it leaves nothing behind. */
class DesktopZcashDbKeyProvider(private val vault: DesktopVault) : ZcashDbKeyProvider {
    override fun keyFor(accountId: String): ZcashDbKey {
        val key = vault.openSession()?.kitDatabaseKey("zcash", accountId) ?: throw StorageLockedException()
        return ZcashDbKey(key, newlyGenerated = false)
    }

    override fun drop(accountId: String) = true
}

/**
 * Derived like the other kit keys ("beam-<network>" must never change); only the fact that a key was
 * issued for a storage is stored, so a wallet created here opens fresh and everything else restores.
 */
class DesktopBeamKitDatabaseKeyProvider(
    private val vault: DesktopVault,
    private val settingsStore: DesktopSettingsStore,
    private val storageLocator: BeamStorageLocator,
    private val deletionState: BeamDeletionState,
) : BeamKitDatabaseKeyProvider {
    // Blocking interface over a blocking store: a thread lock guards check-then-write, nothing inside suspends.
    private val lock = ReentrantLock()

    override fun ensureAvailable(accountId: String) = deletionState.ensureAvailable(accountId)

    override fun keyForInitialization(accountId: String, network: BeamNetwork): DatabaseKey = lock.withLock {
        ensureAvailable(accountId)
        val key = vault.openSession()?.kitDatabaseKey("beam-${network.directoryName}", accountId)
            ?: throw StorageLockedException()
        try {
            DatabaseKey(key, isNew = issue(accountId, network))
        } catch (e: Throwable) {
            key.fill(0)
            throw e
        }
    }

    override fun hasAnyKey(): Boolean = storageIds().isNotEmpty()

    override fun storageIds(): Set<String> = lock.withLock {
        settingsStore.string(ISSUED_STORAGE_IDS)?.split(SEPARATOR)?.filter { it.isNotEmpty() }?.toSet().orEmpty()
    }

    override fun removeStorageKey(id: String) = lock.withLock {
        check(Files.notExists(storageLocator.validatedPath(id).toPath())) {
            "BEAM database must be erased before its key"
        }
        shredStorageKey(id)
    }

    // Drops the record only: the derived key itself is destroyed only by wiping the vault.
    override fun shredStorageKey(id: String) = lock.withLock { writeStorageIds(storageIds() - id) }

    // An existing database without a record restores: createNew must never run over it.
    private fun issue(accountId: String, network: BeamNetwork): Boolean {
        val id = storageLocator.storageId(accountId, network)
        val issued = storageIds()
        if (id in issued) return false
        val databaseExists = storageLocator.databaseFile(accountId, network).exists()
        writeStorageIds(issued + id)
        return !databaseExists
    }

    private fun writeStorageIds(ids: Set<String>) =
        settingsStore.setString(ISSUED_STORAGE_IDS, ids.joinToString(SEPARATOR).ifEmpty { null })

    private companion object {
        const val ISSUED_STORAGE_IDS = "beamIssuedStorageIds"
        const val SEPARATOR = ","
    }
}
