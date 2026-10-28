package cash.p.terminal.modules.send.bitcoin.advanced

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
import cash.p.terminal.modules.info.ui.InfoHeader
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.BtcBlockchainSettings_TransactionInputsOutputs
import cash.p.terminal.resources.BtcBlockchainSettings_TransactionInputsOutputsDescription
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun BtcTransactionInputSortInfoScreen(
    onCloseClick: () -> Unit
) {
    ComposeAppTheme {
        Surface(color = ComposeAppTheme.colors.backgroundBase) {
            Column {
                AppBar(
                    menuItems = listOf(
                        MenuItem(
                            title = TranslatableString.ResString(Res.string.Button_Close),
                            icon = Res.drawable.ic_close_24,
                            onClick = onCloseClick
                        )
                    )
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    InfoHeader(Res.string.BtcBlockchainSettings_TransactionInputsOutputs)
                    InfoBody(Res.string.BtcBlockchainSettings_TransactionInputsOutputsDescription)
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}
