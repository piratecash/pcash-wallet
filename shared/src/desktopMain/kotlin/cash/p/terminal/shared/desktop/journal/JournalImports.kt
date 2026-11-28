package cash.p.terminal.shared.desktop.journal

import cash.p.terminal.feature.logging.journal.JournalRecordKind
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.shared.desktop.vault.DesktopStorageSession
import cash.p.terminal.shared.desktop.vault.OpenSafe
import cash.p.terminal.vault.JournalInboxCrypto
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException

/**
 * What the mailbox holds for the open safes: failed attempts for each by its own settings, and the entries of a
 * duress level for its parent. Nothing is written into a safe below the one importing, except the parent's
 * deletion of the visible records, which that level's own user can do as well.
 */
internal class JournalImports(
    private val mailbox: JournalMailbox,
    private val premium: Lazy<CheckPremiumUseCase>,
) {
    private val logger = Logger.withTag("LoginJournal")

    /** A pending wipe goes first, and no failing step keeps the others from running. */
    suspend fun run(session: DesktopStorageSession) {
        val safes = session.chain.safes
        safes.filter { it.dao.flag(JournalSetting.PENDING_WIPE) }.forEach { step("Wipe") { wipe(session, it) } }
        safes.forEachIndexed { index, safe ->
            step("Failed attempts import") { importFailedAttempts(safe, session.rootLevel + index) }
        }
        safes.forEachIndexed { index, safe ->
            step("Duress entries import") { importChildEntries(session, safe, session.rootLevel + index) }
        }
    }

    suspend fun isPremium(level: Int): Boolean = try {
        premium.value.isPremiumWithParentInCache(level)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.w(e) { "Premium check failed" }
        false
    }

    /** Throws instead of answering "no": the watermark must not pass entries that were never decided on. */
    private suspend fun premiumForImport(level: Int): Boolean = premium.value.isPremiumWithParentInCache(level)

    private suspend fun importFailedAttempts(safe: OpenSafe, level: Int) {
        if (!safe.dao.flag(JournalSetting.LOG_UNSUCCESSFUL)) return
        val journal = safe.journal ?: return
        val privateKey = safe.dao.bytes(JournalSetting.INBOX_KEY) ?: return
        val entries = newEntries(MailboxRing.FAILED, safe.dao.long(JournalSetting.INBOX_WATERMARK)) {
            JournalInboxCrypto.openSlot(it, safe.link.slot, privateKey)
        }
        privateKey.fill(0)
        if (entries.isEmpty()) return
        val premium = premiumForImport(level)
        importEach(safe, entries, JournalSetting.INBOX_WATERMARK) {
            if (premium) journal.insert(it.timestamp, JournalRecordKind.FAILED, "", it.photo, it.seq)
        }
    }

    /** Needs only the parent: an entry of a level that did not open still counts. */
    private suspend fun importChildEntries(session: DesktopStorageSession, parent: OpenSafe, level: Int) {
        val privateKey = parent.dao.bytes(JournalSetting.CHILD_NOTIFY_PRIVATE) ?: return
        val entries = newEntries(MailboxRing.NOTIFY, parent.dao.long(JournalSetting.NOTIFY_WATERMARK)) {
            JournalInboxCrypto.openSingle(it, privateKey)
        }
        privateKey.fill(0)
        if (entries.isEmpty()) return
        val lastSeq = entries.last().seq
        if (parent.dao.flag(JournalSetting.WIPE_ON_DURESS)) {
            // Marked first: a wipe cut short resumes at the next unlock.
            parent.dao.putFlag(JournalSetting.PENDING_WIPE, true)
            parent.dao.putLong(JournalSetting.NOTIFY_WATERMARK, lastSeq)
            wipe(session, parent)
            return
        }
        val logged = parent.dao.flag(JournalSetting.LOG_DURESS) && premiumForImport(level)
        val withPhoto = parent.dao.flag(JournalSetting.SELFIE_ON_DURESS)
        importEach(parent, entries, JournalSetting.NOTIFY_WATERMARK) {
            if (logged) {
                // A journal that did not open keeps the entries for the next unlock.
                checkNotNull(parent.journal) { "The journal is not open" }
                    .insert(it.timestamp, JournalRecordKind.DURESS_ENTRY, "", it.photo.takeIf { withPhoto }, it.seq)
            }
        }
    }

    /** One entry at a time, so a failure part way costs no rereading; the record's sourceSeq keeps a retry single. */
    private suspend fun importEach(
        safe: OpenSafe,
        entries: List<MailboxEntry>,
        watermark: String,
        import: suspend (MailboxEntry) -> Unit,
    ) = entries.forEach {
        import(it)
        safe.dao.putLong(watermark, it.seq)
    }

    private suspend fun newEntries(
        ring: MailboxRing,
        watermark: Long?,
        open: (ByteArray) -> ByteArray?,
    ): List<MailboxEntry> {
        val nextSeq = mailbox.nextSeq(ring)
        // Past the counter means an earlier mailbox; equal is an append cut off before the counter moved.
        val after = watermark?.takeIf { it <= nextSeq } ?: 0
        return mailbox.read(ring, open).filter { it.seq > after }
    }

    /**
     * Android's "delete all auth data": the visible records of [parent] and of every open level below it, and every
     * mailbox entry. Each step repeats safely; the mark stays until a complete chain has seen all of them succeed.
     */
    private suspend fun wipe(session: DesktopStorageSession, parent: OpenSafe) {
        val levels = listOf(parent) + session.chain.below(parent)
        val journalsCleared = levels.map { safe ->
            step("Journal wipe") { checkNotNull(safe.journal).deleteAll() }
        }.all { it }
        val mailboxCleared = step("Mailbox wipe") { mailbox.overwriteEntries() }
        if (journalsCleared && mailboxCleared && session.chain.isComplete) {
            parent.dao.deleteSetting(JournalSetting.PENDING_WIPE)
        }
    }

    /** Logs a failure instead of passing it on, so one step cannot cost the ones after it. */
    suspend fun step(name: String, block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.w(e) { "$name failed" }
        false
    }
}
