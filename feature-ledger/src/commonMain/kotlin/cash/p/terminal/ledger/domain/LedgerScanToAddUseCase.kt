package cash.p.terminal.ledger.domain

import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.useCases.ScanToAddUseCase
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext

class LedgerScanToAddUseCase(
    private val accountManager: IAccountManager,
    private val appAccess: LedgerAppAccess,
    private val keyReader: LedgerKeyReader,
    private val hardwarePublicKeyStorage: IHardwarePublicKeyStorage,
    private val dispatcherProvider: DispatcherProvider,
    private val userOperations: LedgerUserOperations,
) : ScanToAddUseCase {

    override suspend fun addTokensByScan(
        blockchainsToDerive: List<TokenQuery>,
        cardId: String,
        accountId: String,
    ): Boolean = scan(blockchainsToDerive, accountId).keys.size == blockchainsToDerive.size

    suspend fun scan(blockchainsToDerive: List<TokenQuery>, accountId: String): LedgerKeyRead = userOperations.run {
        val account = checkNotNull(accountManager.account(accountId)) { "Account not found" }
        val read = try {
            readProven(account, blockchainsToDerive)
        } catch (_: LedgerConnectionChangedException) {
            appAccess.forgetProof(accountId)
            try {
                readProven(account, blockchainsToDerive)
            } catch (error: LedgerConnectionChangedException) {
                // The device keeps reconnecting: it is being re-plugged, as when unplugged mid-read.
                throw LedgerException.TransportClosed(error)
            }
        }
        withContext(dispatcherProvider.io) {
            hardwarePublicKeyStorage.save(read.keys.map { it.toHardwarePublicKey(accountId) })
        }
        read
    }

    private suspend fun readProven(account: Account, queries: List<TokenQuery>): LedgerKeyRead {
        val proof = appAccess.verifyIdentity(account, queries)
        // Starting in the proven app skips one switch and ties every later app back to the proven device.
        val ordered = proof.app?.let { app -> queries.sortedBy { appAccess.appNameFor(it) != app } } ?: queries
        return try {
            keyReader.read(ordered, proof.connectionId) {}
        } catch (error: LedgerIdentityMismatchException) {
            // Another device answered on the proven connection, so the proof no longer holds.
            appAccess.forgetProof(account.id)
            throw error
        }
    }
}
