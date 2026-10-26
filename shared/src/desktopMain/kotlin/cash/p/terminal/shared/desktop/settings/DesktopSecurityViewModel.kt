package cash.p.terminal.shared.desktop.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import io.horizontalsystems.core.DesktopSettingsStore
import kotlinx.coroutines.launch

class DesktopSecurityViewModel(
    private val vault: DesktopVault,
    private val settingsStore: DesktopSettingsStore,
    private val balanceHiddenManager: IBalanceHiddenManager,
) : ViewModel() {

    var autoLockInterval by mutableStateOf(settingsStore.autoLockInterval)
        private set

    var balanceAutoHideEnabled by mutableStateOf(balanceHiddenManager.balanceAutoHidden)
        private set

    fun onResume() {
        autoLockInterval = settingsStore.autoLockInterval
    }

    fun onBalanceAutoHideChange(enabled: Boolean) {
        balanceAutoHideEnabled = enabled
        balanceHiddenManager.setBalanceAutoHidden(enabled)
    }

    fun onLockClick() {
        viewModelScope.launch { vault.lock() }
    }
}
