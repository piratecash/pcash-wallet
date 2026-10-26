package cash.p.terminal.modules.transactions

import android.os.Build

actual val supportsBlur: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
