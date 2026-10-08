package cash.p.terminal.modules.softwareupdate.history

import androidx.compose.runtime.Composable
import cash.p.terminal.modules.softwareupdate.navigateToChangelog
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import org.koin.compose.viewmodel.koinViewModel

class VersionHistoryPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: VersionHistoryViewModel = koinViewModel()
        VersionHistoryScreen(
            uiState = viewModel.uiState,
            onBack = navigation::navigateUpSafely,
            onRetry = viewModel::retry,
            onVersionClick = navigation::navigateToChangelog,
        )
    }
}
