package cash.p.terminal.modules.watchaddress


import org.jetbrains.compose.resources.getString
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import org.koin.compose.viewmodel.koinViewModel
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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.watchaddress.selectblockchains.SelectBlockchainsPage
import cash.p.terminal.modules.zcashconfigure.ZcashConfigurePage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.rememberResultLauncher
import cash.p.terminal.navigation.slideFromBottom
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Watch_Address_Watch
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui.compose.components.FormsInputMultiline
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.delay
import kotlin.reflect.KClass
import cash.p.terminal.resources.Hud_Text_AddressAdded
import cash.p.terminal.resources.ManageAccount_Name
import cash.p.terminal.resources.ManageAccounts_WatchAddress
import cash.p.terminal.resources.Watch_Address_Hint
import cash.p.terminal.resources.icon_binocule_24
import cash.p.terminal.resources.qr_scanner_title_smart_scan

class WatchAddressPage(val input: ManageAccountsModule.Input?) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val popUpToInclusiveId = input?.popOffOnSuccess ?: WatchAddressPage::class
        val inclusive = input?.popOffInclusive ?: true
        WatchAddressScreen(navigation, popUpToInclusiveId, inclusive)
    }

}

@Composable
fun WatchAddressScreen(navigation: HSNavigation, popUpToInclusiveId: KClass<out HSPage>, inclusive: Boolean) {
    val hudMessenger = rememberHudMessenger()

    val viewModel = koinViewModel<WatchAddressViewModel>()
    val uiState = viewModel.uiState
    val accountCreated = uiState.accountCreated
    val submitType = uiState.submitButtonType
    val accountType = uiState.accountType
    val accountName = uiState.accountName
    val zcashConfigureLauncher = navigation.rememberResultLauncher<ZcashConfigurePage.Result> { result ->
        viewModel.onZcashHeightEntered(result.config)
    }

    LaunchedEffect(accountCreated) {
        if (accountCreated) {
            hudMessenger.showSuccess(getString(Res.string.Hud_Text_AddressAdded), Res.drawable.icon_binocule_24)
            delay(300)
            navigation.removeLastUntil(popUpToInclusiveId, inclusive)
        }
    }

    LaunchedEffect(uiState.zcashHeightRequested) {
        if (!uiState.zcashHeightRequested) return@LaunchedEffect

        viewModel.zcashHeightRequestOpened()
        zcashConfigureLauncher.slideFromBottom(ZcashConfigurePage(ZcashConfigurePage.Input(initialConfig = null)))
    }

    if (accountType != null) {
        viewModel.blockchainSelectionOpened()

        navigation.slideFromRight(
            SelectBlockchainsPage(
                SelectBlockchainsPage.Input(
                    popUpToInclusiveId,
                    inclusive,
                    accountType,
                    accountName
                )
            )
        )
    }

    Column(modifier = Modifier.background(color = ComposeAppTheme.colors.backgroundBase)) {
        AppBar(
            title = stringResource(Res.string.ManageAccounts_WatchAddress),
            navigationIcon = {
                HsBackButton(onClick = { navigation.navigateUpSafely() })
            },
            menuItems = buildList {
                when (submitType) {
                    is SubmitButtonType.Watch -> {
                        add(
                            MenuItem(
                                title = TranslatableString.ResString(Res.string.Watch_Address_Watch),
                                onClick = viewModel::onClickWatch,
                                enabled = submitType.enabled
                            )
                        )
                    }

                    is SubmitButtonType.Next -> {
                        add(
                            MenuItem(
                                title = TranslatableString.ResString(Res.string.Watch_Address_Watch),
                                onClick = viewModel::onClickNext,
                                enabled = submitType.enabled
                            )
                        )
                    }
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            HeaderText(stringResource(Res.string.ManageAccount_Name))
            FormsInput(
                modifier = Modifier.padding(horizontal = 16.dp),
                initial = viewModel.accountName,
                pasteEnabled = false,
                hint = viewModel.defaultAccountName,
                onValueChange = viewModel::onEnterAccountName
            )
            Spacer(Modifier.height(32.dp))
            FormsInputMultiline(
                modifier = Modifier.padding(horizontal = 16.dp),
                initial = viewModel.enteredInput,
                hint = stringResource(Res.string.Watch_Address_Hint),
                qrScannerNavigation = navigation,
                qrScannerTitle = stringResource(Res.string.qr_scanner_title_smart_scan),
                state = uiState.inputState,
                onValueChange = {
                    viewModel.onEnterInput(it)
                },
                onClear = {
                },
                onScanQR = {
                },
                onPaste = {
                }
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
