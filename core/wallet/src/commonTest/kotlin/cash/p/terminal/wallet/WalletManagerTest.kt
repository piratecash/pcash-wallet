package cash.p.terminal.wallet

import cash.p.terminal.core.StorageLockedException
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import kotlin.test.assertEquals

class WalletManagerTest {

    private val accountStates = MutableStateFlow<ActiveAccountState>(ActiveAccountState.NotLoaded)
    private val accountManager = mockk<IAccountManager>(relaxed = true) {
        every { activeAccountStateFlow } returns accountStates
    }
    private val storage = mockk<IWalletStorage>(relaxed = true)

    @Test
    fun activeAccountStateFlow_notLoaded_forgetsTheLoadedWallets() = runBlocking {
        val manager = WalletManager(accountManager, storage)
        val wallet = mockk<Wallet>()
        coEvery { storage.wallets(account("a")) } returns listOf(wallet)
        accountStates.value = ActiveAccountState.ActiveAccount(account("a"))
        manager.awaitWallets(listOf(wallet))

        accountStates.value = ActiveAccountState.NotLoaded
        manager.awaitWallets(emptyList())

        verify(timeout = TIMEOUT) { storage.forgetLoadedWallets() }
    }

    @Test
    fun activeAccountStateFlow_storageLockedDuringReload_clearsWalletsAndKeepsLoading() =
        assertFailedReloadClearsWalletsAndKeepsLoading(StorageLockedException())

    @Test
    fun activeAccountStateFlow_databaseClosedDuringReload_clearsWalletsAndKeepsLoading() =
        assertFailedReloadClearsWalletsAndKeepsLoading(RuntimeException("Connection pool is closed"))

    @Test
    fun activeAccountStateFlow_foreignCancellationDuringReload_clearsWalletsAndKeepsLoading() =
        assertFailedReloadClearsWalletsAndKeepsLoading(CancellationException("Room scope cancelled"))

    private fun assertFailedReloadClearsWalletsAndKeepsLoading(failure: Throwable) = runBlocking {
        val manager = WalletManager(accountManager, storage)
        val first = mockk<Wallet>()
        val next = mockk<Wallet>()
        coEvery { storage.wallets(account("a")) } returns listOf(first)
        coEvery { storage.wallets(account("failing")) } throws failure
        coEvery { storage.wallets(account("b")) } returns listOf(next)
        accountStates.value = ActiveAccountState.ActiveAccount(account("a"))
        manager.awaitWallets(listOf(first))

        accountStates.value = ActiveAccountState.ActiveAccount(account("failing"))
        manager.awaitWallets(emptyList())
        verify(timeout = TIMEOUT) { storage.forgetLoadedWallets() }
        accountStates.value = ActiveAccountState.ActiveAccount(account("b"))

        manager.awaitWallets(listOf(next))
    }

    private suspend fun WalletManager.awaitWallets(expected: List<Wallet>) {
        val actual = withTimeout(TIMEOUT) { activeWalletsFlow.first { it == expected } }
        assertEquals(expected, actual)
    }

    private fun account(id: String) = Account(
        id = id, name = id, type = AccountType.EvmAddress("address-$id"),
        origin = AccountOrigin.Created, level = 0, isBackedUp = false, isFileBackedUp = false,
    )

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
