package io.horizontalsystems.core.common.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Emits each time a USB device of [vendorId] is plugged in, while collected. */
fun Context.usbAttachments(vendorId: Int): Flow<Unit> =
    usbDeviceEvents(UsbManager.ACTION_USB_DEVICE_ATTACHED, vendorId)

/** Emits each time a USB device of [vendorId] is unplugged, while collected. */
fun Context.usbDetachments(vendorId: Int): Flow<Unit> =
    usbDeviceEvents(UsbManager.ACTION_USB_DEVICE_DETACHED, vendorId)

private fun Context.usbDeviceEvents(action: String, vendorId: Int): Flow<Unit> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.usbDevice()?.vendorId == vendorId) trySend(Unit)
        }
    }
    val filter = IntentFilter(action)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        registerReceiver(receiver, filter)
    }
    awaitClose { unregisterReceiver(receiver) }
}
