package cash.p.terminal.core.managers

import android.content.Context
import cash.p.terminal.trezorkit.transport.UsbTrezorTransport
import io.horizontalsystems.core.common.usb.usbAttachments
import io.horizontalsystems.core.common.usb.usbDetachments
import kotlinx.coroutines.flow.Flow

class UsbTrezorAttachments(context: Context) : TrezorAttachments {
    override val attached: Flow<Unit> = context.usbAttachments(UsbTrezorTransport.VENDOR_ID)
    override val detached: Flow<Unit> = context.usbDetachments(UsbTrezorTransport.VENDOR_ID)
}
