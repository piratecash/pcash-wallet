package cash.p.terminal.modules.keystore

import android.os.Parcelable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.managers.TonConnectManager
import kotlinx.parcelize.Parcelize

object KeyStoreModule {
    class Factory(private val mode: ModeType) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return KeyStoreViewModel(
                getKoinInstance(),
                getKoinInstance(),
                getKoinInstance(),
                lazy { getKoinInstance<TonConnectManager>() },
                mode
            ) as T
        }
    }

    @Parcelize
    enum class ModeType : Parcelable {
        NoSystemLock,
        InvalidKey,
        UserAuthentication
    }
}
