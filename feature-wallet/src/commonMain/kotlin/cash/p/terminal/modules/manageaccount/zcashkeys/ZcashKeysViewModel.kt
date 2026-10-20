package cash.p.terminal.modules.manageaccount.zcashkeys

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.adapters.zcash.ZcashKeyExporter
import cash.p.terminal.core.adapters.zcash.ZcashPrivateKeyType
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

data class RevealedKey(val type: ZcashPrivateKeyType, val key: String)

data class ZcashKeysUiState(
    val available: List<ZcashPrivateKeyType> = emptyList(),
    val revealed: RevealedKey? = null,
    val showError: ZcashPrivateKeyType? = null,
    val closeScreen: Boolean = false,
)

class ZcashKeysViewModel(
    accountId: String,
    accountManager: IAccountManager,
    private val zcashKeyExporter: ZcashKeyExporter,
) : ViewModel() {

    private val accountType: AccountType?
    private val revealJobs = mutableMapOf<ZcashPrivateKeyType, Job>()

    var uiState by mutableStateOf(ZcashKeysUiState())
        private set

    init {
        val account = accountManager.account(accountId)
        accountType = account?.type
        uiState = if (account == null) {
            ZcashKeysUiState(closeScreen = true)
        } else {
            ZcashKeysUiState(available = zcashKeyExporter.privateKeyTypes(account.type))
        }
    }

    fun reveal(type: ZcashPrivateKeyType) {
        if (revealJobs[type]?.isActive == true || uiState.revealed?.type == type) return
        val currentAccountType = accountType ?: return
        revealJobs[type] = viewModelScope.launch {
            val exportedKey = zcashKeyExporter.export(currentAccountType, type)
            // tryOrNull swallows CancellationException, and the native call is not
            // interruptible — without this the cancelled job still publishes.
            ensureActive()
            uiState = if (exportedKey != null) {
                uiState.copy(
                    revealed = RevealedKey(type, exportedKey),
                    showError = uiState.showError.takeIf { it != type },
                )
            } else {
                uiState.copy(showError = type)
            }
        }
    }

    /** A result arriving after the screen is gone would navigate on a later, unrelated entry. */
    fun cancelReveal() {
        revealJobs.values.forEach { it.cancel() }
        revealJobs.clear()
        uiState = uiState.copy(revealed = null, showError = null)
    }

    fun onKeyShown() {
        uiState = uiState.copy(revealed = null)
    }

    fun onErrorShown() {
        uiState = uiState.copy(showError = null)
    }
}
