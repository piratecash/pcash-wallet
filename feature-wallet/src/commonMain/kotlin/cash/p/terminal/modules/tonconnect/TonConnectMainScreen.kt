package cash.p.terminal.modules.tonconnect

import androidx.compose.foundation.layout.Column
import cash.p.terminal.resources.ic_ton_connect_24
import cash.p.terminal.resources.WalletConnect_NoConnection
import cash.p.terminal.resources.TonConnect_Title
import cash.p.terminal.resources.TonConnect_NewConnect
import cash.p.terminal.resources.TonConnect_Error_InvalidUrl
import cash.p.terminal.resources.Button_TryAgain
import cash.p.terminal.resources.Button_Cancel
import cash.p.terminal.resources.Res
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import cash.p.terminal.ui_compose.components.AppModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.Caution
import cash.p.terminal.modules.contacts.screen.ConfirmationBottomSheet
import cash.p.terminal.modules.evmfee.ButtonsGroupWithShade
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.rememberQrScannerLauncher
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.ui.compose.components.ListEmptyView
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun TonConnectMainScreen(
    viewModel: TonConnectListViewModel,
    navigation: HSNavigation, deepLinkUri: String?,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
    val invalidUrlBottomSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    val coroutineScope = rememberCoroutineScope()

    val uiState = viewModel.uiState

    val dAppRequestUri = uiState.dAppRequestUri
    LaunchedEffect(dAppRequestUri) {
        if (dAppRequestUri != null) {
            navigation.slideFromBottom(TonConnectNewPage(dAppRequestUri))
            viewModel.onDappRequestHandled()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            delay(300)
            invalidUrlBottomSheetState.show()
            viewModel.onErrorHandled()
        }
    }

    LaunchedEffect(Unit) {
        if (deepLinkUri != null) {
            viewModel.setConnectionUri(deepLinkUri)
        }
    }

    val scannerTitle = stringResource(Res.string.TonConnect_Title)
    val qrScanner = navigation.rememberQrScannerLauncher(koinInject(), viewModel::setConnectionUri)

    AppModalBottomSheetLayout(
        sheetState = invalidUrlBottomSheetState,
        sheetContent = {
            ConfirmationBottomSheet(
                title = stringResource(Res.string.TonConnect_Title),
                text = stringResource(Res.string.TonConnect_Error_InvalidUrl),
                iconPainter = painterResource(Res.drawable.ic_ton_connect_24),
                iconTint = ColorFilter.tint(ComposeAppTheme.colors.brandDefault),
                confirmText = stringResource(Res.string.Button_TryAgain),
                cautionType = Caution.Type.Warning,
                cancelText = stringResource(Res.string.Button_Cancel),
                onConfirm = {
                    coroutineScope.launch {
                        invalidUrlBottomSheetState.hide()
                        qrScanner.open(scannerTitle, showPasteButton = true)
                    }
                },
                onClose = {
                    coroutineScope.launch { invalidUrlBottomSheetState.hide() }
                }
            )
        }
    ) {
        Scaffold(
            containerColor = ComposeAppTheme.colors.backgroundBase,
            topBar = {
                AppBar(
                    title = stringResource(Res.string.TonConnect_Title),
                    navigationIcon = {
                        HsBackButton(onClick = { navigation.navigateUpSafely() })
                    }
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .padding(it)
                    .windowInsetsPadding(windowInsets)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val dapps = uiState.dapps
                    if (dapps.isEmpty()) {
                        ListEmptyView(
                            text = stringResource(Res.string.WalletConnect_NoConnection),
                            icon = Res.drawable.ic_ton_connect_24
                        )
                    } else {
                        TonConnectSessionList(
                            dapps = dapps,
                            onDelete = viewModel::disconnect
                        )
                    }
                }
                ButtonsGroupWithShade {
                    ButtonPrimaryYellow(
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp)
                            .fillMaxWidth(),
                        title = stringResource(Res.string.TonConnect_NewConnect),
                        onClick = { qrScanner.open(scannerTitle, showPasteButton = true) }
                    )
                }
            }
        }
    }
}
