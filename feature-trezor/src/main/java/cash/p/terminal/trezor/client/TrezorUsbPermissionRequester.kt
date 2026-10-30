package cash.p.terminal.trezor.client

import android.hardware.usb.UsbDevice
import cash.p.terminal.trezorkit.transport.UsbPermissionRequester
import io.horizontalsystems.core.common.usb.AndroidUsbPermissionRequester

internal class TrezorUsbPermissionRequester(
    private val requester: AndroidUsbPermissionRequester,
) : UsbPermissionRequester {
    override suspend fun requestPermission(device: UsbDevice): Boolean = requester.requestPermission(device)
}
