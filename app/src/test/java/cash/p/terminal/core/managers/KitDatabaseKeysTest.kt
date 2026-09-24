package cash.p.terminal.core.managers

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class KitDatabaseKeysTest {

    @Test
    fun awaitKey_concurrentCallsForFreshAccount_returnSameKeyAndPersistOnce() = runBlocking {
        val keyProvider = CheckThenPersistKeyProvider()
        val keys = KitDatabaseKeys(keyProvider)
        val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

        val results = dispatcher.use {
            List(2) { async(dispatcher) { keys.awaitKey(ACCOUNT_ID) } }.awaitAll()
        }

        assertEquals(1, keyProvider.persistCount.get())
        assertTrue(results[0].contentEquals(results[1]))
    }

    // Same non-atomic check-generate-persist as the real provider, with the window held open until
    // both callers have passed the check (or the timeout elapses when calls are serialized).
    private class CheckThenPersistKeyProvider : KitDatabaseKeyProvider {
        private val stored = ConcurrentHashMap<String, ByteArray>()
        private val bothChecked = CountDownLatch(2)
        val persistCount = AtomicInteger()

        override fun keyFor(accountId: String): ByteArray {
            stored[accountId]?.let { return it.copyOf() }
            bothChecked.countDown()
            bothChecked.await(RACE_WINDOW_MS, TimeUnit.MILLISECONDS)
            val key = Random.nextBytes(KEY_SIZE)
            stored[accountId] = key
            persistCount.incrementAndGet()
            return key.copyOf()
        }

        override fun remove(accountId: String) {
            stored.remove(accountId)
        }
    }

    private companion object {
        const val ACCOUNT_ID = "account-id"
        const val KEY_SIZE = 32
        const val RACE_WINDOW_MS = 500L
    }
}
