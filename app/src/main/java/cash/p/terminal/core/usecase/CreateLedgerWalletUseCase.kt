package cash.p.terminal.core.usecase

import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.ledger.domain.ICreateLedgerWalletUseCase
import cash.p.terminal.ledger.domain.LedgerAppProgress
import cash.p.terminal.ledger.domain.LedgerConnector
import cash.p.terminal.ledger.domain.LedgerDeviceId
import cash.p.terminal.ledger.domain.LedgerKeyReader
import cash.p.terminal.ledger.domain.LedgerWalletCreation
import cash.p.terminal.ledger.monero.LEDGER_MONERO_APP
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.isMonero
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException

internal class CreateLedgerWalletUseCase(
    private val keyReader: LedgerKeyReader,
    private val connector: LedgerConnector,
    private val accountFactory: IAccountFactory,
    private val accountManager: IAccountManager,
    private val accountActivator: HardwareAccountActivator,
    private val addMoneroToAccount: AddMoneroToTrezorAccountUseCase,
) : ICreateLedgerWalletUseCase {

    override suspend fun invoke(
        accountName: String,
        onOpeningApp: (LedgerAppProgress) -> Unit,
        onExportingViewKey: () -> Unit,
    ): LedgerWalletCreation {
        // The reader's closing same-device check reopens the first app, so Monero stays open for the view-key export.
        val readQueries = TokenQuery.defaultTokenQueries.sortedByDescending { it.isMonero }
        val read = keyReader.read(readQueries, expectedConnection = null, onOpeningApp)
        val keys = read.keys.sortedBy { TokenQuery.defaultTokenQueries.indexOf(it.spec.query) }
        val walletPublicKey = keys.firstOrNull()?.key
            ?: return LedgerWalletCreation(created = false, skippedApps = read.skippedApps)

        val account = accountFactory.account(
            name = accountName,
            type = AccountType.LedgerDevice(
                deviceId = LedgerDeviceId.fromWalletPublicKey(walletPublicKey),
                model = connector.model.name,
                walletPublicKey = walletPublicKey,
            ),
            origin = AccountOrigin.Created,
            backedUp = false,
            fileBackedUp = false,
        )
        val publicKeys = keys.map { it.toHardwarePublicKey(account.id) }
        val tokenQueries = keys.map { it.spec.query }
        accountManager.save(account = account, updateActive = false)
        // Monero needs the stored keys to find this Ledger, so its wallet is created after them.
        accountActivator.activate(account, tokenQueries.filterNot { it.isMonero }, publicKeys)
        val moneroSkipped = tokenQueries.any { it.isMonero } && !addMonero(account, onExportingViewKey)
        return LedgerWalletCreation(
            created = true,
            skippedApps = read.skippedApps + listOfNotNull(LEDGER_MONERO_APP.takeIf { moneroSkipped }),
        )
    }

    private suspend fun addMonero(account: Account, onExportingViewKey: () -> Unit): Boolean = try {
        onExportingViewKey()
        addMoneroToAccount(account)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.w(e) { "Failed to create the Ledger Monero wallet" }
        false
    }

    private companion object {
        val logger = Logger.withTag("CreateLedgerWalletUseCase")
    }
}
