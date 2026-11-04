package cash.p.terminal.modules.tonconnect

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.modules.main.MainActivity
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.ui_compose.components.HudHelper

class TonConnectSendRequestPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val mainActivity = LocalActivity.current as? MainActivity
        if (mainActivity == null) {
            HudHelper.showErrorMessage(
                LocalView.current,
                R.string.unknown_error
            )
            navigation.navigateUp()
            return
        }
        val mainActivityViewModel = mainActivity.viewModel
        val viewModel = viewModel<TonConnectSendRequestViewModel>(initializer = {
            val sendRequestEntity = mainActivityViewModel.tcSendRequest.replayCache.firstOrNull()
            mainActivityViewModel.onTcSendRequestHandled()

            TonConnectSendRequestViewModel(
                sendRequestEntity,
                getKoinInstance(),
                getKoinInstance(),
                getKoinInstance(),
                getKoinInstance(),
            )
        })
        TonConnectSendRequestScreen(navigation, viewModel)
    }
}
