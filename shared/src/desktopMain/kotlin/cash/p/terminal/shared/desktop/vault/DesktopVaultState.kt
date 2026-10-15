package cash.p.terminal.shared.desktop.vault

import java.util.Date

sealed interface DesktopVaultState {
    data object NoVault : DesktopVaultState
    data object Locked : DesktopVaultState
    class Unlocked(val session: DesktopStorageSession) : DesktopVaultState
}

enum class CreateOutcome { SUCCESS, FAILED }

sealed interface UnlockOutcome {
    data object Success : UnlockOutcome
    data class WrongPassword(val attemptsLeft: Int?) : UnlockOutcome
    data class LockedOut(val until: Date) : UnlockOutcome
    data object Corrupted : UnlockOutcome
    /** The password is right but the data could not be opened: retryable, never a reason to wipe. */
    data object StorageUnavailable : UnlockOutcome
    data object Failed : UnlockOutcome
}

enum class ChangePasswordOutcome { SUCCESS, WRONG_PASSWORD, CORRUPTED, FAILED }

enum class WipeOutcome { SUCCESS, FAILED }
