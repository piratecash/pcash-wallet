package cash.p.terminal.modules.manageaccount.zcashkeys

import org.jetbrains.compose.resources.StringResource
import androidx.compose.runtime.getValue
import org.jetbrains.compose.resources.getString
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.adapters.zcash.ZcashPrivateKeyType
import cash.p.terminal.modules.manageaccount.ui.KeyActionItem
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.private_keys_zec
import cash.p.terminal.resources.private_keys_zec_sapling_spending_key
import cash.p.terminal.resources.private_keys_zec_sapling_spending_key_description
import cash.p.terminal.resources.private_keys_zec_sapling_spending_key_error
import cash.p.terminal.resources.private_keys_zec_transparent_key
import cash.p.terminal.resources.private_keys_zec_transparent_key_description
import cash.p.terminal.resources.private_keys_zec_transparent_key_error

@Composable
internal fun ZcashKeysScreen(
    uiState: ZcashKeysUiState,
    onReveal: (ZcashPrivateKeyType) -> Unit,
    onShowKey: () -> Unit,
    onDismissError: () -> Unit,
    onClose: () -> Unit,
) {
    val hudMessenger = rememberHudMessenger()
    val showKey by rememberUpdatedState(onShowKey)
    val dismissError by rememberUpdatedState(onDismissError)

    LaunchedEffect(uiState.revealed) {
        if (uiState.revealed != null) showKey()
    }
    LaunchedEffect(uiState.showError) {
        uiState.showError?.let { type ->
            hudMessenger.showError(getString(errorMessageRes(type)))
            dismissError()
        }
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.private_keys_zec),
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
    ZcashPrivateKeyType.Transparent -> stringResource(Res.string.private_keys_zec_transparent_key)
    ZcashPrivateKeyType.Shielded -> stringResource(Res.string.private_keys_zec_sapling_spending_key)
}

@Composable
private fun descriptionFor(type: ZcashPrivateKeyType): String = when (type) {
    ZcashPrivateKeyType.Transparent -> stringResource(Res.string.private_keys_zec_transparent_key_description)
    ZcashPrivateKeyType.Shielded -> stringResource(Res.string.private_keys_zec_sapling_spending_key_description)
}

internal fun errorMessageRes(type: ZcashPrivateKeyType): StringResource = when (type) {
    ZcashPrivateKeyType.Transparent -> Res.string.private_keys_zec_transparent_key_error
    ZcashPrivateKeyType.Shielded -> Res.string.private_keys_zec_sapling_spending_key_error
}
