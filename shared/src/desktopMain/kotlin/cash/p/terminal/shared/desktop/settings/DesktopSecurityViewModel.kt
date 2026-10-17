package cash.p.terminal.shared.desktop.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.shared.desktop.vault.DesktopVault
import io.horizontalsystems.core.DesktopSettingsStore
import kotlinx.coroutines.launch

class DesktopSecurityViewModel(
    private val vault: DesktopVault,
    private val settingsStore: DesktopSettingsStore,
) : ViewModel() {

    var autoLockInterval by mutableStateOf(settingsStore.autoLockInterval)
        private set

    fun onResume() {
        autoLockInterval = settingsStore.autoLockInterval
    }

    fun onLockClick() {
        viewModelScope.launch { vault.lock() }
    }
}
