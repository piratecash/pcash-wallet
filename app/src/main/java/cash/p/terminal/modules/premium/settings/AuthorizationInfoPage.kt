package cash.p.terminal.modules.premium.settings

import androidx.compose.runtime.Composable
import androidx.paging.compose.collectAsLazyPagingItems
import cash.p.terminal.feature.logging.history.LoggingListScreen
import cash.p.terminal.feature.logging.history.LoggingListViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import org.koin.compose.viewmodel.koinViewModel

class AuthorizationInfoPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: LoggingListViewModel = koinViewModel()

        LoggingListScreen(
            loginRecords = viewModel.loginRecordsFlow.collectAsLazyPagingItems(),
            onDeleteAllClick = viewModel::deleteAllLogs,
            onDeleteClick = viewModel::deleteLog,
            onItemClick = { recordId ->
                navigation.slideFromRight(AuthorizationDetailPage(recordId))
            },
            onClose = { navigation.navigateUpSafely() }
        )
    }
}
