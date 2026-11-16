package cash.p.terminal.shared.desktop.lock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cash.p.terminal.core.managers.TransactionHiddenManager
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.UnlockOutcome
import io.horizontalsystems.core.ICurrentDateProvider

class ConfirmPasswordViewModel(
    private val check: ConfirmPasswordPage.Check,
    private val vault: DesktopVault,
    private val transactionHiddenManager: TransactionHiddenManager,
    currentDateProvider: ICurrentDateProvider,
) : PasswordCheckViewModel(vault, currentDateProvider) {

    var confirmed by mutableStateOf(false)
        private set

    // As Android's TRANSACTIONS_HIDE: a separate password, once set, replaces the app password here.
    override suspend fun check(password: CharArray): UnlockOutcome =
        if (check == ConfirmPasswordPage.Check.TRANSACTIONS_HIDE && separatePasswordExists()) {
            vault.verifyPassword(password) { transactionHiddenManager.isPinMatches(it.concatToString()) }
        } else {
            vault.verifyPassword(password)
        }

    override fun onPasswordAccepted() {
        confirmed = true
    }

    private fun separatePasswordExists() =
        transactionHiddenManager.transactionHiddenFlow.value.transactionAutoHidePinExists
}
