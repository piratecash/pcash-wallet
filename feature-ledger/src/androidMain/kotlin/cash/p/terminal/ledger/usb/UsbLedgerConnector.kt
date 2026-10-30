package cash.p.terminal.ledger.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import cash.p.terminal.ledger.domain.LedgerConnector
import cash.p.terminal.ledger.domain.LedgerNotFoundException
import cash.p.terminal.ledger.domain.LedgerUsbPermissionDeniedException
import cash.p.terminal.ledgerkit.device.LedgerModel
import cash.p.terminal.ledgerkit.transport.AndroidUsbLedger
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import io.horizontalsystems.core.common.usb.AndroidUsbPermissionRequester
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Looks the Ledger up on every open: it re-enumerates as a new USB device after each app switch. */
class UsbLedgerConnector(
    private val usbManager: UsbManager,
    private val permissionRequester: AndroidUsbPermissionRequester,
) : LedgerConnector {

    private val _awaitingPermission = MutableStateFlow(false)
    override val awaitingPermission: StateFlow<Boolean> = _awaitingPermission.asStateFlow()

    override val isDevicePresent: Boolean get() = ledgerDevice() != null

    @Volatile
    override var model: LedgerModel = LedgerModel.Unknown
        private set

    override suspend fun open(): LedgerTransport {
        val device = ledgerDevice() ?: throw LedgerNotFoundException()
        if (!usbManager.hasPermission(device) && !requestPermission(device)) {
            throw LedgerUsbPermissionDeniedException()
        }
        model = LedgerModel.fromProductId(device.productId)
        return AndroidUsbLedger.open(usbManager, device)
    }

    private fun ledgerDevice(): UsbDevice? =
        usbManager.deviceList.values.firstOrNull { it.vendorId == LedgerModel.VENDOR_ID }

    private suspend fun requestPermission(device: UsbDevice): Boolean {
        _awaitingPermission.value = true
        return try {
            permissionRequester.requestPermission(device)
        } finally {
            _awaitingPermission.value = false
        }
    }
}
