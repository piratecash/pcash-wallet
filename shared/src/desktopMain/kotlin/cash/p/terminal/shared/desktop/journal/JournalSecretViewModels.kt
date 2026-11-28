package cash.p.terminal.shared.desktop.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cash.p.terminal.modules.pin.LevelOutcome
import cash.p.terminal.shared.desktop.lock.PasswordCheckViewModel
import cash.p.terminal.shared.desktop.lock.SecretPasswordViewModel
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.UnlockOutcome
import cash.p.terminal.wallet.PassphraseValidator
import io.horizontalsystems.core.ICurrentDateProvider

enum class JournalSecretKind { JOURNAL_PASSWORD, DELETE_CONTACTS }

/** Asks for the secret of [kind]; a wrong one counts into the lock screen's lockout. */
class JournalSecretCheckViewModel(
    private val kind: JournalSecretKind,
    private val secrets: DesktopLoginJournalSecrets,
    private val vault: DesktopVault,
    currentDateProvider: ICurrentDateProvider,
) : PasswordCheckViewModel(vault, currentDateProvider) {

    var confirmed by mutableStateOf(false)
        private set

    override suspend fun check(password: CharArray): UnlockOutcome = when (kind) {
        JournalSecretKind.JOURNAL_PASSWORD -> secrets.verifyJournalPassword(password)
        JournalSecretKind.DELETE_CONTACTS -> secrets.disableDeleteContactsPassword(password)
    }.toUnlockOutcome()

    override fun onPasswordAccepted() {
        confirmed = true
    }

    private suspend fun JournalSecretOutcome.toUnlockOutcome(): UnlockOutcome = when (this) {
        JournalSecretOutcome.SUCCESS -> UnlockOutcome.Success
        JournalSecretOutcome.WRONG_PASSWORD -> UnlockOutcome.WrongPassword(attemptsLeft = null)
        JournalSecretOutcome.LOCKED_OUT -> vault.lockedOutUntil()?.let(UnlockOutcome::LockedOut) ?: UnlockOutcome.Failed
        JournalSecretOutcome.PASSWORD_IN_USE, JournalSecretOutcome.FAILED -> UnlockOutcome.Failed
    }
}

/** Sets the secret of [kind] from a form of the same rules as every other password of the vault. */
class JournalSecretPasswordViewModel(
    private val kind: JournalSecretKind,
    private val secrets: DesktopLoginJournalSecrets,
    passphraseValidator: PassphraseValidator,
) : SecretPasswordViewModel(passphraseValidator) {

    override suspend fun save(secret: CharArray): LevelOutcome = when (kind) {
        JournalSecretKind.JOURNAL_PASSWORD -> secrets.setJournalPassword(secret)
        JournalSecretKind.DELETE_CONTACTS -> secrets.setDeleteContactsPassword(secret)
    }.toLevelOutcome()

    private fun JournalSecretOutcome.toLevelOutcome(): LevelOutcome = when (this) {
        JournalSecretOutcome.SUCCESS -> LevelOutcome.SUCCESS
        JournalSecretOutcome.PASSWORD_IN_USE -> LevelOutcome.PASSWORD_IN_USE
        else -> LevelOutcome.FAILED
    }
}
