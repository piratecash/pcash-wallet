package cash.p.terminal.shared.desktop.journal

import cash.p.terminal.core.storage.desktop.VaultSafeDao
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.shared.desktop.vault.DesktopVault
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DesktopSettingsStore
import io.horizontalsystems.core.ILoggingSettings
import io.horizontalsystems.core.entities.AutoDeletePeriod
import kotlinx.coroutines.runBlocking

/**
 * The journal settings of a level, kept in its safe. The duress switches also write the duress level's own
 * "log successful" and selfie flags, now or when the level is created.
 */
class DesktopLoggingSettings(
    private val vault: DesktopVault,
    private val settingsStore: DesktopSettingsStore,
) : ILoggingSettings {
    private val logger = Logger.withTag("DesktopLoggingSettings")

    override fun hasEnabledAtLeastOneSettingsEnabled(level: Int): Boolean =
        listOf(JournalSetting.LOG_SUCCESSFUL, JournalSetting.LOG_UNSUCCESSFUL, JournalSetting.LOG_DURESS)
            .any { flag(level, it) }

    override fun getLogSuccessfulLoginsEnabled(level: Int) = flag(level, JournalSetting.LOG_SUCCESSFUL)

    override fun setLogSuccessfulLoginsEnabled(level: Int, enabled: Boolean) =
        write(level) { putFlag(JournalSetting.LOG_SUCCESSFUL, enabled) }

    override fun getSelfieOnSuccessfulLoginEnabled(level: Int) = flag(level, JournalSetting.SELFIE_ON_SUCCESSFUL)

    override fun setSelfieOnSuccessfulLoginEnabled(level: Int, enabled: Boolean) =
        write(level) { putFlag(JournalSetting.SELFIE_ON_SUCCESSFUL, enabled) }

    override fun getLogUnsuccessfulLoginsEnabled(level: Int) = flag(level, JournalSetting.LOG_UNSUCCESSFUL)

    /** Attempts made while it was off are not imported later. */
    override fun setLogUnsuccessfulLoginsEnabled(level: Int, enabled: Boolean) = write(level) {
        if (enabled && !startAfterLastEntry(this, MailboxRing.FAILED, JournalSetting.INBOX_WATERMARK)) return@write
        putFlag(JournalSetting.LOG_UNSUCCESSFUL, enabled)
    }

    override fun getSelfieOnUnsuccessfulLoginEnabled(level: Int) = FailedAttemptRecorder.isSelfieEnabled(settingsStore)

    override fun setSelfieOnUnsuccessfulLoginEnabled(level: Int, enabled: Boolean) =
        FailedAttemptRecorder.setSelfieEnabled(settingsStore, enabled)

    override fun getLogIntoDuressModeEnabled(level: Int) = flag(level, JournalSetting.LOG_DURESS)

    override fun setLogIntoDuressModeEnabled(level: Int, enabled: Boolean) = writeDuress(level) {
        val started = !enabled || startAfterLastEntry(this, MailboxRing.NOTIFY, JournalSetting.NOTIFY_WATERMARK)
        if (!started) return@writeDuress
        putFlag(JournalSetting.LOG_DURESS, enabled)
    }

    override fun getSelfieOnDuressLoginEnabled(level: Int) = flag(level, JournalSetting.SELFIE_ON_DURESS)

    override fun setSelfieOnDuressLoginEnabled(level: Int, enabled: Boolean) =
        writeDuress(level) { putFlag(JournalSetting.SELFIE_ON_DURESS, enabled) }

    override fun getDeleteAllAuthDataOnDuressEnabled(level: Int) = flag(level, JournalSetting.WIPE_ON_DURESS)

    override fun setDeleteAllAuthDataOnDuressEnabled(level: Int, enabled: Boolean) =
        write(level) { putFlag(JournalSetting.WIPE_ON_DURESS, enabled) }

    override fun getAutoDeleteLogsPeriod(level: Int): Int =
        safe(level)?.autoDeletePeriod() ?: AutoDeletePeriod.YEAR.value

    override fun setAutoDeleteLogsPeriod(level: Int, period: Int) =
        write(level) { putLong(JournalSetting.AUTO_DELETE_PERIOD, period.toLong()) }

    private fun safe(level: Int): VaultSafeDao? = vault.openSession()?.safeOrNull(level)

    private fun flag(level: Int, key: String): Boolean = safe(level)?.flag(key) ?: false

    private fun write(level: Int, change: VaultSafeDao.() -> Unit) {
        val safe = safe(level) ?: return logger.w { "Journal setting dropped: its level is not open" }
        safe.change()
    }

    /** A duress level of this session follows the switch at once; one created later follows it at creation. */
    private fun writeDuress(level: Int, change: VaultSafeDao.() -> Unit) = write(level) {
        change()
        safe(level + 1)?.let(::applyDuressFlagsTo)
    }

    /** False when the mailbox cannot be read: the switch stays off rather than import the past. */
    private fun startAfterLastEntry(safe: VaultSafeDao, ring: MailboxRing, watermark: String): Boolean {
        val lastSeq = tryOrNull { runBlocking { vault.mailbox.nextSeq(ring) } - 1 }
            ?: return false.also { logger.w { "Journal setting dropped: the mailbox is unreadable" } }
        safe.putLong(watermark, lastSeq)
        return true
    }
}
