package cash.p.terminal.modules.evmnetwork.addrpc

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.feature.wallet.qrScannerAvailable
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.AddEvmSyncSource_AddRPCSource
import cash.p.terminal.resources.AddEvmSyncSource_BasicAuthentication
import cash.p.terminal.resources.AddEvmSyncSource_RpcUrl
import cash.p.terminal.resources.Res
import cash.p.terminal.ui.compose.components.AddSourceScreen
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui.compose.components.toInputState
import cash.p.terminal.ui_compose.components.HeaderText
import io.horizontalsystems.core.entities.Blockchain
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun AddRpcScreen(
    page: AddRpcPage,
    navigation: HSNavigation,
    blockchain: Blockchain,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets
) {
    val viewModel: AddRpcViewModel = koinViewModel { parametersOf(blockchain) }
    if (viewModel.viewState.closeScreen) {
        navigation.navigateUpFrom(page)
        viewModel.onScreenClose()
    }

    AddSourceScreen(
        title = stringResource(Res.string.AddEvmSyncSource_AddRPCSource),
        onClose = navigation::navigateUpSafely,
        onAdd = viewModel::onAddClick,
        windowInsets = windowInsets
    ) {
        HeaderText(stringResource(Res.string.AddEvmSyncSource_RpcUrl))
        FormsInput(
            modifier = Modifier.padding(horizontal = 16.dp),
            qrScannerEnabled = qrScannerAvailable(),
            navigation = navigation,
            qrScannerTitle = stringResource(Res.string.AddEvmSyncSource_RpcUrl),
            onValueChange = viewModel::onEnterRpcUrl,
            hint = "",
            state = viewModel.viewState.urlCaution.toInputState()
        )
        Spacer(modifier = Modifier.height(24.dp))

        HeaderText(stringResource(Res.string.AddEvmSyncSource_BasicAuthentication))
        FormsInput(
            modifier = Modifier.padding(horizontal = 16.dp),
            qrScannerEnabled = qrScannerAvailable(),
            navigation = navigation,
            qrScannerTitle = stringResource(Res.string.AddEvmSyncSource_BasicAuthentication),
            onValueChange = viewModel::onEnterBasicAuth,
            hint = ""
        )
    }
}
