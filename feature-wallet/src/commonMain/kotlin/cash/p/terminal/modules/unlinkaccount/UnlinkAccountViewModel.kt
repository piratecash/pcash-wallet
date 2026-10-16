package cash.p.terminal.modules.unlinkaccount

import org.jetbrains.compose.resources.StringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import cash.p.terminal.modules.manageaccount.AccountDeletionState
import cash.p.terminal.resources.ManageAccount_Delete_ConfirmationLose
import cash.p.terminal.resources.ManageAccount_Delete_ConfirmationRemove
import cash.p.terminal.resources.Res
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import cash.p.terminal.resources.ManageAccount_DeleteWarning
import cash.p.terminal.resources.ManageAccount_Delete_hardware_wallet_warning
import cash.p.terminal.resources.ManageKeys_Delete_FromPhone
import cash.p.terminal.resources.ManageKeys_StopWatching

class UnlinkAccountViewModel(
    private val account: Account,
    private val accountManager: IAccountManager
) : ViewModel() {
    val accountName = account.name
    val deletionState = AccountDeletionState()

    var confirmations by mutableStateOf<List<ConfirmationItem>>(listOf())
        private set
    var unlinkEnabled by mutableStateOf(false)
        private set
    var deleteWarningMsg by mutableStateOf<StringResource?>(null)
        private set
    var closeScreen by mutableStateOf(false)
        private set

    val deleteButtonText = when {
            account.isWatchAccount -> Res.string.ManageKeys_StopWatching
            else -> Res.string.ManageKeys_Delete_FromPhone
        }

    init {
        if (account.isWatchAccount) {
            deleteWarningMsg = Res.string.ManageAccount_DeleteWarning
        } else if(account.isHardwareWalletAccount){
            deleteWarningMsg = Res.string.ManageAccount_Delete_hardware_wallet_warning
        } else {
            confirmations = listOf(
                ConfirmationItem(ConfirmationType.ConfirmationRemove),
                ConfirmationItem(ConfirmationType.ConfirmationLos),
            )
        }

        updateUnlinkEnabledState()
    }

    fun toggleConfirm(item: ConfirmationItem) {
        val index = confirmations.indexOf(item)
        if (index != -1) {
            confirmations = confirmations.toMutableList().apply {
                this[index] = item.copy(confirmed = !item.confirmed)
            }

            updateUnlinkEnabledState()
        }
    }

    /***
     * We use GlobalScope to finish the process even after dialog was closed
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun onUnlink() = GlobalScope.launch {
        closeScreen = deletionState.run { accountManager.delete(account.id) }
    }

    private fun updateUnlinkEnabledState() {
        unlinkEnabled = confirmations.none { !it.confirmed }
    }
}

enum class ConfirmationType(val title: TranslatableString) {
    ConfirmationRemove(TranslatableString.ResString(Res.string.ManageAccount_Delete_ConfirmationRemove)),
    ConfirmationLos(TranslatableString.ResString(Res.string.ManageAccount_Delete_ConfirmationLose))
}

data class ConfirmationItem(val confirmationType: ConfirmationType, val confirmed: Boolean = false)
