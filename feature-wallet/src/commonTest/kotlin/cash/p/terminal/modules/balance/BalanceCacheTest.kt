package cash.p.terminal.modules.balance

import cash.p.terminal.core.storage.EnabledWalletsCacheDao
import cash.p.terminal.core.storage.SingleDatabaseStorageRouter
import cash.p.terminal.core.storage.StaticAppDatabaseProvider
import cash.p.terminal.entities.EnabledWalletCache
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.BalanceData
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BalanceCacheTest {

    private fun routerWith(dao: EnabledWalletsCacheDao) =
        SingleDatabaseStorageRouter(StaticAppDatabaseProvider(mockk { every { enabledWalletsCacheDao() } returns dao }))

    @Test
    fun getCache_cachedTotalBalance_restoresAsAvailable() {
        val wallet = wallet()
        val dao = mockk<EnabledWalletsCacheDao> {
            every { getAll() } returns listOf(
                EnabledWalletCache(
                    tokenQueryId = wallet.token.tokenQuery.id,
                    accountId = wallet.account.id,
                    balance = BigDecimal("3"),
                    balanceLocked = BigDecimal.ZERO,
                    stackingUnpaid = BigDecimal.ZERO
                )
            )
        }

        val cached = BalanceCache(routerWith(dao)).getCache(wallet)

        assertBigDecimalEquals("3", cached?.available)
        assertBigDecimalEquals("0", cached?.pending)
        assertBigDecimalEquals("3", cached?.total)
    }

    @Test
    fun setCache_pendingBalance_persistsColdTotalInBalance() {
        val wallet = wallet()
        val capturedCache = slot<List<EnabledWalletCache>>()
        val dao = mockk<EnabledWalletsCacheDao> {
            every { getAll() } returns emptyList()
            every { insertAll(capture(capturedCache)) } returns Unit
        }

        BalanceCache(routerWith(dao)).setCache(
            wallet,
            BalanceData(
                available = BigDecimal("1"),
                pending = BigDecimal("2")
            )
        )

        val saved = capturedCache.captured.single()
        assertBigDecimalEquals("3", saved.balance)
    }

    @Test
    fun constructor_noLookup_doesNotReadDatabase() {
        val dao = mockk<EnabledWalletsCacheDao>(relaxed = true)

        BalanceCache(routerWith(dao))

        verify(exactly = 0) { dao.getAll() }
    }

    @Test
    fun getCache_repeatedLookups_readsDatabaseOnce() {
        val dao = mockk<EnabledWalletsCacheDao> { every { getAll() } returns emptyList() }
        val cache = BalanceCache(routerWith(dao))

        cache.getCache(wallet("account-1"))
        cache.getCache(wallet("account-2"))

        verify(exactly = 1) { dao.getAll() }
    }

    @Test
    fun getCache_storedAndUpdatedBalance_returnsUpdated() {
        val wallet = wallet()
        val dao = mockk<EnabledWalletsCacheDao>(relaxed = true) {
            every { getAll() } returns listOf(cacheRow(wallet, "3"))
        }
        val cache = BalanceCache(routerWith(dao))
        assertBigDecimalEquals("3", cache.getCache(wallet)?.available)

        cache.setCache(wallet, BalanceData(available = BigDecimal("5")))

        assertBigDecimalEquals("5", cache.getCache(wallet)?.available)
    }

    @Test
    fun setCache_concurrentWritesOfDifferentWallets_keepsEveryWallet() {
        val dao = mockk<EnabledWalletsCacheDao>(relaxed = true) { every { getAll() } returns emptyList() }
        val cache = BalanceCache(routerWith(dao))
        val wallets = List(WRITERS * WRITES_PER_THREAD) { wallet("account-$it") }
        val start = CyclicBarrier(WRITERS)
        val writers = wallets.chunked(WRITES_PER_THREAD).map { chunk ->
            Callable {
                start.await()
                chunk.forEach { cache.setCache(it, BalanceData(available = BigDecimal.ONE)) }
            }
        }

        val pool = Executors.newFixedThreadPool(WRITERS)
        try {
            pool.invokeAll(writers, WRITERS_TIMEOUT_SECONDS, TimeUnit.SECONDS).forEach { it.get() }
        } finally {
            pool.shutdownNow()
        }

        assertEquals(wallets.size, wallets.count { cache.getCache(it) != null })
    }

    private fun cacheRow(wallet: Wallet, balance: String) = EnabledWalletCache(
        tokenQueryId = wallet.token.tokenQuery.id,
        accountId = wallet.account.id,
        balance = BigDecimal(balance),
        balanceLocked = BigDecimal.ZERO,
        stackingUnpaid = BigDecimal.ZERO
    )

    private fun wallet(accountId: String = "account-1"): Wallet {
        val account = mockk<Account> {
            every { id } returns accountId
        }
        val token = Token(
            coin = Coin(uid = "zcash", name = "Zcash", code = "ZEC"),
            blockchain = Blockchain(BlockchainType.Zcash, "Zcash", null),
            type = TokenType.AddressSpecTyped(TokenType.AddressSpecType.Shielded),
            decimals = 8
        )
        return mockk {
            every { this@mockk.account } returns account
            every { this@mockk.token } returns token
        }
    }

    private fun assertBigDecimalEquals(expected: String, actual: BigDecimal?) {
        assertEquals(BigDecimal(expected).stripTrailingZeros(), actual?.stripTrailingZeros())
    }

    private companion object {
        const val WRITERS = 8
        const val WRITES_PER_THREAD = 200
        const val WRITERS_TIMEOUT_SECONDS = 10L
    }
}
