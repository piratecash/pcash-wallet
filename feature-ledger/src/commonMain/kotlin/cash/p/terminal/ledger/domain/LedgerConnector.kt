package cash.p.terminal.ledger.domain

import cash.p.terminal.ledgerkit.device.LedgerModel
import cash.p.terminal.ledgerkit.transport.LedgerTransportProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Platform link to the Ledger: opens transports and reports what it learned while doing so. */
interface LedgerConnector : LedgerTransportProvider {
    val awaitingPermission: StateFlow<Boolean>

    /** Whether a device can be reached now, without opening it or asking for permission. */
    val isDevicePresent: Boolean

    /** Model of the device the last transport was opened to. */
    val model: LedgerModel
}

/** Fires when a Ledger is plugged in, so work that failed without it can retry. */
interface LedgerAttachments {
    val attached: Flow<Unit>
}
