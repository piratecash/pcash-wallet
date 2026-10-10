package cash.p.terminal.modules.zcashnetwork.addserver

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import kotlinx.parcelize.Parcelize
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

    @Parcelize
    data object Result : Parcelable
}
