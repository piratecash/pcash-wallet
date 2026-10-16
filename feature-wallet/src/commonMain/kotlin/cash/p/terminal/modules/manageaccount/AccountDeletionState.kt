package cash.p.terminal.modules.manageaccount

import org.jetbrains.compose.resources.getString
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cash.p.terminal.wallet.AccountDeletionBlockedException
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.unexpected_error

class AccountDeletionState {
    var blocked by mutableStateOf(false)
        private set

    suspend fun run(action: suspend () -> Unit): Boolean {
        blocked = false
        return try {
            action()
            true
        } catch (_: AccountDeletionBlockedException) {
            blocked = true
            false
        }
    }
}

@Composable
fun AccountDeletionError(state: AccountDeletionState) {
    val hudMessenger = rememberHudMessenger()
    LaunchedEffect(state.blocked) {
        if (state.blocked) hudMessenger.showError(getString(Res.string.unexpected_error))
    }
}
