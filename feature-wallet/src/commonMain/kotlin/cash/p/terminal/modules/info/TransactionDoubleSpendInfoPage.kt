package cash.p.terminal.modules.info

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.info.ui.InfoHeader
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.Hud_Text_Copied
import cash.p.terminal.resources.Info_DoubleSpend_ConflictingTx
import cash.p.terminal.resources.Info_DoubleSpend_Description
import cash.p.terminal.resources.Info_DoubleSpend_ThisTx
import cash.p.terminal.resources.Info_DoubleSpend_Title
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.shorten
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonSecondaryDefault
import cash.p.terminal.ui_compose.components.CellSingleLineLawrence
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

class TransactionDoubleSpendInfoPage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        InfoScreen(
            txHash = input.transactionHash,
            conflictingTxHash = input.conflictingTransactionHash,
            onBackClick = { navigation.navigateUpSafely() }
        )
    }

    @CommonParcelize
    data class Input(
        val transactionHash: String,
        val conflictingTransactionHash: String,
    ) : CommonParcelable
}

@Composable
internal fun InfoScreen(
    txHash: String,
    conflictingTxHash: String,
    onBackClick: () -> Unit
) {

    Surface(color = ComposeAppTheme.colors.backgroundBase) {
        Column {
            AppBar(
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Button_Close),
                        icon = Res.drawable.ic_close_24,
                        onClick = onBackClick
                    )
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                InfoHeader(Res.string.Info_DoubleSpend_Title)
                TextImportantWarning(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    text = stringResource(Res.string.Info_DoubleSpend_Description),
                )
                ConflictingTransactions(txHash, conflictingTxHash)
                Spacer(Modifier.height(44.dp))
            }
        }
    }
}

@Composable
fun ConflictingTransactions(transactionHash: String, conflictingHash: String) {
    Spacer(Modifier.height(12.dp))
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        TransactionHashCell(Res.string.Info_DoubleSpend_ThisTx, transactionHash)
        Divider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = ComposeAppTheme.colors.borderDivider
        )
        TransactionHashCell(Res.string.Info_DoubleSpend_ConflictingTx, conflictingHash)
    }
}

@Composable
private fun TransactionHashCell(titleRes: StringResource, transactionHash: String) {
    val hudMessenger = rememberHudMessenger()
    val copiedText = stringResource(Res.string.Hud_Text_Copied)
    val clipboardManager = LocalClipboardManager.current
    CellSingleLineLawrence() {
        Row(verticalAlignment = Alignment.CenterVertically) {
            subhead2_grey(
                text = stringResource(titleRes),
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            )
            ButtonSecondaryDefault(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = transactionHash.shorten(),
                onClick = {
                    clipboardManager.setText(AnnotatedString(transactionHash))
                    hudMessenger.showSuccess(copiedText)
                }
            )
        }
    }
}
