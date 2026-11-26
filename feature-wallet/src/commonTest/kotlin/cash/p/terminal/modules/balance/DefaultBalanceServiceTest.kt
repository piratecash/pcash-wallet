package cash.p.terminal.modules.balance

import cash.p.terminal.core.BalanceSettingsStorage
import cash.p.terminal.manager.IConnectivityManager
import cash.p.terminal.wallet.BalanceSortType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.models.CoinPrice
import cash.p.terminal.wallet.balance.BalanceXRateRepository
import cash.p.terminal.wallet.entities.BalanceData
import io.mockk.every
import io.mockk.mockk
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Test
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DefaultBalanceServiceTest {

    private var walletUpdates: Observable<List<Wallet>> = Observable.just(emptyList())
    private var rateUpdates: Observable<Map<String, CoinPrice?>> = Observable.never()
    private var adapterReadiness: Observable<Unit> = Observable.never()

    private val activeWalletRepository = mockk<BalanceActiveWalletRepository> {
        every { itemsObservable } answers { walletUpdates }
    }
    private val xRateRepository = mockk<BalanceXRateRepository>(relaxed = true) {
        every { itemObservable } answers { rateUpdates }
        every { getLatestRates() } returns emptyMap()
    }
    private val adapterRepository = mockk<BalanceAdapterRepository>(relaxed = true) {
        every { readyObservable } answers { adapterReadiness }
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
    fun balanceItemsFlow_ratesAndAdaptersBeforeWallets_staysNotLoaded() = runBlocking {
        walletUpdates = Observable.never()
        rateUpdates = Observable.just(emptyMap())
        adapterReadiness = Observable.just(Unit)

        service.start()
        val loaded = withTimeoutOrNull(PENDING_CHECK_MS) { service.balanceItemsFlow.first { it != null } }

        assertNull(loaded)
    }

    @Test
    fun balanceItemsFlow_adapterUpdateDuringWalletsUpdate_keepsTheNewerBalance() = runBlocking {
        val wallet = mockk<Wallet>(relaxed = true)
        val wallets = BehaviorSubject.createDefault(listOf(wallet))
        val adapterUpdates = PublishSubject.create<Wallet>()
        walletUpdates = wallets
        every { adapterRepository.updatesObservable } returns adapterUpdates
        val balance = AtomicReference(BigDecimal.ONE)
        val raceOnNextRead = AtomicBoolean(false)
        every { adapterRepository.balanceData(wallet) } answers {
            val read = balance.get()
            // The adapter update lands while this wallets update is still building its list.
            if (raceOnNextRead.compareAndSet(true, false)) {
                balance.set(BigDecimal.TEN)
                adapterUpdates.onNext(wallet)
                waitUntil { service.balanceOf(wallet) == BigDecimal.TEN }
            }
            BalanceData(read)
        }
        service.start()
        withTimeoutOrNull(TIMEOUT_MS) { service.balanceItemsFlow.first { service.balanceOf(wallet) == BigDecimal.ONE } }
        waitUntil { adapterUpdates.hasObservers() }

        raceOnNextRead.set(true)
        wallets.onNext(listOf(wallet))
        withTimeoutOrNull(TIMEOUT_MS) { service.balanceItemsFlow.first { service.balanceOf(wallet) == BigDecimal.TEN } }
        val overwritten = withTimeoutOrNull(PENDING_CHECK_MS) {
            service.balanceItemsFlow.first { service.balanceOf(wallet) == BigDecimal.ONE }
        }

        assertNull(overwritten)
        assertEquals(BigDecimal.TEN, service.balanceOf(wallet))
    }

    @Test
    fun awaitWalletsLoaded_beforeStart_staysPending() = runBlocking {
        val result = withTimeoutOrNull(PENDING_CHECK_MS) { service.awaitWalletsLoaded() }

        assertNull(result)
    }

    private fun DefaultBalanceService.balanceOf(wallet: Wallet): BigDecimal? =
        balanceItemsFlow.value?.find { it.wallet == wallet }?.balanceData?.available

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(1)
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
