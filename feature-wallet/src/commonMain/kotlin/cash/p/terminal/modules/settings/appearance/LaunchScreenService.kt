package cash.p.terminal.modules.settings.appearance

import cash.p.terminal.core.AppearanceSettingsStore
import cash.p.terminal.entities.LaunchPage
import cash.p.terminal.ui_compose.Select
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LaunchScreenService(private val localStorage: AppearanceSettingsStore) {
    private val screens by lazy { LaunchPage.entries }

    val selectedLaunchScreen: LaunchPage
        get() = localStorage.launchPage ?: DEFAULT_LAUNCH_PAGE

    private val _optionsFlow = MutableStateFlow(
        Select(selectedLaunchScreen, screens)
    )
    val optionsFlow = _optionsFlow.asStateFlow()

    fun setLaunchScreen(launchPage: LaunchPage) {
        localStorage.launchPage = launchPage

        _optionsFlow.update {
            Select(launchPage, screens)
        }
    }

    companion object {
        val DEFAULT_LAUNCH_PAGE = LaunchPage.Auto
    }
}
