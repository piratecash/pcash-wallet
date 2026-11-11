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
import cash.p.terminal.core.managers.KitPlatformContexts
import cash.p.terminal.core.managers.KitStorageNaming
import cash.p.terminal.core.managers.SolanaKitDatabaseKeyProvider
import cash.p.terminal.core.managers.StellarKitDatabaseKeyProvider
import cash.p.terminal.core.managers.TonKitDatabaseKeyProvider
import cash.p.terminal.core.managers.TronKitDatabaseKeyProvider
import java.io.File
import java.nio.file.Files
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private fun DesktopVault.requireKitKey(accountId: String, kitName: String): DatabaseKey =
    openSession()?.kitKey(accountId, kitName) ?: throw StorageLockedException()

/**
 * The key stays in the account's store and dies with it. [remove] keeps it, since it only follows a kit's clear,
 * which under opaque names leaves files it cannot list: those still belong to the account until its directory goes.
 */
open class DesktopKitDatabaseKeyProvider(
    private val vault: DesktopVault,
    private val kitName: String,
) : KitDatabaseKeyProvider {

    override suspend fun awaitDatabaseKey(accountId: String): DatabaseKey = vault.requireKitKey(accountId, kitName)

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

/** Dropped for real: erasing a wallet must leave any database file that survived unreadable, as on Android. */
class DesktopZcashDbKeyProvider(private val vault: DesktopVault) : ZcashDbKeyProvider {
    override fun keyFor(accountId: String): ZcashDbKey =
        vault.requireKitKey(accountId, KIT_NAME).let { ZcashDbKey(it.bytes, newlyGenerated = it.isNew) }

    override fun drop(accountId: String): Boolean {
        vault.openSession()?.account(accountId)?.removeKitKey(KIT_NAME)
        return true
    }

    private companion object {
        const val KIT_NAME = "zcash"
    }
}

/** A key in the account's store is the record of a storage: only the open accounts' storages are known here. */
class DesktopBeamKitDatabaseKeyProvider(
    private val vault: DesktopVault,
    private val storageLocator: BeamStorageLocator,
    private val deletionState: BeamDeletionState,
) : BeamKitDatabaseKeyProvider {
    // Blocking interface over a blocking store: a thread lock guards check-then-write, nothing inside suspends.
    private val lock = ReentrantLock()

    override fun ensureAvailable(accountId: String) = deletionState.ensureAvailable(accountId)

    // An existing database without a key restores: createNew must never run over it.
    override fun keyForInitialization(accountId: String, network: BeamNetwork): DatabaseKey = lock.withLock {
        ensureAvailable(accountId)
        val databaseExists = storageLocator.databaseFile(accountId, network).exists()
        val key = vault.requireKitKey(accountId, network.kitName)
        DatabaseKey(key.bytes, isNew = key.isNew && !databaseExists)
    }

    override fun hasAnyKey(): Boolean = storageIds().isNotEmpty()

    override fun storageIds(): Set<String> = lock.withLock {
        forEachStorage { accountId, network ->
            storageLocator.storageId(accountId, network).takeIf {
                vault.openSession()?.account(accountId)?.hasKitKey(network.kitName) == true
            }
        }.toSet()
    }

    override fun removeStorageKey(id: String) = lock.withLock {
        check(Files.notExists(storageLocator.validatedPath(id).toPath())) {
            "BEAM database must be erased before its key"
        }
        shredStorageKey(id)
    }

    override fun shredStorageKey(id: String) = lock.withLock {
        forEachStorage { accountId, network ->
            if (storageLocator.storageId(accountId, network) == id) {
                vault.openSession()?.account(accountId)?.removeKitKey(network.kitName)
            }
        }
        Unit
    }

    private inline fun <T : Any> forEachStorage(block: (String, BeamNetwork) -> T?): List<T> =
        vault.openSession()?.accountIds.orEmpty().flatMap { accountId ->
            BeamNetwork.entries.mapNotNull { block(accountId, it) }
        }

    private val BeamNetwork.kitName get() = "beam-$directoryName"
}

/** Storages exist only inside the directories of the accounts this session can open. */
class DesktopBeamStorageLocator(
    private val vault: DesktopVault,
    platformContexts: KitPlatformContexts,
    storageNaming: KitStorageNaming,
) : BeamStorageLocator(platformContexts, storageNaming) {

    override fun hasAnyData(): Boolean = storageIds().isNotEmpty()

    override fun storageIds(): Set<String> = storages().filterValues(File::exists).keys

    override fun validatedPath(id: String): File =
        requireNotNull(storages()[id]) { "Invalid BEAM storage scope" }.also(::validateContained)

    private fun storages(): Map<String, File> =
        vault.openSession()?.accountIds.orEmpty().flatMap { accountId ->
            BeamNetwork.entries.map { storageId(accountId, it) to storagePath(accountId, it) }
        }.toMap()
}
