package io.horizontalsystems.core.common.usb

import android.app.Activity
import android.os.Bundle

/** Lets the user pick the wallet as the default app for a USB device; it only has to exist. */
open class UsbAttachActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
