package cash.p.terminal.core.managers

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.wallet.AccountDeletionPreflight
import cash.p.terminal.wallet.AccountDeletionBlockedException
import cash.p.terminal.wallet.IAccountCleaner
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IWalletManager
import io.horizontalsystems.core.IKeyStoreCleaner
import kotlinx.coroutines.CancellationException
import timber.log.Timber

class KeyStoreCleaner(
    private val localStorage: ILocalStorage,
    private val accountManager: IAccountManager,
    private val walletManager: IWalletManager,
    private val deletionPreflight: AccountDeletionPreflight,
    private val accountCleaner: Lazy<IAccountCleaner>,
)
    : IKeyStoreCleaner {

    override var encryptedSampleText: String?
        get() = localStorage.encryptedSampleText
        set(value) {
            localStorage.encryptedSampleText = value
        }

    override fun cleanApp() {
        // ensureCanReset() is a plain synchronous check (prefs/file reads), so the keystore's
        // synchronous contract no longer needs to bridge into a coroutine to call it.
        deletionPreflight.ensureCanReset()
        clearApp()
    }

    suspend fun cleanExplicitReset() {
        deletionPreflight.ensureExplicitResetCleaned()
        try {
            val ids = accountManager.accounts.map { it.id }
            if (ids.isNotEmpty()) accountCleaner.value.clearAccounts(ids)
        } catch (error: AccountDeletionBlockedException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.w(error, "Failed clearing account artifacts")
        }
        clearApp()
    }

    private fun clearApp() {
        accountManager.clear()
        walletManager.clear()
        localStorage.clear()
    }
}
