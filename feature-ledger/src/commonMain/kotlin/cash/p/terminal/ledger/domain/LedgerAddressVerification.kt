package cash.p.terminal.ledger.domain

import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenQuery

/** Shows a receive address on the device screen through one Ledger app. */
interface LedgerAddressVerifier {
    fun supports(query: TokenQuery): Boolean

    /** Returns once the user confirmed [address] on the device. */
    suspend fun verify(transport: LedgerTransport, wallet: Wallet, address: String)
}

class LedgerAddressVerification(
    private val verifiers: List<LedgerAddressVerifier>,
    private val appAccess: LedgerAppAccess,
) {
    fun isAvailable(query: TokenQuery): Boolean = verifierFor(query) != null

    suspend fun verify(wallet: Wallet, address: String) {
        val query = wallet.token.tokenQuery
        val verifier = checkNotNull(verifierFor(query)) { "No Ledger address verifier for ${query.id}" }
        appAccess.withLedgerApp(wallet.account, query) { transport -> verifier.verify(transport, wallet, address) }
    }

    private fun verifierFor(query: TokenQuery) = verifiers.firstOrNull { it.supports(query) }
}
