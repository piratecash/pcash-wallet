package cash.p.terminal.core.adapters.zcash

import co.touchlab.kermit.Logger

internal val zcashLogger = Logger.withTag("ZEC")

internal val Throwable.zcashErrorName: String
    get() = this::class.simpleName ?: "Unknown"
