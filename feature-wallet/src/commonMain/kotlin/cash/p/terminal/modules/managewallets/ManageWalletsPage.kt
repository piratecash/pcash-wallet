package cash.p.terminal.modules.managewallets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import cash.p.terminal.modules.enablecoin.restoresettings.RestoreSettingsViewModel
import cash.p.terminal.modules.moneroconfigure.MoneroConfigurePage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui.dialogs.CancelOrScanSheet

class ManageWalletsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val vmFactory = remember { ManageWalletsModule.Factory() }
        val viewModel: ManageWalletsViewModel = viewModel(factory = vmFactory)
        val restoreSettingsViewModel: RestoreSettingsViewModel = viewModel(factory = vmFactory)
        MoneroRestoreHeightRequests(viewModel, navigation)

        // Hardware wallet: intercept the system back gesture to offer scan-or-cancel first.
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            onBackCompleted = {
                if (viewModel.showScanToAddButton) {
                    showScanOrCancelDialog(navigation, viewModel)
                } else {
                    navigation.navigateUp()
                }
            },
        )

        ManageWalletsScreen(
            navigation = navigation,
            manageWalletsCallback = viewModel,
            onBackPressed = {
                if (viewModel.showScanToAddButton) {
                    showScanOrCancelDialog(navigation, viewModel)
                } else {
                    navigation.navigateUpSafely()
                }
            },
            requestScan = {
                viewModel.requestScanToAddTokens(false)
            },
            restoreSettingsViewModel = restoreSettingsViewModel
        )
    }
}

private fun showScanOrCancelDialog(navigation: HSNavigation, viewModel: ManageWalletsViewModel) {
    navigation.slideFromBottomForResult<CancelOrScanSheet.Result>(CancelOrScanSheet()) { result ->
        if (result.confirmed) {
            viewModel.requestScanToAddTokens(true)
        } else {
            navigation.removeLastUntil(ManageWalletsPage::class, inclusive = true)
        }
    }
}

@Composable
fun MoneroRestoreHeightRequests(viewModel: ManageWalletsViewModel, navigation: HSNavigation) {
    LaunchedEffect(Unit) {
        viewModel.moneroRestoreHeightRequests.collect {
            navigation.slideFromBottomForResult<MoneroConfigurePage.Result>(
                MoneroConfigurePage(MoneroConfigurePage.Input(initialConfig = null))
            ) { result ->
                viewModel.onMoneroRestoreHeightConfigured(result.config)
            }
        }
    }
}
