package cash.p.terminal.ledger.domain

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.HardwarePublicKey

/** A stored account key that signs only after the connected device proved it holds that key. */
class LedgerSigningKey(
    private val appAccess: LedgerAppAccess,
    private val account: Account,
    val hardwarePublicKey: HardwarePublicKey,
) {
    private val path = DerivationPath.parse(hardwarePublicKey.derivationPath)

    val deviceModel: String
        get() = (account.type as? AccountType.LedgerDevice)?.model.orEmpty()

    suspend fun <T> sign(block: suspend (LedgerTransport, DerivationPath) -> T): T =
        appAccess.signWithLedgerApp(account, hardwarePublicKey.tokenQuery) { transport -> block(transport, path) }
}
