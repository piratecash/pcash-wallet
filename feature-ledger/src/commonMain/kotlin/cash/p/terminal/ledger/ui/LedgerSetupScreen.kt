package cash.p.terminal.ledger.ui

import androidx.compose.runtime.Composable
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.connect_ledger
import cash.p.terminal.resources.ledger_monero_confirm_view_key
import cash.p.terminal.resources.ledger_not_added_description
import cash.p.terminal.resources.ledger_not_added_title
import cash.p.terminal.resources.ledger_opening_app
import cash.p.terminal.resources.ledger_setup_description
import cash.p.terminal.resources.ledger_usb_permission
import cash.p.terminal.resources.ledger_wallet
import cash.p.terminal.ui_compose.components.InfoBottomSheet
import cash.p.terminal.ui_compose.components.hardware.HardwareSetupScreen
import org.jetbrains.compose.resources.stringResource

@Composable
fun LedgerSetupScreen(
    uiState: LedgerSetupUiState,
    onConnect: () -> Unit,
    onDismissSkippedApps: () -> Unit,
    onBack: () -> Unit,
) {
    HardwareSetupScreen(
        title = stringResource(Res.string.ledger_wallet),
        description = stringResource(Res.string.ledger_setup_description),
        connectButtonTitle = stringResource(Res.string.connect_ledger),
        loading = uiState.loading,
        onConnect = onConnect,
        onBack = onBack,
        statusText = statusText(uiState),
        errorText = uiState.error?.let { ledgerErrorText(it) },
    )

    if (uiState.skippedApps.isNotEmpty()) {
        InfoBottomSheet(
            title = stringResource(Res.string.ledger_not_added_title),
            text = stringResource(Res.string.ledger_not_added_description, uiState.skippedApps.joinToString()),
            onDismiss = onDismissSkippedApps,
        )
    }
}

@Composable
private fun statusText(uiState: LedgerSetupUiState): String? = when {
    uiState.awaitingPermission -> stringResource(Res.string.ledger_usb_permission)
    uiState.exportingViewKey -> stringResource(Res.string.ledger_monero_confirm_view_key)
    uiState.progress != null -> with(uiState.progress) {
        stringResource(Res.string.ledger_opening_app, index, total, appName)
    }
    else -> null
}
