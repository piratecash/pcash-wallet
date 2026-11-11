package cash.p.terminal.modules.balance

import cash.p.terminal.core.BalanceSettingsStorage
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.wallet.BalanceSortType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.balance.BalanceXRateRepository
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DefaultBalanceServiceTest {

    private val activeWalletRepository = mockk<BalanceActiveWalletRepository> {
        every { itemsObservable } returns Observable.just(emptyList())
    }
    private val xRateRepository = mockk<BalanceXRateRepository>(relaxed = true) {
        every { itemObservable } returns Observable.never()
        every { getLatestRates() } returns emptyMap()
    }
    private val adapterRepository = mockk<BalanceAdapterRepository>(relaxed = true) {
        every { readyObservable } returns Observable.never()
        every { updatesObservable } returns Observable.never()
    }
    private val localStorage = mockk<BalanceSettingsStorage>(relaxed = true) {
        every { sortType } returns BalanceSortType.Value
    }
    private val accountManager = mockk<IAccountManager>(relaxed = true) {
        every { activeAccount } returns null
    }
    private val service = createService()

    @After
    fun tearDown() {
        service.close()
    }

    @Test
    fun awaitWalletsLoaded_firstUpdateWithNoWallets_completes() = runBlocking {
        service.start()

        val result = withTimeoutOrNull(TIMEOUT_MS) { service.awaitWalletsLoaded() }

        assertNotNull(result)
        assertEquals(emptyList(), service.balanceItemsFlow.value)
    }

    @Test
    fun awaitWalletsLoaded_beforeStart_staysPending() = runBlocking {
        val result = withTimeoutOrNull(PENDING_CHECK_MS) { service.awaitWalletsLoaded() }

        assertNull(result)
    }

    // The constructor is private and the factory resolves its dependencies from the global Koin graph.
    private fun createService() = DefaultBalanceService::class.java.declaredConstructors.single { it.parameterCount == CONSTRUCTOR_PARAMS }
        .apply { isAccessible = true }
        .newInstance(
            activeWalletRepository,
            xRateRepository,
            adapterRepository,
            localStorage,
            mockk<IConnectivityManager>(relaxed = true),
            BalanceSorter(),
            accountManager,
        ) as DefaultBalanceService

    private companion object {
        const val CONSTRUCTOR_PARAMS = 7
        const val TIMEOUT_MS = 5_000L
        const val PENDING_CHECK_MS = 200L
    }
}
