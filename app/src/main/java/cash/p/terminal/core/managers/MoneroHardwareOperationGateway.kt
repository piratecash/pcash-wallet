package cash.p.terminal.core.managers

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType

/** Runs a Monero wallet2 operation while the hardware device is connected as its external signer. */
interface MoneroHardwareOperationGateway {
    /** Verifies the device belongs to [account]; [block] receives the live wallet public key. */
    suspend fun <T> execute(account: Account, block: (String) -> T): T

    /** [execute] for work nobody asked for: it never reaches the device while the app is in the background. */
    suspend fun <T> executeUnattended(account: Account, block: (String) -> T): T = execute(account, block)

    /** [executeUnattended] that gives the device up to a user operation; throws [LedgerSessionYieldedException]. */
    suspend fun <T> executeYielding(account: Account, block: (String) -> T): T = executeUnattended(account, block)
}

/** Hands each operation to the gateway of the account's device vendor. */
class MoneroDeviceOperationGateway(
    private val trezor: MoneroTrezorOperationGateway,
    private val ledger: MoneroLedgerOperationGateway,
) : MoneroHardwareOperationGateway {
    override suspend fun <T> execute(account: Account, block: (String) -> T): T =
        gatewayFor(account).execute(account, block)

    override suspend fun <T> executeUnattended(account: Account, block: (String) -> T): T =
        gatewayFor(account).executeUnattended(account, block)

    override suspend fun <T> executeYielding(account: Account, block: (String) -> T): T =
        gatewayFor(account).executeYielding(account, block)

    private fun gatewayFor(account: Account): MoneroHardwareOperationGateway =
        if (account.type is AccountType.LedgerDevice) ledger else trezor
}
