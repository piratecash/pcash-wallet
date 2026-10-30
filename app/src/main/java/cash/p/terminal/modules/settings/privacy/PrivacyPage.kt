package cash.p.terminal.modules.settings.privacy

import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import org.koin.compose.viewmodel.koinViewModel

class PrivacyPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: PrivacyViewModel = koinViewModel()
        PrivacyScreen(
            shareCrashData = viewModel.uiState.uiStatsEnabled,
            onToggleShareCrashData = viewModel::toggleCrashData,
            onClose = navigation::navigateUpSafely
        )
    }
}
