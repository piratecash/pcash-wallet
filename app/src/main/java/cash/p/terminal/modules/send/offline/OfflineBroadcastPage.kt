package cash.p.terminal.modules.send.offline

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.components.HudHelper
import kotlinx.parcelize.Parcelize
import org.koin.compose.viewmodel.koinViewModel

class OfflineBroadcastPage(val input: Input?) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: OfflineBroadcastViewModel = koinViewModel()
        val view = LocalView.current
        val initialInput = input?.initialInput

        LaunchedEffect(Unit) {
            initialInput?.let(viewModel::prefillAndAdvance)
        }

        // Unrecoverable inputs (bad payload, no broadcasting wallet) surface a toast and close the
        // screen instead of stranding the user on an empty confirmation.
        viewModel.uiState.dismissError?.let { message ->
            LaunchedEffect(message) {
                HudHelper.showErrorMessage(view, message)
                viewModel.onDismissErrorShown()
                navigation.navigateUp()
            }
        }

        // Recoverable errors (e.g. enabling the network failed) keep the user on the screen so they
        // can retry, so they only surface a toast without navigating away.
        viewModel.uiState.errorMessage?.let { message ->
            LaunchedEffect(message) {
                HudHelper.showErrorMessage(view, message)
                viewModel.onErrorMessageShown()
            }
        }

        OfflineBroadcastScreen(
            uiState = viewModel.uiState,
            onPickNetwork = viewModel::onPickNetwork,
            onSelectBlockchain = viewModel::onSelectBlockchain,
            onPrimaryAction = viewModel::onPrimaryAction,
            onRetry = viewModel::onRetry,
            onBack = { if (!viewModel.onBack()) navigation.navigateUpSafely() },
            onClose = navigation::navigateUpSafely,
        )
    }

    @Parcelize
    data class Input(val initialInput: String? = null) : Parcelable
}
