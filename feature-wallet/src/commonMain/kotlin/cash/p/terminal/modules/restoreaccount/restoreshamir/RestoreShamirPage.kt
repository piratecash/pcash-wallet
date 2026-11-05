package cash.p.terminal.modules.restoreaccount.restoreshamir

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.modules.restoreaccount.RestoreSelectCoinsPage
import cash.p.terminal.modules.restoreaccount.RestoreViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.rememberQrScannerLauncher
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.shamir_restore_title
import cash.p.terminal.ui.compose.components.ThirdPartyKeyboardWarning
import cash.p.terminal.ui.compose.components.rememberThirdPartyKeyboardWarningState
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.reflect.KClass

class RestoreShamirPage(val input: Input) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val mainViewModel: RestoreViewModel = viewModel { RestoreViewModel() }
        val viewModel = koinViewModel<RestoreShamirViewModel>()
        val uiState = viewModel.uiState
        val scannerTitle = stringResource(Res.string.shamir_restore_title)
        val qrScanner = navigation.rememberQrScannerLauncher(koinInject(), viewModel::onScanned)
        val keyboardWarning = rememberThirdPartyKeyboardWarningState(viewModel)

        LaunchedEffect(Unit) {
            input.prefill?.let(viewModel::applyPrefill)
        }

        LaunchedEffect(uiState.accountType) {
            uiState.accountType?.let { accountType ->
                mainViewModel.setAccountData(
                    accountType,
                    viewModel.accountName,
                    manualBackup = true,
                    fileBackup = false
                )
                navigation.slideFromRight(
                    RestoreSelectCoinsPage(RestoreShamirPage::class, input.popOffOnSuccess, input.popOffInclusive)
                )
                viewModel.onSelectCoinsShown()
            }
        }

        RestoreShamirScreen(
            uiState = uiState,
            onEnterShare = viewModel::onEnterShare,
            onTypeShare = keyboardWarning::onSecretInput,
            onContinue = viewModel::onContinue,
            onScanClick = { qrScanner.open(scannerTitle) },
            walletActions = ShamirWalletActions(
                onEnterName = viewModel::onEnterName,
                onTogglePassphrase = viewModel::onTogglePassphrase,
                onEnterPassphrase = viewModel::onEnterPassphrase,
            ),
            onBackClick = navigation::navigateUpSafely,
        )
        ThirdPartyKeyboardWarning(keyboardWarning)
    }

    data class Input(
        val popOffOnSuccess: KClass<out HSPage>,
        val popOffInclusive: Boolean,
        val prefill: ShamirPrefill? = null,
    )
}
