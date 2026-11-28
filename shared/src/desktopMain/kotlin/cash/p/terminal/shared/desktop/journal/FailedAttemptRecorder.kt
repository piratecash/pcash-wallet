package cash.p.terminal.shared.desktop.journal

import cash.p.terminal.shared.desktop.vault.DesktopVault
import io.horizontalsystems.core.DesktopSettingsStore
import io.horizontalsystems.core.ICurrentDateProvider

/**
 * Every refused password at the lock screen goes into the mailbox for all slots alike; each level imports it by
 * its own settings. Nothing about the attempt depends on which levels exist.
 */
class FailedAttemptRecorder(
    private val vault: DesktopVault,
    private val camera: SelfieCamera,
    private val settingsStore: DesktopSettingsStore,
    private val dateProvider: ICurrentDateProvider,
    private val queue: JournalQueue,
) {
    /** Called under the vault lock: the attempt's moment and wipe generation are taken now, the photo later. */
    fun record() {
        val mailbox = vault.mailbox
        val generation = mailbox.wipeGeneration
        val timestamp = dateProvider.currentDate.time
        queue.submit("Recording a failed attempt") {
            val photo = if (isSelfieEnabled(settingsStore)) camera.captureFor(vault.openSession()?.rootSafe) else null
            mailbox.appendFailed(timestamp, photo, generation)
        }
    }

    companion object {
        /** One switch for the whole vault: a failed attempt belongs to no level. */
        private const val SELFIE_ON_FAILED_ATTEMPT = "loginJournalSelfieOnFailedAttempt"

        fun isSelfieEnabled(store: DesktopSettingsStore): Boolean = store.flag(SELFIE_ON_FAILED_ATTEMPT) ?: false

        fun setSelfieEnabled(store: DesktopSettingsStore, enabled: Boolean) =
            store.setFlag(SELFIE_ON_FAILED_ATTEMPT, enabled)
    }
}
