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
import cash.p.terminal.modules.info.ui.InfoBody
import cash.p.terminal.modules.info.ui.InfoSubHeader
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.StatusInfo_Confirmed
import cash.p.terminal.resources.StatusInfo_ConfirmedDescription
import cash.p.terminal.resources.StatusInfo_Failed
import cash.p.terminal.resources.StatusInfo_FailedDescription
import cash.p.terminal.resources.StatusInfo_Pending
import cash.p.terminal.resources.StatusInfo_PendingDescription
import cash.p.terminal.resources.StatusInfo_Processing
import cash.p.terminal.resources.StatusInfo_ProcessingDescription
import cash.p.terminal.resources.TransactionInfo_Status
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource

class TransactionStatusInfoPage : HSPage() {

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
                title = stringResource(Res.string.TransactionInfo_Status),
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
                InfoSubHeader(Res.string.StatusInfo_Pending)
                InfoBody(Res.string.StatusInfo_PendingDescription)
                InfoSubHeader(Res.string.StatusInfo_Processing)
                InfoBody(Res.string.StatusInfo_ProcessingDescription)
                InfoSubHeader(Res.string.StatusInfo_Confirmed)
                InfoBody(Res.string.StatusInfo_ConfirmedDescription)
                InfoSubHeader(Res.string.StatusInfo_Failed)
                InfoBody(Res.string.StatusInfo_FailedDescription)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
