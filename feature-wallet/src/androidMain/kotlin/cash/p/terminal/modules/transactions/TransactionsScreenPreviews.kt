package cash.p.terminal.modules.transactions

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.modules.transactions.poison_status.PoisonStatus
import cash.p.terminal.ui_compose.ColorName
import cash.p.terminal.ui_compose.ColoredValue
import java.util.Date

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun TransactionCellPreview() {
    TransactionCellPreviewContent(
        item = TransactionViewItem(
            uid = "uid",
            progress = null,
            title = "Received Bitcoin",
            subtitle = "From Alice to My Bitcoin Wallet",
            icon = TransactionViewItem.Icon.Failed,
            doubleSpend = true,
            locked = true,
            sentToSelf = true,
            primaryValue = ColoredValue("0.00123 BTC", ColorName.Primary),
            secondaryValue = ColoredValue("$45.67", ColorName.Primary),
            date = Date(),
            formattedTime = "12:00",
            amlStatus = AmlStatus.Low
        )
    )
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Failed Swap Cropped", widthDp = 300, showBackground = true)
@Composable
private fun FailedSwapTransactionCellPreview() {
    TransactionCellPreviewContent(
        item = TransactionViewItem(
            uid = "swap-failed",
            progress = null,
            title = "Обмен не выполнен",
            subtitle = "PancakeSwap",
            icon = TransactionViewItem.Icon.Failed,
            primaryValue = ColoredValue("-125.45 USDT", ColorName.Lucian),
            secondaryValue = ColoredValue("+0.0523 BNB", ColorName.Remus),
            date = Date(),
            formattedTime = "17:49",
            poisonStatus = PoisonStatus.BLOCKCHAIN
        )
    )
}
