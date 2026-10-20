package cash.p.terminal.modules.settings.security.autolock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.App

object AutoLockModule {
    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AutoLockIntervalsViewModel(App.localStorage) as T
        }
    }
}
