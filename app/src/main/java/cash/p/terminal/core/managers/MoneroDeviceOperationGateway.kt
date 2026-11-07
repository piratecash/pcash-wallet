package cash.p.terminal.core.managers

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType

/** Hands each operation to the gateway of the account's device vendor. */
class MoneroDeviceOperationGateway(
    private val trezor: MoneroTrezorOperationGateway,
    private val ledger: MoneroLedgerOperationGateway,
) : MoneroHardwareGateway {
    override suspend fun <T> execute(account: Account, block: (String) -> T): T =
        gatewayFor(account).execute(account, block)

    override suspend fun <T> executeUnattended(account: Account, block: (String) -> T): T =
        gatewayFor(account).executeUnattended(account, block)

    override suspend fun <T> executeYielding(account: Account, block: (String) -> T): T =
        gatewayFor(account).executeYielding(account, block)

    private fun gatewayFor(account: Account): MoneroHardwareGateway =
        if (account.type is AccountType.LedgerDevice) ledger else trezor
}
