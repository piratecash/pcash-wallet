package cash.p.terminal.shared.desktop.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.TransactionSettingsStorage
import cash.p.terminal.core.managers.TransactionHiddenManager
import cash.p.terminal.modules.settings.security.autolock.AutoLockInterval
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.managers.TransactionDisplayLevel
import cash.p.terminal.wallet.managers.TransactionHiddenState
import io.horizontalsystems.core.DesktopSettingsStore
import kotlinx.coroutines.launch

class DesktopSecurityViewModel(
    private val vault: DesktopVault,
    private val settingsStore: DesktopSettingsStore,
    private val balanceHiddenManager: IBalanceHiddenManager,
    private val transactionSettings: TransactionSettingsStorage,
    private val transactionHiddenManager: TransactionHiddenManager,
) : ViewModel() {

    var uiState by mutableStateOf(
        DesktopSecurityUiState(
            autoLockInterval = settingsStore.autoLockInterval,
            balanceAutoHideEnabled = balanceHiddenManager.balanceAutoHidden,
            transferPasswordEnabled = transactionSettings.transferPasscodeEnabled,
            transactionHidden = transactionHiddenManager.transactionHiddenFlow.value,
        )
    )
        private set

    init {
        // The display level is changed on its own page, the separate password on another.
        viewModelScope.launch {
            transactionHiddenManager.transactionHiddenFlow.collect { uiState = uiState.copy(transactionHidden = it) }
        }
    }

    fun onResume() {
        uiState = uiState.copy(autoLockInterval = settingsStore.autoLockInterval)
    }

    fun onBalanceAutoHideChange(enabled: Boolean) {
        uiState = uiState.copy(balanceAutoHideEnabled = enabled)
        balanceHiddenManager.setBalanceAutoHidden(enabled)
    }

    fun onTransferPasswordChange(enabled: Boolean) {
        uiState = uiState.copy(transferPasswordEnabled = enabled)
        transactionSettings.transferPasscodeEnabled = enabled
    }

    fun onTransactionAutoHideChange(enabled: Boolean) = transactionHiddenManager.setAutoHideEnabled(enabled)

    fun onDeleteSeparatePassword() = transactionHiddenManager.clearSeparatePin()

    fun onLockClick() {
        viewModelScope.launch { vault.lock() }
    }
}

data class DesktopSecurityUiState(
    val autoLockInterval: AutoLockInterval,
    val balanceAutoHideEnabled: Boolean,
    val transferPasswordEnabled: Boolean,
    private val transactionHidden: TransactionHiddenState,
) {
    val transactionAutoHideEnabled: Boolean get() = transactionHidden.transactionHideEnabled
    val displayLevel: TransactionDisplayLevel get() = transactionHidden.transactionDisplayLevel
    val separatePasswordExists: Boolean get() = transactionHidden.transactionAutoHidePinExists
}
