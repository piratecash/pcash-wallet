package cash.p.terminal.modules.theme

import cash.p.terminal.core.AppearanceSettingsStore
import cash.p.terminal.ui_compose.Select
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ThemeService(private val localStorage: AppearanceSettingsStore) {
    private val themes by lazy { ThemeType.entries }

    val selectedTheme: ThemeType
        get() = localStorage.currentTheme

    private val _optionsFlow = MutableStateFlow(
        Select(selectedTheme, themes)
    )
    val optionsFlow = _optionsFlow.asStateFlow()

    fun setThemeType(themeType: ThemeType) {
        localStorage.currentTheme = themeType

        _optionsFlow.update {
            Select(themeType, themes)
        }

        applyNightMode(themeType)
    }

    companion object {
        val DEFAULT_THEME = ThemeType.System
    }
}

internal expect fun applyNightMode(themeType: ThemeType)
