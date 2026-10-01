package cash.p.terminal.modules.manageaccount.zcashkeys

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.core.adapters.zcash.ZcashPrivateKeyType
import cash.p.terminal.modules.manageaccount.ui.KeyActionItem
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
internal fun ZcashKeysScreen(
    uiState: ZcashKeysUiState,
    onReveal: (ZcashPrivateKeyType) -> Unit,
    onShowKey: () -> Unit,
    onDismissError: () -> Unit,
    onClose: () -> Unit,
) {
    val view = LocalView.current
    val showKey by rememberUpdatedState(onShowKey)
    val dismissError by rememberUpdatedState(onDismissError)

    LaunchedEffect(uiState.revealed) {
        if (uiState.revealed != null) showKey()
    }
    LaunchedEffect(uiState.showError) {
        uiState.showError?.let { type ->
            HudHelper.showErrorMessage(view, errorMessageRes(type))
            dismissError()
        }
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.private_keys_zec),
                navigationIcon = { HsBackButton(onClick = onClose) },
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            Spacer(Modifier.height(12.dp))
            uiState.available.forEach { type ->
                KeyActionItem(
                    title = titleFor(type),
                    description = descriptionFor(type),
                ) {
                    onReveal(type)
                }
            }
        }
    }
}

@Composable
internal fun titleFor(type: ZcashPrivateKeyType): String = when (type) {
    ZcashPrivateKeyType.Transparent -> stringResource(R.string.private_keys_zec_transparent_key)
    ZcashPrivateKeyType.Shielded -> stringResource(R.string.private_keys_zec_sapling_spending_key)
}

@Composable
private fun descriptionFor(type: ZcashPrivateKeyType): String = when (type) {
    ZcashPrivateKeyType.Transparent -> stringResource(R.string.private_keys_zec_transparent_key_description)
    ZcashPrivateKeyType.Shielded -> stringResource(R.string.private_keys_zec_sapling_spending_key_description)
}

internal fun errorMessageRes(type: ZcashPrivateKeyType): Int = when (type) {
    ZcashPrivateKeyType.Transparent -> R.string.private_keys_zec_transparent_key_error
    ZcashPrivateKeyType.Shielded -> R.string.private_keys_zec_sapling_spending_key_error
}

@Preview
@Composable
private fun ZcashKeysScreenPreview() {
    ComposeAppTheme {
        ZcashKeysScreen(
            uiState = ZcashKeysUiState(available = ZcashPrivateKeyType.entries),
            onReveal = {},
            onShowKey = {},
            onDismissError = {},
            onClose = {},
        )
    }
}
