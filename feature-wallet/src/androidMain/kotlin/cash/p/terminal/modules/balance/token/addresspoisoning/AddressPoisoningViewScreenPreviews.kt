package cash.p.terminal.modules.balance.token.addresspoisoning

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.modules.transactions.TransactionViewItem
import cash.p.terminal.modules.transactions.poison_status.PoisonStatus
import cash.p.terminal.ui_compose.ColorName
import cash.p.terminal.ui_compose.ColoredValue
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import java.util.Date

@Suppress("UnusedPrivateMember")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AddressPoisoningViewScreenPreview() {
    val mockItem = TransactionViewItem(
        uid = "preview",
        progress = null,
        title = "Sent",
        subtitle = "to xxxxxxxxxxxx...xxxx...xxxxxxxxxx",
        primaryValue = ColoredValue("-1 ETH", ColorName.Lucian),
        secondaryValue = ColoredValue("$3,198", ColorName.Secondary),
        date = Date(),
        formattedTime = "14:30",
        icon = TransactionViewItem.Icon.Failed,
        poisonStatus = PoisonStatus.CREATED,
        addressPoisoningViewMode = AddressPoisoningViewMode.STANDARD,
    )
    ComposeAppTheme {
        AddressPoisoningViewScreen(
            uiState = AddressPoisoningViewUiState(
                selectedMode = AddressPoisoningViewMode.STANDARD,
                standardItem = mockItem,
                compactItem = mockItem.copy(
                    uid = "preview_compact",
                    addressPoisoningViewMode = AddressPoisoningViewMode.COMPACT,
                ),
            ),
            onSelect = {},
            onClose = {},
        )
    }
}
