package cash.p.terminal.ledger.usb

import android.content.Context
import cash.p.terminal.ledger.domain.LedgerAttachments
import cash.p.terminal.ledgerkit.device.LedgerModel
import io.horizontalsystems.core.common.usb.usbAttachments
import kotlinx.coroutines.flow.Flow

class UsbLedgerAttachments(context: Context) : LedgerAttachments {
    override val attached: Flow<Unit> = context.usbAttachments(LedgerModel.VENDOR_ID)
}
