package cash.p.terminal.modules.manageaccount

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import cash.p.terminal.R
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.wallet.AccountDeletionBlockedException

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
internal fun AccountDeletionError(state: AccountDeletionState) {
    val view = LocalView.current
    LaunchedEffect(state.blocked) {
        if (state.blocked) HudHelper.showErrorMessage(view, R.string.unexpected_error)
    }
}
