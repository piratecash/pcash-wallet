package cash.p.terminal.modules.walletconnect.request

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.ui.compose.components.ListErrorView
import cash.p.terminal.ui_compose.entities.DataState

@Composable
fun WcRequestPreScreen(navigation: HSNavigation) {
    val viewModelPre = viewModel<WCRequestPreViewModel>(
        factory = WCRequestPreViewModel.Factory()
    )

    val uiState = viewModelPre.uiState

    if (uiState is DataState.Success) {
        WcRequestScreen(navigation, uiState.data.sessionRequest, uiState.data.wcAction)
    } else if (uiState is DataState.Error) {
        ListErrorView(uiState.error.message ?: "Error") { }
    }
}
