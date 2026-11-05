package cash.p.terminal.modules.manageaccount.backupconfirmkey

import cash.p.terminal.core.IRandomProvider
import cash.p.terminal.modules.manageaccount.backupkey.manualBackupPhrase
import io.horizontalsystems.core.ViewModelUiState
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager

class BackupConfirmKeyViewModel(
    private val account: Account,
    private val accountManager: IAccountManager,
    randomProvider: IRandomProvider
) : ViewModelUiState<BackupConfirmUiState>() {

    private var check = WordCheckState.create(account.type.manualBackupPhrase()?.first.orEmpty(), randomProvider)
    private var confirmed = false

    override fun createState() = BackupConfirmUiState(check = check, confirmed = confirmed)

    fun onSelectWord(optionIndex: Int) {
        if (confirmed) return
        check = check.select(optionIndex)
        if (check.isComplete) {
            accountManager.markAsBackedUp(account.id)
            accountManager.onHandledBackupRequiredNewAccount()
            confirmed = true
        }
        emitState()
    }
}

data class BackupConfirmUiState(
    val check: WordCheckState,
    val confirmed: Boolean
)
