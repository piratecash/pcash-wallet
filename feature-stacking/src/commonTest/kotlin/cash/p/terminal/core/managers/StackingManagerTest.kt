package cash.p.terminal.core.managers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.storage.AppDatabaseProvider
import cash.p.terminal.core.storage.StackingCacheDao
import cash.p.terminal.entities.StackingCache
import cash.p.terminal.network.pirate.domain.enity.InvestmentData
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.getUniqueKey
import cash.p.terminal.wallet.isCosanta
import cash.p.terminal.wallet.isPirateCash
import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class StackingManagerTest {

    private val repository = mockk<PiratePlaceRepository>()
    private val dao = mockk<StackingCacheDao>(relaxed = true)
    private val databaseProvider = mockk<AppDatabaseProvider> {
        every { database.stackingCacheDao() } returns dao
    }
    private val wallet = mockk<Wallet>()

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val dispatcherProvider = TestDispatcherProvider(dispatcher, testScope)

    private lateinit var manager: StackingManager

    companion object {
        private const val ADDRESS = "PtestAddress123"
        private const val COIN = "pirate"
        private const val WALLET_KEY = "pirate-wallet-key"
        private val BALANCE = BigDecimal("1000")
        private val NEXT_ACCRUAL = Instant.parse("2026-04-20T00:00:00Z")
        private val NEXT_PAYOUT = Instant.parse("2026-04-21T00:00:00Z")
        private val UNPAID = BigDecimal("42.5")
        private val MINT = BigDecimal("100")
    }

    @Before
    fun setup() {
        mockkStatic("cash.p.terminal.wallet.ExtensionsKt")
        every { wallet.isPirateCash() } returns true
        every { wallet.isCosanta() } returns false
        every { wallet.getUniqueKey() } returns WALLET_KEY
        coEvery { dao.get(any(), any()) } returns null

        manager = StackingManager(repository, databaseProvider, dispatcherProvider)
    }

    @After
    fun tearDown() {
        unmockkStatic("cash.p.terminal.wallet.ExtensionsKt")
    }

    @Test
    fun coldStart_noCache_unpaidIsZero() = testScope.runTest {
        coEvery { repository.getInvestmentData(any(), any(), any()) } throws RuntimeException("no network")

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        assertEquals(BigDecimal.ZERO, manager.unpaidFor(wallet))
        assertNull(manager.infoFor(wallet)?.nextAccrualAt)
    }

    @Test
    fun coldStart_hasCache_emitsCachedValues() = testScope.runTest {
        givenCache(totalIncome = MINT, nextAccrualAt = NEXT_ACCRUAL, nextPayoutAt = NEXT_PAYOUT)
        coEvery { repository.getInvestmentData(any(), any(), any()) } throws RuntimeException("no network")

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)

        val info = manager.infoFor(wallet)
        assertEquals(UNPAID, info?.unpaid)
        assertEquals(MINT, info?.totalIncome)
        assertEquals(NEXT_ACCRUAL, info?.nextAccrualAt)
        assertEquals(NEXT_PAYOUT, info?.nextPayoutAt)
    }

    @Test
    fun apiSuccess_updatesFlowsAndSavesCache() = testScope.runTest {
        val data = investmentData(
            unrealizedValue = "42.5",
            mint = "100",
            nextAccrualAt = NEXT_ACCRUAL,
            nextPayoutAt = NEXT_PAYOUT,
        )
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns data

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        val info = manager.infoFor(wallet)
        assertEquals(UNPAID, info?.unpaid)
        assertEquals(MINT, info?.totalIncome)
        assertEquals(NEXT_ACCRUAL, info?.nextAccrualAt)
        assertEquals(NEXT_PAYOUT, info?.nextPayoutAt)
        coVerify {
            dao.save(
                savedCache(
                    StackingCache(
                        coin = COIN,
                        address = ADDRESS,
                        unpaid = UNPAID,
                        totalIncome = MINT,
                        nextAccrualAt = NEXT_ACCRUAL.toEpochMilli(),
                        nextPayoutAt = NEXT_PAYOUT.toEpochMilli(),
                        cachedBalance = BALANCE,
                        timestamp = 0L,
                    )
                )
            )
        }
    }

    @Test
    fun apiError_keepsCachedValues() = testScope.runTest {
        givenCache(nextAccrualAt = NEXT_ACCRUAL)
        coEvery { repository.getInvestmentData(any(), any(), any()) } throws RuntimeException("network error")

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        val info = manager.infoFor(wallet)
        assertEquals(UNPAID, info?.unpaid)
        assertEquals(NEXT_ACCRUAL, info?.nextAccrualAt)
    }

    @Test
    fun apiError_noCache_unpaidFallsBackToZero() = testScope.runTest {
        coEvery { repository.getInvestmentData(any(), any(), any()) } throws RuntimeException("network error")

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        assertEquals(BigDecimal.ZERO, manager.unpaidFor(wallet))
    }

    @Test
    fun cacheValid_sameBalanceWithinTtl_skipsApi() = testScope.runTest {
        givenCache(nextAccrualAt = NEXT_ACCRUAL, timestamp = System.currentTimeMillis())

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.getInvestmentData(any(), any(), any()) }
        assertEquals(UNPAID, manager.unpaidFor(wallet))
    }

    @Test
    fun cacheInvalid_balanceChanged_callsApi() = testScope.runTest {
        val newBalance = BigDecimal("2000")
        givenCache(timestamp = System.currentTimeMillis())
        val data = investmentData(
            unrealizedValue = "50",
            mint = "0",
            nextAccrualAt = NEXT_ACCRUAL,
            nextPayoutAt = null,
        )
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns data

        manager.loadInvestmentData(wallet, ADDRESS, newBalance)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getInvestmentData(COIN, ADDRESS, any()) }
        assertEquals(BigDecimal("50"), manager.unpaidFor(wallet))
    }

    @Test
    fun cacheInvalid_ttlExpired_callsApi() = testScope.runTest {
        givenCache(timestamp = 0L)
        val data = investmentData(
            unrealizedValue = "42.5",
            mint = "100",
            nextAccrualAt = NEXT_ACCRUAL,
            nextPayoutAt = null,
        )
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns data

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getInvestmentData(COIN, ADDRESS, any()) }
    }

    @Test
    fun forceRefresh_sameBalanceWithinTtl_callsApi() = testScope.runTest {
        givenCache(nextAccrualAt = NEXT_ACCRUAL, timestamp = System.currentTimeMillis())
        val data = investmentData(
            unrealizedValue = "50",
            mint = "0",
            nextAccrualAt = NEXT_ACCRUAL,
            nextPayoutAt = null,
        )
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns data

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE, forceRefresh = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getInvestmentData(COIN, ADDRESS, any()) }
        assertEquals(BigDecimal("50"), manager.unpaidFor(wallet))
    }

    @Test
    fun nullBalance_alwaysCallsApi() = testScope.runTest {
        givenCache(timestamp = System.currentTimeMillis())
        val data = investmentData(
            unrealizedValue = "42.5",
            mint = "100",
            nextAccrualAt = NEXT_ACCRUAL,
            nextPayoutAt = null,
        )
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns data

        manager.loadInvestmentData(wallet, ADDRESS, null)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getInvestmentData(COIN, ADDRESS, any()) }
    }

    @Test
    fun apiReturnsNullNextAccrual_savesNull() = testScope.runTest {
        val data = investmentData(
            unrealizedValue = "5",
            mint = "0",
            nextAccrualAt = null,
            nextPayoutAt = null,
        )
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns data

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        assertNull(manager.infoFor(wallet)?.nextAccrualAt)
        coVerify {
            dao.save(
                savedCache(
                    StackingCache(
                        coin = COIN,
                        address = ADDRESS,
                        unpaid = BigDecimal("5"),
                        totalIncome = BigDecimal.ZERO,
                        nextAccrualAt = null,
                        nextPayoutAt = null,
                        cachedBalance = BALANCE,
                        timestamp = 0L,
                    )
                )
            )
        }
    }

    @Test
    fun cosantaWallet_usesCorrectCoinKey() = testScope.runTest {
        every { wallet.isPirateCash() } returns false
        every { wallet.isCosanta() } returns true
        val data = investmentData(
            unrealizedValue = "1",
            mint = "0",
            nextAccrualAt = null,
            nextPayoutAt = null,
        )
        coEvery { repository.getInvestmentData("cosa", ADDRESS, any()) } returns data

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        coVerify { repository.getInvestmentData("cosa", ADDRESS, any()) }
        coVerify { dao.save(match { it.coin == "cosa" && it.address == ADDRESS }) }
    }

    @Test
    fun malformedUnrealizedValue_doesNotCrash() = testScope.runTest {
        val data = investmentData(
            unrealizedValue = "not_a_number",
            mint = "0",
            nextAccrualAt = NEXT_ACCRUAL,
            nextPayoutAt = null,
        )
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns data

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        assertEquals(BigDecimal.ZERO, manager.unpaidFor(wallet))
        coVerify(exactly = 0) { dao.save(any()) }
    }

    @Test
    fun perWalletIsolation_pirateAndCosanta_storedSeparately() = testScope.runTest {
        val cosantaWallet = mockk<Wallet>()
        every { cosantaWallet.isPirateCash() } returns false
        every { cosantaWallet.isCosanta() } returns true
        every { cosantaWallet.getUniqueKey() } returns "cosanta-wallet-key"
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns investmentData(
            unrealizedValue = "42.5",
            mint = "10",
            nextAccrualAt = NEXT_ACCRUAL,
            nextPayoutAt = null,
        )
        coEvery { repository.getInvestmentData("cosa", "CosAddr", any()) } returns investmentData(
            unrealizedValue = "5.5",
            mint = "2",
            nextAccrualAt = null,
            nextPayoutAt = NEXT_PAYOUT,
        )

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        manager.loadInvestmentData(cosantaWallet, "CosAddr", BALANCE)
        advanceUntilIdle()

        assertEquals(BigDecimal("42.5"), manager.unpaidFor(wallet))
        assertEquals(BigDecimal("5.5"), manager.unpaidFor(cosantaWallet))
        assertNotEquals(manager.unpaidFor(wallet), manager.unpaidFor(cosantaWallet))
    }

    @Test
    fun unpaidFlow_emitsPerWalletValue() = testScope.runTest {
        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns investmentData(
            unrealizedValue = "42.5",
            mint = "0",
            nextAccrualAt = NEXT_ACCRUAL,
            nextPayoutAt = null,
        )

        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        val emitted = manager.unpaidFlow(wallet).first()
        assertEquals(UNPAID, emitted)
    }

    @Test
    fun infoFlow_unrelatedWallet_emitsNull() = testScope.runTest {
        val otherWallet = mockk<Wallet>()
        every { otherWallet.getUniqueKey() } returns "unrelated-wallet"
        every { otherWallet.isPirateCash() } returns false
        every { otherWallet.isCosanta() } returns false

        coEvery { repository.getInvestmentData(COIN, ADDRESS, any()) } returns investmentData(
            unrealizedValue = "42.5",
            mint = "0",
            nextAccrualAt = NEXT_ACCRUAL,
            nextPayoutAt = null,
        )
        manager.loadInvestmentData(wallet, ADDRESS, BALANCE)
        advanceUntilIdle()

        assertNull(manager.infoFor(otherWallet))
    }

    @Test
    fun unsupportedWallet_setsEmptyInfo() = testScope.runTest {
        val unsupported = mockk<Wallet>()
        every { unsupported.isPirateCash() } returns false
        every { unsupported.isCosanta() } returns false
        every { unsupported.getUniqueKey() } returns "unsupported"

        manager.loadInvestmentData(unsupported, ADDRESS, BALANCE)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.getInvestmentData(any(), any(), any()) }
        assertEquals(BigDecimal.ZERO, manager.unpaidFor(unsupported))
    }

    private fun givenCache(
        totalIncome: BigDecimal = BigDecimal.ZERO,
        nextAccrualAt: Instant? = null,
        nextPayoutAt: Instant? = null,
        timestamp: Long = 0L,
    ) {
        coEvery { dao.get(COIN, ADDRESS) } returns StackingCache(
            coin = COIN,
            address = ADDRESS,
            unpaid = UNPAID,
            totalIncome = totalIncome,
            nextAccrualAt = nextAccrualAt?.toEpochMilli(),
            nextPayoutAt = nextPayoutAt?.toEpochMilli(),
            cachedBalance = BALANCE,
            timestamp = timestamp,
        )
    }

    /** The save time is the wall clock; compare everything else. */
    private fun MockKMatcherScope.savedCache(expected: StackingCache) =
        match<StackingCache> { it.copy(timestamp = 0L) == expected }

    private fun investmentData(
        unrealizedValue: String,
        mint: String,
        nextAccrualAt: Instant?,
        nextPayoutAt: Instant?,
    ) = InvestmentData(
        balance = "1000",
        unrealizedValue = unrealizedValue,
        mint = mint,
        stakingActive = true,
        stakingInactiveReason = null,
        nextAccrualAt = nextAccrualAt,
        nextPayoutAt = nextPayoutAt,
    )
}
