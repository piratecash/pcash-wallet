package cash.p.terminal.modules.settings.advancedsecurity.securereset

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import cash.p.terminal.R
import cash.p.terminal.modules.main.MainPage
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.pin.ui.PinSet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.ui_compose.components.HudHelper

class SetSecureResetPinPage : HSPage(screenshotEnabled = false) {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val view = LocalView.current

        PinSet(
            title = stringResource(R.string.SecureReset_Pin_Title),
            description = stringResource(R.string.SecureReset_Pin_Description),
            pinType = PinType.SECURE_RESET,
            dismissWithSuccess = {
                HudHelper.showSuccessMessage(view, R.string.Hud_Text_Created)
                navigation.removeLastUntil(MainPage::class, inclusive = false)
            },
            onBackPress = {
                navigation.removeLastUntil(MainPage::class, inclusive = false)
            }
        )
    }
}
