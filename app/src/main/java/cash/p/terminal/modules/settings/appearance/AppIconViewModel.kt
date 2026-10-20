package cash.p.terminal.modules.settings.appearance

import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.ui_compose.Select
import io.horizontalsystems.core.ViewModelUiState
import kotlinx.coroutines.launch

class AppIconViewModel(
    private val appIconService: AppIconService,
    private val localStorage: ILocalStorage,
) : ViewModelUiState<AppIconUIState>() {
    private var appIconOptions = appIconService.optionsFlow.value

    init {
        viewModelScope.launch {
            appIconService.optionsFlow
                .collect {
                    appIconOptions = it
                    emitState()
                }
        }
    }

    override fun createState() = AppIconUIState(
        appIconOptions = appIconOptions,
        pushNotificationsEnabled = localStorage.pushNotificationsEnabled,
        isCalculatorModeEnabled = localStorage.isCalculatorModeEnabled,
    )

    fun onEnterAppIcon(enabledAppIcon: AppIcon) {
        appIconService.setAppIcon(enabledAppIcon)
    }
}

data class AppIconUIState(
    val appIconOptions: Select<AppIcon>,
    val pushNotificationsEnabled: Boolean,
    val isCalculatorModeEnabled: Boolean,
)
