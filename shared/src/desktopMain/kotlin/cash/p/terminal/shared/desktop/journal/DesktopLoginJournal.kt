package cash.p.terminal.shared.desktop.journal

import cash.p.terminal.feature.logging.journal.JournalRecordKind
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.shared.desktop.vault.DesktopStorageSession
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.OpenSafe
import cash.p.terminal.shared.desktop.vault.VaultLoginEvents
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.ICurrentDateProvider
import io.horizontalsystems.core.entities.AutoDeletePeriod

/**
 * The desktop login journal. An entry is always announced to the root's notify key, logged or not, so a parent
 * learns of its duress level's entries without the duress level storing anything about it.
 */
class DesktopLoginJournal internal constructor(
    private val vault: DesktopVault,
    private val camera: SelfieCamera,
    private val premium: Lazy<CheckPremiumUseCase>,
    private val contacts: Lazy<ContactsRepository>,
    private val failedAttempts: FailedAttemptRecorder,
    private val records: DesktopLoginRecordRepository,
    private val dateProvider: ICurrentDateProvider,
    private val queue: JournalQueue,
) : VaultLoginEvents {
    private val logger = Logger.withTag("LoginJournal")
    private val imports by lazy { JournalImports(vault.mailbox, premium) }

    /**
     * Everything about the entry is taken now: by the time the queue gets to it the session may have switched or
     * narrowed, and the entry must neither be lost nor told as another level's.
     */
    override fun onEntered(session: DesktopStorageSession) {
        val entry = try {
            entryOf(session)
        } catch (e: Exception) {
            logger.w(e) { "Reading the entered level failed" }
            return
        }
        queue.submit("Recording an entry") { record(session, entry) }
    }

    override fun onFailedUnlock() = failedAttempts.record()

    /** Also without a session: a cache left from the last one would bring the contacts back on unlock. */
    override fun onEraseContacts() = contacts.value.clear(writeSynchronously = true)

    private fun entryOf(session: DesktopStorageSession): Entry {
        val root = session.chain.root
        val logged = root.dao.flag(JournalSetting.LOG_SUCCESSFUL)
        return Entry(
            root = root,
            rootLevel = session.rootLevel,
            timestamp = dateProvider.currentDate.time,
            generation = vault.mailbox.wipeGeneration,
            notifyKey = root.dao.bytes(JournalSetting.NOTIFY_KEY),
            logged = logged,
            withPhoto = logged && root.dao.flag(JournalSetting.SELFIE_ON_SUCCESSFUL),
            accountId = root.activeAccountDao().getActiveAccount(0)?.accountId.orEmpty(),
        )
    }

    /** The announcement goes out whatever happened since; the rest only while the entered level is still the root. */
    private suspend fun record(session: DesktopStorageSession, entry: Entry) {
        // One photo serves both the announcement and the visible record.
        val photo = if (entry.withPhoto) camera.captureFor(entry.root.dao) else null
        entry.notifyKey?.let {
            imports.step("Announcing the entry") {
                vault.mailbox.appendNotification(it, entry.timestamp, photo, entry.generation)
            }
        }
        if (!isCurrent(session, entry)) return
        imports.run(session)
        if (entry.logged && imports.isPremium(entry.rootLevel)) {
            entry.root.journal?.insert(entry.timestamp, JournalRecordKind.SUCCESS, entry.accountId, photo)
        }
        deleteExpired(session)
    }

    /** Every open level by its own period: imports reach the journals below the root too. */
    private suspend fun deleteExpired(session: DesktopStorageSession) =
        session.chain.safes.forEachIndexed { index, safe ->
            imports.step("Auto-delete") {
                val period = AutoDeletePeriod.fromValue(safe.dao.autoDeletePeriod())
                records.deleteExpired(session.rootLevel + index, period)
            }
        }

    private fun isCurrent(session: DesktopStorageSession, entry: Entry) =
        vault.openSession() === session && session.chain.root === entry.root

    private class Entry(
        val root: OpenSafe,
        val rootLevel: Int,
        val timestamp: Long,
        val generation: Long,
        val notifyKey: ByteArray?,
        val logged: Boolean,
        val withPhoto: Boolean,
        val accountId: String,
    )
}
