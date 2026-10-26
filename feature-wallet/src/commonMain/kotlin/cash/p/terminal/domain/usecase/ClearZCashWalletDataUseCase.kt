package cash.p.terminal.domain.usecase

import cash.p.terminal.core.adapters.zcash.session.ZcashDatabaseFiles
import cash.p.terminal.core.adapters.zcash.session.ZcashDbKeyProvider
import cash.p.terminal.core.adapters.zcash.session.ZcashSessionManager
import cash.p.terminal.core.adapters.zcash.zcashLogger
import cash.p.terminal.core.storage.ZcashSingleUseAddressStorage
import cash.p.terminal.core.tryOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ZcashEraseResult { ALL, PARTIAL, NONE }

class ClearZCashWalletDataUseCase(
    private val sessionManager: ZcashSessionManager,
    private val databaseFiles: ZcashDatabaseFiles,
    private val dbKeyProvider: ZcashDbKeyProvider,
    private val zcashSingleUseAddressStorage: ZcashSingleUseAddressStorage,
) {

    private val mutex = Mutex()

    /**
     * Erases the account's wallet. Deletion is irreversible, so the caller must distinguish
     * [ZcashEraseResult.NONE] — nothing was touched and the account is unchanged, so a rollback is
     * safe — from [ZcashEraseResult.PARTIAL]/[ZcashEraseResult.ALL], where the account has already
     * lost data and must be committed to a fresh restore rather than resumed as-is.
     */
    suspend operator fun invoke(accountId: String): ZcashEraseResult {
        mutex.withLock {
            // Nothing may be deleted while a native call can still be reading the files. A refused
            // close returns the session to service, so the account stays exactly as it was.
            if (!sessionManager.closeForErase(accountId)) return ZcashEraseResult.NONE

            // Discharges the deep-sweep obligation too: the coverage record lives in this file,
            // so deleting it is what makes the next restore sweep deep again, with nothing to
            // remember to clear.
            val database = databaseFiles.delete(accountId)
            val dbKey = tryOrNull { dbKeyProvider.drop(accountId) } == true
            val addresses = tryOrNull {
                zcashSingleUseAddressStorage.deleteAccountAddresses(accountId)
            } != null

            // Never NONE past this point: the session is closed, so this is no longer the untouched
            // account a caller may resume as-is.
            val result = if (database && dbKey && addresses) ZcashEraseResult.ALL
            else ZcashEraseResult.PARTIAL
            return result.also {
                if (it != ZcashEraseResult.ALL) {
                    zcashLogger.w {
                        "Wallet erase $it database=$database key=$dbKey addresses=$addresses"
                    }
                }
            }
        }
    }
}
