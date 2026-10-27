package cash.p.terminal.modules.hardwarewallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.ledger.ui.LedgerSetupPage
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.moneroconfigure.MoneroConfigurePage
import cash.p.terminal.modules.zcashconfigure.ZcashConfigurePage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.rememberResultLauncher
import cash.p.terminal.navigation.slideFromBottom
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.tangem.ui.HardwareWalletError
import cash.p.terminal.tangem.ui.HardwareWalletOnboardingPage
import cash.p.terminal.trezor.ui.onboarding.TrezorSetupPage
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.core.hasNFC
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

class HardwareWalletPage(val input: ManageAccountsModule.Input?) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val popUpToInclusiveId = input?.popOffOnSuccess ?: HardwareWalletPage::class
        val inclusive = input?.popOffInclusive != false
        val viewModel = koinViewModel<HardwareWalletViewModel>()

        val view = LocalView.current
        val onboardingLauncher = navigation.rememberResultLauncher<HardwareWalletOnboardingPage.Result> {
            viewModel.success = it.success
        }
        val trezorLauncher = navigation.rememberResultLauncher<TrezorSetupPage.Result> {
            viewModel.onHardwareSetupFinished(it.success)
        }
        val ledgerLauncher = navigation.rememberResultLauncher<LedgerSetupPage.Result> {
            viewModel.onHardwareSetupFinished(it.success)
        }

        LaunchedEffect(Unit) {
            viewModel.errorEvents.collect { error ->
                when (error) {
                    HardwareWalletError.CardNotActivated -> {
                        onboardingLauncher.slideFromRight(
                            HardwareWalletOnboardingPage(HardwareWalletOnboardingPage.Input(viewModel.accountName))
                        )
                    }

                    HardwareWalletError.WalletsNotCreated -> {
                        HudHelper.showErrorMessage(
                            contenView = view,
                            resId = R.string.error_wallets_creating
                        )
                    }

                    HardwareWalletError.AttestationFailed,
                    HardwareWalletError.UnknownError -> {
                        HudHelper.showErrorMessage(
                            contenView = view,
                            resId = R.string.unknown_error
                        )
                    }

                    is HardwareWalletError.NeedFactoryReset -> Unit
                    HardwareWalletError.ErrorInBackupCard -> Unit
                }
            }
        }
        RestoreHeightRequests(viewModel, navigation)

        HardwareWalletScreen(
            navigation = navigation,
            onScanCard = viewModel::scanCard,
            onConnectTrezor = {
                trezorLauncher.slideFromRight(TrezorSetupPage(TrezorSetupPage.Input(viewModel.accountName)))
            },
            onConnectLedger = {
                ledgerLauncher.slideFromRight(LedgerSetupPage(viewModel.accountName))
            },
            onFinish = { navigation.removeLastUntil(popUpToInclusiveId, inclusive) },
            viewModel = viewModel,
        )
    }
}

@Composable
private fun RestoreHeightRequests(viewModel: HardwareWalletViewModel, navigation: HSNavigation) {
    val zcashLauncher = navigation.rememberResultLauncher<ZcashConfigurePage.Result> { result ->
        viewModel.onRestoreHeightConfigured(RestoreHeightChain.Zcash, result.config)
    }
    val moneroLauncher = navigation.rememberResultLauncher<MoneroConfigurePage.Result> { result ->
        viewModel.onRestoreHeightConfigured(RestoreHeightChain.Monero, result.config)
    }
    LaunchedEffect(Unit) {
        viewModel.restoreHeightRequests.collect { chain ->
            when (chain) {
                RestoreHeightChain.Zcash -> zcashLauncher.slideFromBottom(
                    ZcashConfigurePage(ZcashConfigurePage.Input(initialConfig = null))
                )

                RestoreHeightChain.Monero -> moneroLauncher.slideFromBottom(
                    MoneroConfigurePage(MoneroConfigurePage.Input(initialConfig = null))
                )
            }
        }
    }
}

@Composable
internal fun HardwareWalletScreen(
    navigation: HSNavigation,
    onScanCard: () -> Unit,
    onConnectTrezor: () -> Unit,
    onConnectLedger: () -> Unit,
    onFinish: () -> Unit,
    viewModel: HardwareWalletViewModel
) {
    val view = LocalView.current
    LaunchedEffect(viewModel.success) {
        if (viewModel.success) {
            HudHelper.showSuccessMessage(
                contenView = view,
                resId = R.string.Hud_Text_Created,
                icon = R.drawable.icon_add_to_wallet_24,
            )
            delay(300)
            onFinish()
        }
    }

    Column(modifier = Modifier.background(color = ComposeAppTheme.colors.backgroundBase)) {
        AppBar(
            title = stringResource(R.string.hardware_wallet),
            navigationIcon = {
                HsBackButton(onClick = { navigation.navigateUpSafely() })
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            HeaderText(stringResource(id = R.string.ManageAccount_Name))
            FormsInput(
                modifier = Modifier.padding(horizontal = 16.dp),
                initial = viewModel.accountName,
                pasteEnabled = false,
                hint = viewModel.defaultAccountName,
                onValueChange = viewModel::onChangeAccountName
            )
            Spacer(Modifier.height(32.dp))
            ConnectButtons(
                hasNfc = view.context.hasNFC(),
                onScanCard = onScanCard,
                onConnectTrezor = onConnectTrezor,
                onConnectLedger = onConnectLedger,
            )
        }
    }
}

@Composable
private fun ConnectButtons(
    hasNfc: Boolean,
    onScanCard: () -> Unit,
    onConnectTrezor: () -> Unit,
    onConnectLedger: () -> Unit,
) {
    Column {
        ButtonPrimaryYellow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            title = stringResource(R.string.scan_tangem_card),
            enabled = hasNfc,
            onClick = onScanCard
        )
        if (!hasNfc) {
            InfoText(
                text = stringResource(R.string.hardware_wallet_not_detected_error),
                paddingBottom = 0.dp
            )
        }
        Spacer(Modifier.height(16.dp))
        ButtonPrimaryDefault(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            title = stringResource(R.string.connect_trezor),
            onClick = onConnectTrezor
        )
        Spacer(Modifier.height(16.dp))
        ButtonPrimaryDefault(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            title = stringResource(R.string.connect_ledger),
            onClick = onConnectLedger
        )
        LedgerDeveloperSettings()
    }
}
