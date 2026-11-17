package cash.p.terminal.shared.desktop.journal

import cash.p.terminal.shared.desktop.vault.DesktopVault
import io.horizontalsystems.core.ILoggingSettings

/** The last photo of the open level failed: no camera, or access to it is denied. */
internal fun DesktopVault.selfieProblem(): Boolean =
    openSession()?.rootSafe?.flag(JournalSetting.SELFIE_PROBLEM) ?: false

internal fun ILoggingSettings.anySelfieEnabled(level: Int): Boolean =
    getLogSuccessfulLoginsEnabled(level) && getSelfieOnSuccessfulLoginEnabled(level) ||
        getLogUnsuccessfulLoginsEnabled(level) && getSelfieOnUnsuccessfulLoginEnabled(level) ||
        getLogIntoDuressModeEnabled(level) && getSelfieOnDuressLoginEnabled(level)
