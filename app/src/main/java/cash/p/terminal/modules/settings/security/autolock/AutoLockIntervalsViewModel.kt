package cash.p.terminal.modules.settings.security.autolock

import androidx.lifecycle.ViewModel
import cash.p.terminal.core.ILocalStorage

class AutoLockIntervalsViewModel(
    private val localStorage: ILocalStorage
) : ViewModel() {

    val selectedInterval: AutoLockInterval
        get() = localStorage.autoLockInterval

    fun onSelectAutoLockInterval(autoLockInterval: AutoLockInterval) {
        localStorage.autoLockInterval = autoLockInterval
    }
}
