package cash.p.terminal.modules.manageaccount.evmaddress

import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.core.managers.FaqManager
import cash.p.terminal.modules.manageaccount.evmaddress.PublicViewKeyPage.Input
import cash.p.terminal.modules.manageaccount.ui.ActionButton
import cash.p.terminal.modules.manageaccount.ui.HidableContent
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.helpers.TextHelper
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.parcelize.Parcelize
import cash.p.terminal.navigation.navigateUpSafely

class PublicViewKeyPage(val input: Input) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        PublicViewKeyScreen(input, navigation)
    }

    @Parcelize
    data class Input(
        @StringRes val titleResId: Int,
        val viewKey: String,
        val showInfo: Boolean
    ) : Parcelable

}

@Composable
private fun PublicViewKeyScreen(input: Input, navigation: HSNavigation) {
    val view = LocalView.current
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(input.titleResId),
                navigationIcon = {
                    HsBackButton(onClick = navigation::navigateUpSafely)
                },
                menuItems = if (input.showInfo) {
                    listOf(
                        MenuItem(
                            title = TranslatableString.ResString(R.string.Info_Title),
                            icon = R.drawable.ic_info_24,
                            onClick = {
                                FaqManager.showFaqPage(FaqManager.faqPrivateKeys)
                            }
                        )
                    )
                } else {
                    emptyList()
                }
            )
        }) { innerPaddings ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPaddings)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))
            HidableContent(input.viewKey)
            Spacer(Modifier.weight(1f))
            ActionButton(R.string.Alert_Copy) {
                TextHelper.copyText(input.viewKey)
                HudHelper.showSuccessMessage(view, R.string.Hud_Text_Copied)
            }
        }
    }
}
