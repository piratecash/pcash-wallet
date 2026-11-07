package cash.p.terminal.modules.hardwarewallet

import androidx.compose.runtime.Composable
import cash.p.terminal.ledger.di.LEDGER_USB_CONNECTOR
import cash.p.terminal.ledger.domain.LedgerConnector
import org.koin.dsl.module

val ledgerConnectorModule = module {
    single<LedgerConnector> { get(LEDGER_USB_CONNECTOR) }
}

@Composable
fun LedgerDeveloperSettings() = Unit
