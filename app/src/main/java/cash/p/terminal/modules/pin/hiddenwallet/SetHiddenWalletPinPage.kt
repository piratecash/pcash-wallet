package cash.p.terminal.modules.pin.hiddenwallet

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import cash.p.terminal.R
import cash.p.terminal.modules.main.MainActivity
import cash.p.terminal.modules.main.MainPage
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.pin.ui.PinSet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.ui_compose.components.HudHelper

class SetHiddenWalletPinPage : HSPage(screenshotEnabled = false) {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val view = LocalView.current
        val activity = LocalActivity.current

        PinSet(
            title = stringResource(id = R.string.create_hidden_wallet_pin),
            description = stringResource(id = R.string.create_hidden_wallet_pin_description),
            pinType = PinType.HIDDEN_WALLET,
            dismissWithSuccess = {
                HudHelper.showSuccessMessage(view, R.string.Hud_Text_Created)
                (activity as MainActivity).openCreateNewWallet()
            },
            onBackPress = {
                navigation.removeLastUntil(MainPage::class, inclusive = false)
            },
        )
    }
}
