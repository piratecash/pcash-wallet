package cash.p.terminal.modules.manageaccount.evmaddress

import org.jetbrains.compose.resources.StringResource
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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.feature.wallet.FaqTopic
import cash.p.terminal.feature.wallet.openFaq
import cash.p.terminal.modules.manageaccount.evmaddress.PublicViewKeyPage.Input
import cash.p.terminal.modules.manageaccount.ui.ActionButton
import cash.p.terminal.modules.manageaccount.ui.HidableContent
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.resources.Info_Title
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_info_24
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.modules.manageaccount.ui.rememberCopyWithHud
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Alert_Copy

class PublicViewKeyPage(val input: Input) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        PublicViewKeyScreen(input, navigation)
    }

    data class Input(
        val titleResId: StringResource,
        val viewKey: String,
        val showInfo: Boolean
    )

}

@Composable
private fun PublicViewKeyScreen(input: Input, navigation: HSNavigation) {
    val copyWithHud = rememberCopyWithHud()
    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(input.titleResId),
                navigationIcon = {
                    HsBackButton(onClick = navigation::navigateUpSafely)
                },
                menuItems = if (input.showInfo) {
                    listOf(
                        MenuItem(
                            title = TranslatableString.ResString(Res.string.Info_Title),
                            icon = Res.drawable.ic_info_24,
                            onClick = {
                                navigation.openFaq(FaqTopic.PrivateKeys)
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
            ActionButton(Res.string.Alert_Copy) {
                copyWithHud(input.viewKey)
            }
        }
    }
}
