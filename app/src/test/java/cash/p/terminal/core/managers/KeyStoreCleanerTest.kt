package cash.p.terminal.core.managers

import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.wallet.AccountDeletionBlockedException
import cash.p.terminal.wallet.AccountDeletionPreflight
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAccountCleaner
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IWalletManager
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.every
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFailsWith

class KeyStoreCleanerTest {
    private val storage = mockk<ILocalStorage>(relaxed = true)
    private val accounts = mockk<IAccountManager>(relaxed = true)
    private val wallets = mockk<IWalletManager>(relaxed = true)
    private val preflight = mockk<AccountDeletionPreflight>()
    private val artifacts = mockk<IAccountCleaner>(relaxed = true)
    private val cleaner = KeyStoreCleaner(storage, accounts, wallets, preflight, lazy { artifacts })

    @Test
    fun cleanExplicitReset_beamArtifactFailure_preservesGlobalData() = runTest {
        val account = mockk<Account> { every { id } returns "account" }
        every { accounts.accounts } returns listOf(account)
        coEvery { preflight.ensureExplicitResetCleaned() } returns Unit
        coEvery { artifacts.clearAccounts(listOf("account")) } throws AccountDeletionBlockedException()

        assertFailsWith<AccountDeletionBlockedException> { cleaner.cleanExplicitReset() }

        verify(exactly = 0) { accounts.clear() }
        verify { listOf(wallets, storage) wasNot Called }
    }

    @Test
    fun cleanExplicitReset_cleanupIncomplete_preservesGlobalData() = runTest {
        coEvery { preflight.ensureExplicitResetCleaned() } throws AccountDeletionBlockedException()
        assertFailsWith<AccountDeletionBlockedException> { cleaner.cleanExplicitReset() }
        verify { listOf(accounts, wallets, storage) wasNot Called }
    }

    @Test
    fun cleanExplicitReset_cleanupComplete_usesExplicitBarrierBeforeGlobalClear() = runTest {
        coEvery { preflight.ensureExplicitResetCleaned() } returns Unit
        cleaner.cleanExplicitReset()
        coVerifyOrder {
            preflight.ensureExplicitResetCleaned()
            accounts.clear()
            wallets.clear()
            storage.clear()
        }
    }

    @Test
    fun cleanApp_preflightBlocks_hasNoSideEffects() {
        every { preflight.ensureCanReset() } throws AccountDeletionBlockedException()

        assertFailsWith<AccountDeletionBlockedException> { cleaner.cleanApp() }

        verify { listOf(accounts, wallets, storage) wasNot Called }
    }

    @Test
    fun cleanApp_preflightAllows_clearsOnlyAfterPreflightCompletes() {
        every { preflight.ensureCanReset() } answers {
            verify { listOf(accounts, wallets, storage) wasNot Called }
        }

        cleaner.cleanApp()

        verifyOrder {
            preflight.ensureCanReset()
            accounts.clear()
            wallets.clear()
            storage.clear()
        }
    }
}
