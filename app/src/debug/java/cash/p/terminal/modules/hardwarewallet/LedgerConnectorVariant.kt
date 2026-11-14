package cash.p.terminal.modules.hardwarewallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.ledger.di.LEDGER_USB_CONNECTOR
import cash.p.terminal.ledger.domain.LedgerConnector
import cash.p.terminal.ledgerkit.LedgerSession
import cash.p.terminal.ui_compose.components.InputField
import cash.p.terminal.ui_compose.components.SwitchWithText
import org.koin.android.ext.koin.androidContext
import org.koin.compose.koinInject
import org.koin.dsl.module

/** Debug builds can reach a speculos emulator instead of a USB Ledger. */
val ledgerConnectorModule = module {
    single { LedgerSpeculosSettings(androidContext()) }
    single<LedgerConnector> { SpeculosSwitchingLedgerConnector(get(LEDGER_USB_CONNECTOR), get()) }
}

@Composable
fun LedgerDeveloperSettings() {
    val settings = koinInject<LedgerSpeculosSettings>()
    val session = koinInject<LedgerSession>()
    var enabled by remember { mutableStateOf(settings.enabled) }
    var address by remember { mutableStateOf(settings.address) }

    Column(modifier = Modifier.padding(top = 16.dp)) {
        SwitchWithText(
            text = stringResource(R.string.ledger_speculos_switch),
            checked = enabled,
            onCheckedChange = {
                enabled = it
                settings.enabled = it
                // The open transport belongs to the previous mode.
                session.close()
            },
        )
        InputField(
            value = address,
            onValueChange = {
                address = it
                settings.address = it
            },
            placeholderText = stringResource(R.string.ledger_speculos_address),
            modifier = Modifier.padding(horizontal = 16.dp),
            keyboardType = KeyboardType.Uri,
        )
    }
}
