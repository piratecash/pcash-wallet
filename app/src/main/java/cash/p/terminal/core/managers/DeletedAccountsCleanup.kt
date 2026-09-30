package cash.p.terminal.core.managers

import cash.p.terminal.wallet.AccountDeletionBlockedException
import cash.p.terminal.wallet.AccountDeletionPreflight
import cash.p.terminal.wallet.IAccountCleaner
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAccountsStorage
import timber.log.Timber

class DeletedAccountsCleanup(
    private val accountManager: IAccountManager,
    private val accountsStorage: IAccountsStorage,
    private val restoreSettingsManager: RestoreSettingsManager,
    private val accountCleaner: IAccountCleaner,
    private val deletionPreflight: AccountDeletionPreflight,
) {
    suspend operator fun invoke() {
        try {
            deletionPreflight.resumePendingReset()
        } catch (_: AccountDeletionBlockedException) {
            // App.clearDeletedAccounts() launches this without a CoroutineExceptionHandler, so an
            // escaping exception would crash the process on every start until the leftover is gone.
            // Cancellation is not an AccountDeletionBlockedException and still propagates.
            Timber.w("BEAM reset cleanup incomplete; marker retained for the next start")
        }
        val ids = accountManager.getDeletedAccountIds()
        val deletedAccounts = ids.mapNotNull(accountsStorage::loadAccount)
        restoreSettingsManager.backfillTrezorMoneroRestoreHeights(accountManager.accounts + deletedAccounts)
        for (id in ids) {
            try {
                deletionPreflight.ensureCanDelete(listOf(id))
                accountCleaner.clearAccounts(listOf(id))
                accountManager.clearDeleted(listOf(id))
            } catch (_: AccountDeletionBlockedException) {
                // Keep only this tombstone; unrelated deleted accounts can still be cleaned.
                Timber.w("Deleted account cleanup incomplete; retry marker retained")
            }
        }
    }
}
