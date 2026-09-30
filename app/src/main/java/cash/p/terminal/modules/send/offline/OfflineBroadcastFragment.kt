package cash.p.terminal.modules.send.offline

import android.content.ContentResolver
import android.net.Uri
import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.navigation.NavController
import androidx.navigation.fragment.navArgs
import cash.p.terminal.R
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.HudHelper
import kotlinx.parcelize.Parcelize
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

class OfflineBroadcastFragment : BaseComposeFragment() {

    private val args: OfflineBroadcastFragmentArgs by navArgs()

    @Composable
    override fun GetContent(navController: NavController) {
        val viewModel: OfflineBroadcastViewModel = koinViewModel()
        val fileTransfer: OfflineTransactionFileTransfer = koinInject()
        val context = LocalContext.current
        val view = LocalView.current
        val initialInput = args.input?.initialInput
        val fileUri = args.input?.fileUri

        LaunchedEffect(initialInput, fileUri) {
            when {
                initialInput != null -> viewModel.prefillAndAdvance(initialInput)
                fileUri != null -> {
                    val uri = Uri.parse(fileUri)
                    val result = if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                        fileTransfer.read(context, uri)
                    } else {
                        OfflineTransactionFileTransfer.ReadResult.Unavailable
                    }
                    when (result) {
                        is OfflineTransactionFileTransfer.ReadResult.Success ->
                            viewModel.prefillAndAdvance(result.content)

                        OfflineTransactionFileTransfer.ReadResult.Invalid -> {
                            HudHelper.showErrorMessage(view, R.string.offline_broadcast_invalid_input)
                            navController.navigateUp()
                        }

                        OfflineTransactionFileTransfer.ReadResult.Unavailable -> {
                            HudHelper.showErrorMessage(view, R.string.offline_broadcast_file_read_failed)
                            navController.navigateUp()
                        }
                    }
                }
            }
        }

        // Unrecoverable inputs (bad payload, no broadcasting wallet) surface a toast and close the
        // screen instead of stranding the user on an empty confirmation.
        viewModel.uiState.dismissError?.let { message ->
            LaunchedEffect(message) {
                HudHelper.showErrorMessage(view, message)
                viewModel.onDismissErrorShown()
                navController.navigateUp()
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
            onBack = { if (!viewModel.onBack()) navController.navigateUpSafely() },
            onClose = navController::navigateUpSafely,
        )
    }

    @Parcelize
    data class Input(
        val initialInput: String? = null,
        val fileUri: String? = null,
    ) : Parcelable
}
