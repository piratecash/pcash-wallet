package cash.p.terminal.modules.premium.settings

import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.TransactionSettingsStorage
import cash.p.terminal.core.managers.AmlStatusManager
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import io.horizontalsystems.core.ViewModelUiState
import kotlinx.coroutines.launch

internal class PremiumSettingsViewModel(
    private val localStorage: TransactionSettingsStorage,
    private val checkPremiumUseCase: CheckPremiumUseCase,
    val platformSettings: PremiumPlatformSettings?,
    private val amlStatusManager: AmlStatusManager
) : ViewModelUiState<PremiumSettingsUiState>() {
    private var checkEnabled = localStorage.recipientAddressContractCheckEnabled
    private var showLoggingAlert = false

    init {
        checkLoggingAlert()

        viewModelScope.launch {
            amlStatusManager.enabledStateFlow.collect {
                emitState()
            }
        }
    }

    private fun checkLoggingAlert() {
        viewModelScope.launch {
            showLoggingAlert = platformSettings?.shouldShowLoggingAlert() ?: false
            emitState()
        }
    }

    override fun createState() = PremiumSettingsUiState(
        checkEnabled = checkEnabled && checkPremiumUseCase.getPremiumType().isPremium(),
        notificationNotAvailable = !checkPremiumUseCase.getPremiumType().isPremium(),
        amlCheckReceivedEnabled = amlStatusManager.isEnabled && checkPremiumUseCase.getPremiumType()
            .isPremium(),
        showAlertIcon = showLoggingAlert,
        platformSettingsAvailable = platformSettings != null,
    )

    fun setAddressContractChecking(enabled: Boolean) {
        localStorage.recipientAddressContractCheckEnabled = enabled
        checkEnabled = enabled
        emitState()
    }

    fun setAmlCheckReceivedEnabled(enabled: Boolean) {
        amlStatusManager.setEnabled(enabled)
    }
}


internal data class PremiumSettingsUiState(
    val checkEnabled: Boolean,
    val notificationNotAvailable: Boolean,
    val amlCheckReceivedEnabled: Boolean = false,
    val showAlertIcon: Boolean = false,
    val platformSettingsAvailable: Boolean = true,
)
