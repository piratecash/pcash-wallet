package cash.p.terminal.core.managers

import io.horizontalsystems.bitcoincore.BitcoinCore
import io.horizontalsystems.hdwalletkit.HDWallet
import io.horizontalsystems.litecoinkit.LitecoinKit
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LitecoinKitPoolTest {

    private val pool = LitecoinKitPool()
    private val key = kitKey(mwebEnabled = true)

    @Test
    fun acquire_sameKeyConcurrently_createsOneKitForAll() {
        val threads = 8
        val createCount = AtomicInteger()
        val startGate = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threads)
        try {
            val futures = List(threads) {
                executor.submit<LitecoinKit> {
                    startGate.await(5, TimeUnit.SECONDS)
                    pool.acquire(key) {
                        createCount.incrementAndGet()
                        // Widens the window in which a non-atomic acquire would build a second kit.
                        Thread.sleep(50)
                        mockk(relaxed = true)
                    }
                }
            }
            startGate.countDown()

            val kits = futures.map { it.get(5, TimeUnit.SECONDS) }

            assertEquals(1, createCount.get())
            kits.forEach { assertSame(kits.first(), it) }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun acquire_differentKey_createsSeparateKit() {
        val first = pool.acquire(key) { mockk(relaxed = true) }
        val second = pool.acquire(kitKey(mwebEnabled = false)) { mockk(relaxed = true) }

        assertNotSame(first, second)
    }

    @Test
    fun release_oneOfTwoHolders_keepsKitShared() {
        val kit = pool.acquire(key) { mockk(relaxed = true) }
        pool.acquire(key) { error("kit must be reused") }

        pool.release(kit)

        verify(exactly = 0) { kit.dispose() }
        assertSame(kit, pool.acquire(key) { error("kit must be reused") })
    }

    @Test
    fun release_lastHolder_disposesKitExactlyOnce() {
        val kit = pool.acquire(key) { mockk(relaxed = true) }
        pool.acquire(key) { error("kit must be reused") }

        pool.release(kit)
        pool.release(kit)
        pool.release(kit)

        verify(exactly = 1) { kit.dispose() }
        val next = mockk<LitecoinKit>(relaxed = true)
        assertSame(next, pool.acquire(key) { next })
    }

    private fun kitKey(mwebEnabled: Boolean) = LitecoinKitKey(
        accountId = "account",
        syncModeClass = BitcoinCore.SyncMode.Api::class,
        purpose = HDWallet.Purpose.BIP84,
        mwebEnabled = mwebEnabled,
    )
}
