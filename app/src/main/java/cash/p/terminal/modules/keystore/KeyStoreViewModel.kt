package cash.p.terminal.modules.keystore

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.wallet.AccountDeletionBlockedException
import cash.p.terminal.wallet.AccountDeletionPreflight
import io.horizontalsystems.core.IKeyStoreManager
import kotlinx.coroutines.launch

class KeyStoreViewModel(
    private val keyStoreManager: IKeyStoreManager,
    private val localStorage: ILocalStorage,
    private val deletionPreflight: AccountDeletionPreflight,
    mode: KeyStoreModule.ModeType
) : ViewModel() {

    var recoveryRequired by mutableStateOf(false)
        private set

    var showSystemLockWarning by mutableStateOf(false)
        private set

    var showBiometricPrompt by mutableStateOf(false)
        private set

    var showInvalidKeyWarning by mutableStateOf(false)
        private set

    var openMainModule by mutableStateOf(false)
        private set

    var closeApp by mutableStateOf(false)
        private set


    var showTermsDialog by mutableStateOf(false)
        private set

    var isSystemPinRequired  by mutableStateOf(localStorage.isSystemPinRequired)
        private set

    init {
        when (mode) {
            KeyStoreModule.ModeType.NoSystemLock,
            KeyStoreModule.ModeType.InvalidKey -> withResetPreflight {
                keyStoreManager.resetApp(mode.name)
                showSystemLockWarning = mode == KeyStoreModule.ModeType.NoSystemLock
                showInvalidKeyWarning = mode == KeyStoreModule.ModeType.InvalidKey
            }

            KeyStoreModule.ModeType.UserAuthentication -> {
                showBiometricPrompt = true
            }
        }
    }

    fun onCloseInvalidKeyWarning() {
        if (!showInvalidKeyWarning || recoveryRequired) return
        withResetPreflight {
            keyStoreManager.removeKey()
            showInvalidKeyWarning = false
            openMainModule = true
        }
    }

    private fun withResetPreflight(action: () -> Unit) {
        viewModelScope.launch {
            try {
                deletionPreflight.ensureCanReset()
                action()
            } catch (_: AccountDeletionBlockedException) {
                showSystemLockWarning = false
                showInvalidKeyWarning = false
                showTermsDialog = false
                recoveryRequired = true
            }
        }
    }

    fun onAuthenticationCanceled() {
        showBiometricPrompt = false
        closeApp = true
    }

    fun onAuthenticationSuccess() {
        if (recoveryRequired) return
        showBiometricPrompt = false
        openMainModule = true
    }

    fun openMainModuleCalled() {
        openMainModule = false
    }

    fun closeAppCalled() {
        closeApp = false
    }

    fun changeSystemPinRequired(required: Boolean) {
        if (!showSystemLockWarning || recoveryRequired) return
        setSystemPinRequiredInner(required)

        if (!required) {
            showTermsDialog = true
        }
    }

    fun onCloseTermsDialog() {
        showTermsDialog = false
        changeSystemPinRequired(true)
    }

    fun onTermsAccepted() {
        if (!showTermsDialog || recoveryRequired) return
        showTermsDialog = false

        setSystemPinRequiredInner(false)
    }

    private fun setSystemPinRequiredInner(required: Boolean) {
        isSystemPinRequired = required
        localStorage.isSystemPinRequired = isSystemPinRequired
    }
}
