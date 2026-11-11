package cash.p.terminal.wallet.managers

import co.touchlab.kermit.Logger

private val logger = Logger.withTag("SessionLevel")

/** The level itself stays out of the message: it would reveal the structure of the storage. */
fun logIfBelowSessionRoot(level: Int, rootLevel: Int) {
    if (level < rootLevel) logger.w { "Level below the session root requested, using the root" }
}
