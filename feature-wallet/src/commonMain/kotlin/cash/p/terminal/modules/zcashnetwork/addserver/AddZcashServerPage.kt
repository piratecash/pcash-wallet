package cash.p.terminal.modules.zcashnetwork.addserver

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import org.koin.compose.viewmodel.koinViewModel

class AddZcashServerPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: AddZcashServerViewModel = koinViewModel()
        val uiState = viewModel.uiState

        LaunchedEffect(uiState.closeScreen) {
            if (uiState.closeScreen) {
                navigation.setResult(this@AddZcashServerPage, Result)
                navigation.navigateUp()
            }
        }

        AddZcashServerScreen(
            uiState = uiState,
            onUrlChange = viewModel::onUrlChange,
            onAddClick = viewModel::onAddClick,
            onClose = navigation::navigateUpSafely,
        )
    }

    @CommonParcelize
    data object Result : CommonParcelable
}
