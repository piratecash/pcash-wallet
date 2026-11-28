package cash.p.terminal.modules.settings.displaytransactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.display_last_1_transaction
import cash.p.terminal.resources.display_last_2_transactions
import cash.p.terminal.resources.display_last_4_transactions
import cash.p.terminal.resources.display_nothing
import cash.p.terminal.resources.ic_checkmark_20
import cash.p.terminal.resources.settings_display_transactions
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.managers.TransactionDisplayLevel

@Composable
internal fun DisplayTransactionsScreen(
    selectedItem: TransactionDisplayLevel,
    onItemSelect: (TransactionDisplayLevel) -> Unit,
    onBackPress: () -> Unit
) {
    Column(
        modifier = Modifier.background(color = ComposeAppTheme.colors.backgroundBase)
    ) {
        AppBar(
            title = stringResource(Res.string.settings_display_transactions),
            navigationIcon = {
                HsBackButton(onClick = onBackPress)
            }
        )
        Column(
            Modifier.verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))
            CellUniversalLawrenceSection(TransactionDisplayLevel.entries) { item ->
                DisplayItemCell(
                    title = stringResource(item.title),
                    checked = item == selectedItem,
                    onClick = { onItemSelect(item) }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

internal val TransactionDisplayLevel.title: StringResource
    get() = when (this) {
        TransactionDisplayLevel.NOTHING -> Res.string.display_nothing
        TransactionDisplayLevel.LAST_4_TRANSACTIONS -> Res.string.display_last_4_transactions
        TransactionDisplayLevel.LAST_2_TRANSACTIONS -> Res.string.display_last_2_transactions
        TransactionDisplayLevel.LAST_1_TRANSACTION -> Res.string.display_last_1_transaction
    }

@Composable
private fun DisplayItemCell(
    title: String,
    checked: Boolean,
    onClick: () -> Unit
) {
    RowUniversal(
        onClick = onClick,
        minHeight = 48.dp
    ) {
        body_leah(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .weight(1f)
        )
        Box(
            modifier = Modifier
                .width(52.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    painter = painterResource(Res.drawable.ic_checkmark_20),
                    tint = ComposeAppTheme.colors.brandDefault,
                    contentDescription = null,
                )
            }
        }
    }
}
