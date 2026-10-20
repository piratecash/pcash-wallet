package cash.p.terminal.modules.settings.language

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.core.fullRestart
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.shared.settings.LanguageSettingsContent

class LanguageSettingsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val activity = LocalActivity.current
        LanguageScreen(
            navigation,
            { activity?.fullRestart() }
        )
    }

}

@Composable
private fun LanguageScreen(
    navigation: HSNavigation,
    reloadApp: () -> Unit,
    viewModel: LanguageSettingsViewModel = viewModel(
        factory = LanguageSettingsModule.Factory()
    )
) {
    if (viewModel.closeScreen) {
        navigation.navigateUp()
    }

    if (viewModel.reloadApp) {
        reloadApp()
    }

    LanguageSettingsContent(
        items = viewModel.languageItems,
        onSelect = viewModel::onSelectLocale,
        onBack = navigation::navigateUpSafely,
    )
}
