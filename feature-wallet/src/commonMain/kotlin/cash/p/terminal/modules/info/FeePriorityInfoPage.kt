package cash.p.terminal.modules.info

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.modules.info.ui.InfoBody
import cash.p.terminal.modules.info.ui.InfoHeader
import cash.p.terminal.modules.info.ui.InfoSubHeader
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.FeeInfo_Average
import cash.p.terminal.resources.FeeInfo_AverageDescription
import cash.p.terminal.resources.FeeInfo_Description
import cash.p.terminal.resources.FeeInfo_Fast
import cash.p.terminal.resources.FeeInfo_FastDescription
import cash.p.terminal.resources.FeeInfo_Slow
import cash.p.terminal.resources.FeeInfo_SlowDescription
import cash.p.terminal.resources.FeeInfo_Title
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

class FeePriorityInfoPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        InfoScreen { navigation.navigateUpSafely() }
    }
}

@Composable
private fun InfoScreen(
    onClose: () -> Unit
) {

    Surface(color = ComposeAppTheme.colors.backgroundBase) {
        Column {
            AppBar(
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Button_Close),
                        icon = Res.drawable.ic_close_24,
                        onClick = onClose
                    )
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                InfoHeader(Res.string.FeeInfo_Title)
                InfoBody(Res.string.FeeInfo_Description)
                InfoSubHeader(Res.string.FeeInfo_Slow)
                InfoBody(Res.string.FeeInfo_SlowDescription)
                InfoSubHeader(Res.string.FeeInfo_Average)
                InfoBody(Res.string.FeeInfo_AverageDescription)
                InfoSubHeader(Res.string.FeeInfo_Fast)
                InfoBody(Res.string.FeeInfo_FastDescription)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
