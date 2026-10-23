package cash.p.terminal.ui.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.offline.OperationAvailability
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.TransactionInfo_From

@Suppress("UnusedPrivateMember")
@Composable
@Preview(showBackground = true)
private fun TransactionInfoTransactionHashCellPreview() {
    ComposeAppTheme {
        Column {
            Box(Modifier.width(200.dp)) {
                TransactionInfoTransactionHashCell("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")
            }
            Box {
                TransactionInfoTransactionHashCell("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview(showBackground = true)
private fun TransactionInfoBeamCancelCellPreview() {
    ComposeAppTheme {
        TransactionInfoBeamCancelCell(
            availability = OperationAvailability.Available,
            wallet = null,
            onCancel = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview(
    name = "Known address",
    locale = "ru",
    widthDp = 360,
    showBackground = true,
)
private fun TransactionInfoKnownAddressCellPreview() {
    ComposeAppTheme {
        TransactionInfoAddressCell(
            title = "${stringResource(Res.string.TransactionInfo_From)} · Token Bridge",
            value = "0x579fedB9253ccA1b3114d5e2fA44F8158d61e436",
            showAdd = false,
            blockchainType = BlockchainType.BinanceSmartChain,
            collapseAddress = true,
        )
    }
}
