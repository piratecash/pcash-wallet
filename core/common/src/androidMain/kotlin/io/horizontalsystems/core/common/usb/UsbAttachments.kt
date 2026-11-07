package io.horizontalsystems.core.common.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Emits each time a USB device of [vendorId] is plugged in, while collected. */
fun Context.usbAttachments(vendorId: Int): Flow<Unit> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.usbDevice()?.vendorId == vendorId) trySend(Unit)
        }
    }
    ContextCompat.registerReceiver(
        this@usbAttachments,
        receiver,
        IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED),
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    awaitClose { unregisterReceiver(receiver) }
}
