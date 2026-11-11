package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.storage.ActiveAccountDao
import cash.p.terminal.core.storage.LevelDaos
import cash.p.terminal.core.storage.desktop.LevelDatabase
import cash.p.terminal.core.storage.desktop.LevelDatabase_Impl
import cash.p.terminal.core.storage.desktop.SafeLink
import cash.p.terminal.core.storage.desktop.VaultSafeDao
import cash.p.terminal.core.storage.desktop.VaultSafeMeta
import cash.p.terminal.entities.ActiveAccount
import cash.p.terminal.premium.data.database.PremiumDatabase
import cash.p.terminal.premium.data.database.PremiumDatabaseConstructor

/** The databases of one level. Owns [link] and wipes its key on [close]. */
internal class OpenSafe(
    val link: SafeLink,
    private val level: EncryptedDatabase<LevelDatabase>,
    private val premiumDatabase: EncryptedDatabase<PremiumDatabase>,
) : LevelDaos by level.room, AutoCloseable {
    private val activeAccounts = SafeActiveAccountDao(level.room.activeAccountDao())

    val dao: VaultSafeDao = level.room.vaultSafeDao()
    val premium: PremiumDatabase get() = premiumDatabase.room
    val meta: VaultSafeMeta get() = checkNotNull(dao.meta()) { "The safe has no meta" }

    override fun activeAccountDao(): ActiveAccountDao = activeAccounts

    override fun close() {
        premiumDatabase.close()
        level.close()
        link.key.fill(0)
    }
}

/** A safe has one active account; its level is relative, so it differs between a direct login and the chain. */
private class SafeActiveAccountDao(private val delegate: ActiveAccountDao) : ActiveAccountDao {
    override fun getActiveAccount(level: Int): ActiveAccount? = delegate.getActiveAccount(STORED_LEVEL)

    override fun insertActiveAccount(activeAccount: ActiveAccount) =
        delegate.insertActiveAccount(ActiveAccount(STORED_LEVEL, activeAccount.accountId))

    override fun deleteActiveAccount(level: Int) = delegate.deleteActiveAccount(STORED_LEVEL)

    private companion object {
        const val STORED_LEVEL = 0
    }
}

internal class SafeOpener(private val environment: StorageEnvironment) {

    /** Takes ownership of [link]. Throws when the key does not open the slot, as for any free slot. */
    fun open(link: SafeLink): OpenSafe = ownsLink(link) {
        val file = environment.files.safeFile(link.slot)
        // Room would quietly create an empty database in its place.
        check(file.exists()) { "The slot has no safe" }
        val level = environment.openDatabase<LevelDatabase>(file, link.key, ::LevelDatabase_Impl)
        closingOnFailure(level) {
            val meta = checkNotNull(level.room.vaultSafeDao().meta()) { "The safe has no meta" }
            OpenSafe(link, level, openPremium(link.slot, meta.premiumKey))
        }
    }

    /** Takes ownership of [link]. Whatever lay in the slot is gone. */
    fun create(link: SafeLink, slotPool: Set<Int>, envelopePool: Set<Int>, isHiddenRoot: Boolean): OpenSafe =
        ownsLink(link) {
            environment.slots.clearForNewSafe(link.slot)
            val file = environment.files.safeFile(link.slot)
            val level = environment.openDatabase<LevelDatabase>(file, link.key, ::LevelDatabase_Impl)
            closingOnFailure(level) {
                val premiumKey = environment.randomKey()
                val meta = VaultSafeMeta(
                    premiumKey = premiumKey,
                    slotPool = slotPool,
                    envelopePool = envelopePool,
                    isHiddenRoot = isHiddenRoot,
                    child = null,
                    collapse = null,
                )
                level.room.vaultSafeDao().putMeta(meta)
                OpenSafe(link, level, openPremium(link.slot, premiumKey))
            }
        }

    private fun openPremium(slot: Int, key: ByteArray): EncryptedDatabase<PremiumDatabase> {
        val premium = environment.openDatabase(
            environment.files.premiumFile(slot),
            key,
            PremiumDatabaseConstructor::initialize,
        )
        return closingOnFailure(premium) { premium.apply { openNow() } }
    }

    private inline fun ownsLink(link: SafeLink, block: () -> OpenSafe): OpenSafe = try {
        block()
    } catch (e: Throwable) {
        link.key.fill(0)
        throw e
    }

    private inline fun <T> closingOnFailure(database: AutoCloseable, block: () -> T): T = try {
        block()
    } catch (e: Throwable) {
        database.close()
        throw e
    }
}
