package cash.p.terminal.shared.desktop.lock

import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.UnlockOutcome
import io.horizontalsystems.core.ICurrentDateProvider

class UnlockViewModel(
    private val vault: DesktopVault,
    currentDateProvider: ICurrentDateProvider,
) : PasswordCheckViewModel(vault, currentDateProvider) {

    override suspend fun check(password: CharArray): UnlockOutcome = vault.unlock(password)

    /** Shown once: returning from the reset screen must land on a plain unlock form. */
    fun onDataUnreadableShown() {
        uiState = uiState.copy(outcome = null)
    }
}
