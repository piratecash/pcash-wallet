package cash.p.terminal.modules.backupalert

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class BackupAlertViewModel(
    private val accountManager: IAccountManager,
) : ViewModel() {
    var account by mutableStateOf<Account?>(null)
        private set

    private var job: Job? = null

    fun resume() {
        job = viewModelScope.launch {
            accountManager.newAccountBackupRequiredFlow.collect {
                account = it
            }
        }
    }

    fun pause() {
        job?.cancel()
    }

    fun onHandled() {
        accountManager.onHandledBackupRequiredNewAccount()
    }

}
