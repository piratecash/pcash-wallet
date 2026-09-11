package cash.p.terminal.network.zcash.data

import co.touchlab.kermit.Logger as KermitLogger

private val logger = KermitLogger.withTag("ZEC")

internal class Logger {
    fun log(date: String, error: Throwable) {
        logger.w { "Height lookup failed date=$date error=${error::class.simpleName ?: "Unknown"}" }
    }
}
