package cash.p.terminal.modules.settings.security.tor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.ITorManager
import cash.p.terminal.core.tor.TorPlatform
import co.touchlab.kermit.Logger
import kotlinx.coroutines.launch

class SecurityTorSettingsViewModel(
    private val torManager: ITorManager,
    private val torPlatform: TorPlatform,
) : ViewModel() {

    private val logger = Logger.withTag("SecurityTorSettingsViewModel")

    var torCheckEnabled by mutableStateOf(torManager.isTorEnabled)
        private set

    var showRestartAlert by mutableStateOf(false)
        private set

    var showNotSupported by mutableStateOf(false)
        private set

    var restartApp by mutableStateOf(false)
        private set

    fun setTorEnabledWithChecks(enabled: Boolean) {
        if (enabled && !torPlatform.isSupported) {
            showNotSupported = true
            return
        }
        torCheckEnabled = enabled
        showRestartAlert = true
    }

    fun setTorEnabled() {
        if (torCheckEnabled) {
            torManager.setTorAsEnabled()
            restartApp = true
        } else {
            torManager.setTorAsDisabled()
            viewModelScope.launch {
                try {
                    torManager.stop()
                } catch (e: Throwable) {
                    logger.w(e) { "Tor exception" }
                } finally {
                    torPlatform.beforeRestartAfterDisable()
                    restartApp = true
                }
            }
        }
    }

    fun restartAppAlertShown() {
        showRestartAlert = false
    }

    fun notSupportedShown() {
        showNotSupported = false
    }

    fun appRestarted() {
        restartApp = false
    }

    fun resetSwitch() {
        torCheckEnabled = torManager.isTorEnabled
    }

}
