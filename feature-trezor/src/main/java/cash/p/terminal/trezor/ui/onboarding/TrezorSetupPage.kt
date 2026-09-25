package cash.p.terminal.trezor.ui.onboarding

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.trezor.R
import cash.p.terminal.trezor.ui.TrezorSideEffect
import cash.p.terminal.trezor.ui.TrezorWalletViewModel
import cash.p.terminal.ui_compose.components.HudHelper
import kotlinx.parcelize.Parcelize
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class TrezorSetupPage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<TrezorWalletViewModel>(
            parameters = { parametersOf(input.accountName) }
        )
        val context = LocalContext.current
        val view = LocalView.current
        val connectFailedMessage = stringResource(R.string.trezor_connect_failed)

        LaunchedEffect(viewModel.uiState.success) {
            if (viewModel.uiState.success) {
                navigation.setResult(this@TrezorSetupPage, Result(success = true))
                navigation.navigateUp()
            }
        }

        LaunchedEffect(connectFailedMessage) {
            viewModel.sideEffects.collect { effect ->
                when (effect) {
                    is TrezorSideEffect.OpenIntent -> context.startActivity(effect.intent)
                    is TrezorSideEffect.ShowError -> HudHelper.showErrorMessage(
                        view,
                        effect.message ?: connectFailedMessage,
                    )
                }
            }
        }

        TrezorSetupScreen(
            uiState = viewModel.uiState,
            onConnect = viewModel::connectTrezor,
            onOpenSetupGuide = viewModel::openSetupGuide,
            onDismissSetupPrompt = viewModel::dismissNotInitialized,
            onSelectMoneroRestoreMode = viewModel::selectMoneroRestoreMode,
            onMoneroRestoreHeightChange = viewModel::setMoneroRestoreHeight,
            onSubmitMoneroRestore = viewModel::submitMoneroRestore,
            onBack = navigation::navigateUp
        )
    }

    @Parcelize
    data class Input(val accountName: String) : Parcelable

    @Parcelize
    data class Result(val success: Boolean) : Parcelable
}
