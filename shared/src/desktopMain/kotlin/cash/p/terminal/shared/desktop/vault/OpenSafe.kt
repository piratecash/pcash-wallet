package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.storage.ActiveAccountDao
import cash.p.terminal.core.storage.LevelDaos
import cash.p.terminal.core.storage.desktop.LevelDatabase
import cash.p.terminal.core.storage.desktop.LevelDatabase_Impl
import cash.p.terminal.core.storage.desktop.SafeLink
import cash.p.terminal.core.storage.desktop.VaultSafeDao
import cash.p.terminal.core.storage.desktop.VaultSafeMeta
import cash.p.terminal.entities.ActiveAccount
import cash.p.terminal.feature.logging.journal.JournalDatabase
import cash.p.terminal.feature.logging.journal.JournalDatabase_Impl
import cash.p.terminal.feature.logging.journal.JournalStore
import cash.p.terminal.premium.data.database.PremiumDatabase
import cash.p.terminal.premium.data.database.PremiumDatabaseConstructor
import cash.p.terminal.shared.desktop.journal.JournalKeys
import cash.p.terminal.shared.desktop.journal.JournalSetting
import cash.p.terminal.shared.desktop.journal.bytes
import cash.p.terminal.shared.desktop.journal.putBytes
import co.touchlab.kermit.Logger

/** The databases of one level. Owns [link] and wipes its key on [close]. */
internal class OpenSafe(
    val link: SafeLink,
    private val level: EncryptedDatabase<LevelDatabase>,
    private val premiumDatabase: EncryptedDatabase<PremiumDatabase>,
    private val journalDatabase: EncryptedDatabase<JournalDatabase>? = null,
) : LevelDaos by level.room, AutoCloseable {
    private val activeAccounts = SafeActiveAccountDao(level.room.activeAccountDao())

    val dao: VaultSafeDao = level.room.vaultSafeDao()
    val premium: PremiumDatabase get() = premiumDatabase.room
    val meta: VaultSafeMeta get() = checkNotNull(dao.meta()) { "The safe has no meta" }

    /** Null outside a session, and when the journal did not open: logging stops, the level does not. */
    val journal: JournalStore? = journalDatabase?.let { JournalStore(it.room) }

    override fun activeAccountDao(): ActiveAccountDao = activeAccounts

    override fun close() {
        journalDatabase?.close()
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
    private val logger = Logger.withTag("SafeOpener")

    /**
     * Takes ownership of [link]. Throws when the key does not open the slot, as for any free slot.
     * Only a safe of the session gets its journal, and the journal keys it is missing.
     */
    fun open(link: SafeLink, withJournal: Boolean = false): OpenSafe = ownsLink(link) {
        val file = environment.files.safeFile(link.slot)
        // Room would quietly create an empty database in its place.
        check(file.exists()) { "The slot has no safe" }
        val level = environment.openDatabase<LevelDatabase>(file, link.key, ::LevelDatabase_Impl)
        closingOnFailure(level) {
            val dao = level.room.vaultSafeDao()
            val meta = checkNotNull(dao.meta()) { "The safe has no meta" }
            val premium = openPremium(link.slot, meta.premiumKey)
            closingOnFailure(premium) {
                OpenSafe(link, level, premium, if (withJournal) openJournal(link.slot, dao) else null)
            }
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
                val dao = level.room.vaultSafeDao()
                dao.putMeta(meta)
                JournalKeys.putRootNotifyKey(dao)
                val premium = openPremium(link.slot, premiumKey)
                closingOnFailure(premium) { OpenSafe(link, level, premium, openJournal(link.slot, dao)) }
            }
        }

    /** Without a key the slot's journal is noise or left over: a new one replaces it before the key is kept. */
    private fun openJournal(slot: Int, safe: VaultSafeDao): EncryptedDatabase<JournalDatabase>? = try {
        JournalKeys.ensureInboxKey(safe, slot, environment.mailbox)
        safe.bytes(JournalSetting.JOURNAL_KEY)?.let { key -> openJournalFile(slot, key).also { key.fill(0) } }
            ?: createJournal(slot, safe)
    } catch (e: Exception) {
        logger.w(e) { "The login journal did not open" }
        null
    }

    private fun createJournal(slot: Int, safe: VaultSafeDao): EncryptedDatabase<JournalDatabase> {
        environment.slots.clearJournal(slot)
        val key = environment.randomKey()
        try {
            val journal = openJournalFile(slot, key)
            closingOnFailure(journal) { safe.putBytes(JournalSetting.JOURNAL_KEY, key) }
            return journal
        } finally {
            key.fill(0)
        }
    }

    private fun openJournalFile(slot: Int, key: ByteArray): EncryptedDatabase<JournalDatabase> {
        val file = environment.files.journalFile(slot)
        val journal = environment.openDatabase<JournalDatabase>(file, key, ::JournalDatabase_Impl)
        return closingOnFailure(journal) { journal.apply { openNow() } }
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
