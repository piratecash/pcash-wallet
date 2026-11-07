package cash.p.terminal.ledger.domain

import cash.p.terminal.ledger.bitcoin.LedgerBtcApp

/** Oldest device app versions the wallet works with; apps not listed accept any version. */
object LedgerAppRequirements {
    const val ZCASH_APP = "Zcash"

    private const val ANY_VERSION = "0"

    // Bitcoin 2.1.0 is the first app speaking the wallet-policy protocol.
    private val minVersions = mapOf(ZCASH_APP to "3.9.4", LedgerBtcApp.Bitcoin.appName to "2.1.0")

    fun minVersion(appName: String): String = minVersions[appName] ?: ANY_VERSION
}
