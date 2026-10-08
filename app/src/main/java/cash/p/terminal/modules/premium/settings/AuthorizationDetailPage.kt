package cash.p.terminal.modules.premium.settings

import androidx.compose.runtime.Composable
import cash.p.terminal.feature.logging.detail.LoggingDetailScreen
import cash.p.terminal.feature.logging.detail.LoggingDetailViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class AuthorizationDetailPage(val initialRecordId: Long) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: LoggingDetailViewModel = koinViewModel {
            parametersOf(initialRecordId)
        }

        LoggingDetailScreen(
            viewModel = viewModel,
            onClose = { navigation.navigateUpSafely() }
        )
    }
}
