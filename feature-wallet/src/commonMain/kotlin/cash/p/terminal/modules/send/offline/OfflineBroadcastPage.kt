package cash.p.terminal.modules.send.offline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.offline_broadcast_file_read_failed
import cash.p.terminal.resources.offline_broadcast_invalid_input
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import io.horizontalsystems.core.Translator
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

class OfflineBroadcastPage(val input: Input?) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: OfflineBroadcastViewModel = koinViewModel()
        val files: OfflineTransactionFiles = koinInject()
        val hud = rememberHudMessenger()
        val initialInput = input?.initialInput
        val fileUri = input?.fileUri

        LaunchedEffect(initialInput, fileUri) {
            when {
                initialInput != null -> viewModel.prefillAndAdvance(initialInput)
                fileUri != null -> {
                    when (val result = files.read(fileUri)) {
                        is OfflineTransactionFileTransfer.ReadResult.Success ->
                            viewModel.prefillAndAdvance(result.content)

                        OfflineTransactionFileTransfer.ReadResult.Invalid -> {
                            hud.showError(Translator.getString(Res.string.offline_broadcast_invalid_input))
                            navigation.navigateUp()
                        }

                        OfflineTransactionFileTransfer.ReadResult.Unavailable -> {
                            hud.showError(Translator.getString(Res.string.offline_broadcast_file_read_failed))
                            navigation.navigateUp()
                        }
                    }
                }
            }
        }

        // Unrecoverable inputs (bad payload, no broadcasting wallet) surface a toast and close the
        // screen instead of stranding the user on an empty confirmation.
        viewModel.uiState.dismissError?.let { message ->
            LaunchedEffect(message) {
                hud.showError(message)
                viewModel.onDismissErrorShown()
                navigation.navigateUp()
            }
        }

        // Recoverable errors (e.g. enabling the network failed) keep the user on the screen so they
        // can retry, so they only surface a toast without navigating away.
        viewModel.uiState.errorMessage?.let { message ->
            LaunchedEffect(message) {
                hud.showError(message)
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

    data class Input(
        val initialInput: String? = null,
        val fileUri: String? = null,
    )
}
