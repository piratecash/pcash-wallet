package cash.p.terminal.ledger.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ledger_verify_address
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.caption_lucian
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Wallet
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Hidden unless the wallet belongs to a Ledger and its chain can show addresses on the device. */
@Composable
fun LedgerVerifyAddressButton(wallet: Wallet, address: String) {
    if (wallet.account.type !is AccountType.LedgerDevice) return
    val viewModel = koinViewModel<LedgerVerifyAddressViewModel>(
        key = "ledger-verify:${wallet.account.id}:${wallet.token.tokenQuery.id}",
    ) { parametersOf(wallet) }
    if (!viewModel.available) return

    LedgerVerifyAddressSection(
        verifying = viewModel.verifying,
        error = viewModel.error,
        enabled = address.isNotEmpty(),
        onVerify = { viewModel.verify(address) },
    )
}

@Composable
internal fun LedgerVerifyAddressSection(
    verifying: Boolean,
    error: Throwable?,
    enabled: Boolean,
    onVerify: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        ButtonPrimaryDefault(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(Res.string.ledger_verify_address),
            enabled = enabled && !verifying,
            onClick = onVerify,
        )
        error?.let {
            caption_lucian(
                text = ledgerErrorText(it),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}
