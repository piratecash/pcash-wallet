package cash.p.terminal.feature.logging.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.core.ILoginRecordRepository
import io.horizontalsystems.core.ILoggingSettings
import io.horizontalsystems.core.entities.AutoDeletePeriod
import kotlinx.coroutines.launch

class LoggingSettingsViewModel(
    private val loggingSettings: ILoggingSettings,
    private val loginRecordRepository: ILoginRecordRepository,
    private val userManager: UserManager,
    private val journalSecrets: LoginJournalSecrets,
    checkPremiumUseCase: CheckPremiumUseCase
) : ViewModel() {

    private val currentLevel: Int
        get() = userManager.getUserLevel()

    var uiState by mutableStateOf(
        LoggingSettingsUiState(
            logSuccessfulLoginsEnabled = false,
            passcodeEnabled = journalSecrets.isJournalSecretSet(),
            deleteContactsPasscodeEnabled = journalSecrets.isDeleteContactsSecretSet(),
            deleteButtonEnabled = false,
            isPremiumActive = checkPremiumUseCase.getPremiumType().isPremium()
        ).withStoredSettings()
    )
        private set

    init {
        viewModelScope.launch {
            loginRecordRepository.observeHasRecords(userManager.getUserLevel())
                .collect { hasRecords ->
                    uiState = uiState.copy(deleteButtonEnabled = hasRecords)
                }
        }
        viewModelScope.launch {
            journalSecrets.changes.collect {
                updatePasscodeLoggingState()
            }
        }
    }

    fun setSelfieOnSuccessfulLoginEnabled(enabled: Boolean) = store { setSelfieOnSuccessfulLoginEnabled(it, enabled) }

    fun setLogSuccessfulLoginsEnabled(enabled: Boolean) = store { setLogSuccessfulLoginsEnabled(it, enabled) }

    fun setLogUnsuccessfulLoginsEnabled(enabled: Boolean) = store { setLogUnsuccessfulLoginsEnabled(it, enabled) }

    fun setSelfieOnUnsuccessfulLoginEnabled(enabled: Boolean) =
        store { setSelfieOnUnsuccessfulLoginEnabled(it, enabled) }

    fun setLogIntoDuressModeEnabled(enabled: Boolean) = store { setLogIntoDuressModeEnabled(it, enabled) }

    fun onSelfieOnDuressLoginEnabled(enabled: Boolean) = store { setSelfieOnDuressLoginEnabled(it, enabled) }

    fun setDeleteAllAuthDataOnDuressEnabled(enabled: Boolean) =
        store { setDeleteAllAuthDataOnDuressEnabled(it, enabled) }

    fun setAutoDeletePeriod(period: AutoDeletePeriod) = store { setAutoDeleteLogsPeriod(it, period.value) }

    /** Shows what was stored, not what was asked: a setter may refuse the change. */
    private inline fun store(write: ILoggingSettings.(level: Int) -> Unit) {
        loggingSettings.write(currentLevel)
        uiState = uiState.withStoredSettings()
    }

    private fun LoggingSettingsUiState.withStoredSettings() = copy(
        logSuccessfulLoginsEnabled = loggingSettings.getLogSuccessfulLoginsEnabled(currentLevel),
        selfieOnSuccessfulLoginEnabled = loggingSettings.getSelfieOnSuccessfulLoginEnabled(currentLevel),
        logUnsuccessfulLoginsEnabled = loggingSettings.getLogUnsuccessfulLoginsEnabled(currentLevel),
        selfieOnUnsuccessfulLoginEnabled = loggingSettings.getSelfieOnUnsuccessfulLoginEnabled(currentLevel),
        logIntoDuressModeEnabled = loggingSettings.getLogIntoDuressModeEnabled(currentLevel),
        selfieOnDuressLoginEnabled = loggingSettings.getSelfieOnDuressLoginEnabled(currentLevel),
        deleteAllAuthDataOnDuressEnabled = loggingSettings.getDeleteAllAuthDataOnDuressEnabled(currentLevel),
        autoDeletePeriod = AutoDeletePeriod.fromValue(loggingSettings.getAutoDeleteLogsPeriod(currentLevel)),
    )

    fun deleteAllLogs() {
        viewModelScope.launch {
            loginRecordRepository.deleteAll(userManager.getUserLevel())
        }
    }

    fun disablePasscodeLoggingPin() {
        journalSecrets.disableJournalSecret()
        updatePasscodeLoggingState()
    }

    fun disableDeleteContactsPin() {
        journalSecrets.disableDeleteContactsSecret()
        updatePasscodeLoggingState()
    }

    fun updatePasscodeLoggingState() {
        uiState = uiState.copy(
            passcodeEnabled = journalSecrets.isJournalSecretSet(),
            deleteContactsPasscodeEnabled = journalSecrets.isDeleteContactsSecretSet()
        )
    }
}

data class LoggingSettingsUiState(
    val logSuccessfulLoginsEnabled: Boolean,
    val isPremiumActive: Boolean,
    val passcodeEnabled: Boolean = false,
    val deleteContactsPasscodeEnabled: Boolean = false,
    val selfieOnSuccessfulLoginEnabled: Boolean = false,
    val logUnsuccessfulLoginsEnabled: Boolean = false,
    val selfieOnUnsuccessfulLoginEnabled: Boolean = false,
    val logIntoDuressModeEnabled: Boolean = false,
    val selfieOnDuressLoginEnabled: Boolean = false,
    val deleteAllAuthDataOnDuressEnabled: Boolean = false,
    val autoDeletePeriod: AutoDeletePeriod = AutoDeletePeriod.YEAR,
    val deleteButtonEnabled: Boolean = false,
    val noCameraPermissions: Boolean = false,
)
