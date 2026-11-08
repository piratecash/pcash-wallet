package cash.p.terminal.wallet

import io.horizontalsystems.core.DefaultDispatcherProvider
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.useCases.IGetMoneroWalletFilesNameUseCase
import cash.p.terminal.wallet.useCases.IRemoveMoneroWalletFilesUseCase
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.every
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountManagerBackupFlowTest {

    @Test
    fun setLevel_pendingBackupAccountNotVisibleThere_clearsBackupNotification() = runTest {
        val account = pendingBackupAccount()
        val storage = mockk<IAccountsStorage>(relaxed = true) {
            every { allAccounts(1) } returns emptyList()
        }
        val accountManager = accountManager(storage)
        accountManager.save(account, updateActive = false)

        accountManager.setLevel(1)

        assertNull(accountManager.newAccountBackupRequiredFlow.value)
    }

    @Test
    fun setLevel_pendingBackupAccountVisibleThere_keepsBackupNotification() = runTest {
        val account = pendingBackupAccount()
        val storage = mockk<IAccountsStorage>(relaxed = true) {
            every { allAccounts(0) } returns listOf(account)
        }
        val accountManager = accountManager(storage)
        accountManager.save(account, updateActive = false)

        accountManager.setLevel(0)

        assertEquals(account, accountManager.newAccountBackupRequiredFlow.value)
    }

    @Test
    fun delete_pendingBackupAccount_clearsBackupNotification() = runTest {
        val account = pendingBackupAccount()
        val storage = mockk<IAccountsStorage>(relaxed = true) {
            every { loadAccount(account.id) } returns account
        }
        val accountManager = accountManager(storage)
        accountManager.save(account, updateActive = false)
        assertEquals(account, accountManager.newAccountBackupRequiredFlow.value)

        accountManager.delete(account.id)

        assertNull(accountManager.newAccountBackupRequiredFlow.value)
    }

    private fun pendingBackupAccount() = Account(
        id = "account-id",
        name = "Trezor",
        type = AccountType.TrezorDevice(
            deviceId = "device-id",
            model = "T3T1",
            firmwareVersion = "2.8.10",
            walletPublicKey = "wallet-public-key",
        ),
        origin = AccountOrigin.Created,
        level = 0,
        isBackedUp = false,
        isFileBackedUp = false,
    )

    private fun accountManager(storage: IAccountsStorage): AccountManager {
        val getMoneroWalletFilesNameUseCase = mockk<IGetMoneroWalletFilesNameUseCase>()
        coEvery { getMoneroWalletFilesNameUseCase(any()) } returns null
        return AccountManager(
            storage = storage,
            getMoneroWalletFilesNameUseCase = getMoneroWalletFilesNameUseCase,
            removeMoneroWalletFilesUseCase = mockk<IRemoveMoneroWalletFilesUseCase>(relaxed = true),
            balanceHiddenManager = mockk<IBalanceHiddenManager>(relaxed = true),
            deletionPreflight = mockk(relaxed = true),
            dispatcherProvider = DefaultDispatcherProvider(),
        )
    }
}
