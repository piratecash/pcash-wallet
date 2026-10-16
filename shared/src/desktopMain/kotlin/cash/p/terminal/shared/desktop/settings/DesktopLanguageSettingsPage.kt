package cash.p.terminal.shared.desktop.settings

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.shared.settings.LanguageSettingsItem
import cash.p.terminal.shared.settings.LanguageSettingsContent
import cash.p.terminal.shared.settings.languageSettingsItems
import cash.p.terminal.strings.helpers.LocaleType
import io.horizontalsystems.core.DesktopLanguageManager
import org.koin.compose.viewmodel.koinViewModel

class DesktopLanguageSettingsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: DesktopLanguageSettingsViewModel = koinViewModel()
        LanguageSettingsContent(
            items = viewModel.items,
            onSelect = { localeTag ->
                // The current language changes nothing: close the page instead of recreating the app.
                if (viewModel.isCurrent(localeTag)) {
                    navigation.navigateUpSafely()
                } else {
                    viewModel.onSelectLocale(localeTag)
                }
            },
            onBack = navigation::navigateUpSafely,
        )
    }
}

/** Selecting a language recreates the whole app from the root, so this page needs no close. */
class DesktopLanguageSettingsViewModel(
    private val languageManager: DesktopLanguageManager,
) : ViewModel() {

    val items: List<LanguageSettingsItem> = languageSettingsItems(languageManager)

    fun isCurrent(localeTag: String): Boolean =
        localeTag == LocaleType.of(languageManager.currentLocaleTag)?.tag

    fun onSelectLocale(localeTag: String) {
        languageManager.currentLocaleTag = localeTag
    }
}
