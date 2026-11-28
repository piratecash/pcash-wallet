package cash.p.terminal.shared.desktop.journal

import cash.p.terminal.core.storage.desktop.LevelSetting
import cash.p.terminal.core.storage.desktop.VaultSafeDao
import io.horizontalsystems.core.entities.AutoDeletePeriod

/** The journal's settings in a level's safe; none of them names or opens anything above that level. */
internal object JournalSetting {
    const val JOURNAL_KEY = "journal.key"
    const val INBOX_KEY = "inbox.key"
    const val INBOX_WATERMARK = "inbox.watermark"
    const val NOTIFY_KEY = "entry.notifyKey"
    const val NOTIFY_WATERMARK = "entry.watermark"
    const val CHILD_NOTIFY_PRIVATE = "child.notifyPrivate"
    const val PENDING_WIPE = "journal.pendingWipe"
    const val PASSWORD = "journal.password"
    const val SELFIE_PROBLEM = "selfie.problem"
    const val ERASE_TAG = "contacts.eraseTag"

    const val LOG_SUCCESSFUL = "log.successful"
    const val SELFIE_ON_SUCCESSFUL = "log.successfulSelfie"
    const val LOG_UNSUCCESSFUL = "log.unsuccessful"
    const val LOG_DURESS = "log.duress"
    const val SELFIE_ON_DURESS = "log.duressSelfie"
    const val WIPE_ON_DURESS = "log.duressWipe"
    const val AUTO_DELETE_PERIOD = "log.autoDeletePeriod"
}

internal fun VaultSafeDao.bytes(key: String): ByteArray? = setting(key)?.hexToByteArray()

internal fun VaultSafeDao.putBytes(key: String, value: ByteArray) = putSetting(LevelSetting(key, value.toHexString()))

internal fun VaultSafeDao.flag(key: String): Boolean = setting(key)?.toBooleanStrictOrNull() ?: false

/** "Off" is stored as nothing: a switch never turned on leaves the same safe as one turned off again. */
internal fun VaultSafeDao.putFlag(key: String, value: Boolean) =
    if (value) putSetting(LevelSetting(key, value.toString())) else deleteSetting(key)

internal fun VaultSafeDao.long(key: String): Long? = setting(key)?.toLongOrNull()

internal fun VaultSafeDao.putLong(key: String, value: Long) = putSetting(LevelSetting(key, value.toString()))

/** Android's default: a year. */
internal fun VaultSafeDao.autoDeletePeriod(): Int =
    long(JournalSetting.AUTO_DELETE_PERIOD)?.toInt() ?: AutoDeletePeriod.YEAR.value

/** The parent's duress switches become the child's own; as on Android, the selfie only goes with duress logging. */
internal fun VaultSafeDao.applyDuressFlagsTo(child: VaultSafeDao) {
    val logDuress = flag(JournalSetting.LOG_DURESS)
    child.putFlag(JournalSetting.LOG_SUCCESSFUL, logDuress)
    child.putFlag(JournalSetting.SELFIE_ON_SUCCESSFUL, logDuress && flag(JournalSetting.SELFIE_ON_DURESS))
}
