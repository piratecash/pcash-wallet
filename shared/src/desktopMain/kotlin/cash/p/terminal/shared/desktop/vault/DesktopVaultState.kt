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
    /** The file is an earlier format that has no migration; the only way on is to delete the data. */
    data object LegacyFormat : UnlockOutcome
    /** The password is right but the data could not be opened: retryable, never a reason to wipe. */
    data object StorageUnavailable : UnlockOutcome
    data object Failed : UnlockOutcome
}

enum class ChangePasswordOutcome { SUCCESS, WRONG_PASSWORD, LOCKED_OUT, PASSWORD_IN_USE, CORRUPTED, FAILED }

enum class WipeOutcome { SUCCESS, FAILED }

/** The wallet above the storage, for the level changes the vault decides on. */
interface DesktopWalletLayer {
    /** Runs under the vault lock, right before the root moves: must neither suspend nor wait for anything. */
    fun onLevelNarrowing()

    /** The session already stands at [level]; the wallet follows before the screen opens. */
    suspend fun onLevelNarrowed(level: Int)

    /** Returns once nothing runs on accounts other than [keptAccountIds]. */
    suspend fun awaitReleased(keptAccountIds: Set<String>)

    /** The session is about to close for another one: the wallet lets go of its accounts. */
    suspend fun onSessionClosing()
}

/** What the login journal hears from the lock screen. The calls made under the vault lock must not wait. */
interface VaultLoginEvents {
    /** A password at the lock screen opened [session], or came back to it. */
    fun onEntered(session: DesktopStorageSession)

    /** Under the vault lock: a password at the lock screen was refused. */
    fun onFailedUnlock()

    /** Under the vault lock, before the file goes: the delete-contacts password was typed at the lock screen. */
    fun onEraseContacts()

    object None : VaultLoginEvents {
        override fun onEntered(session: DesktopStorageSession) = Unit
        override fun onFailedUnlock() = Unit
        override fun onEraseContacts() = Unit
    }
}
