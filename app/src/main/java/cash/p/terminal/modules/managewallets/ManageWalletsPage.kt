package cash.p.terminal.modules.managewallets

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.modules.enablecoin.restoresettings.RestoreSettingsViewModel
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

        // Hardware wallet: intercept the system back gesture to offer scan-or-cancel first.
        BackHandler {
            if (viewModel.showScanToAddButton) {
                showScanOrCancelDialog(navigation, viewModel)
            } else {
                navigation.navigateUp()
            }
        }

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
