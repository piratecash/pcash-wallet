package cash.p.terminal.shared.desktop.lock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.UnlockOutcome
import io.horizontalsystems.core.ICurrentDateProvider

class ConfirmPasswordViewModel(
    private val vault: DesktopVault,
    currentDateProvider: ICurrentDateProvider,
) : PasswordCheckViewModel(vault, currentDateProvider) {

    var confirmed by mutableStateOf(false)
        private set

    override suspend fun check(password: CharArray): UnlockOutcome = vault.verifyPassword(password)

    override fun onPasswordAccepted() {
        confirmed = true
    }
}
