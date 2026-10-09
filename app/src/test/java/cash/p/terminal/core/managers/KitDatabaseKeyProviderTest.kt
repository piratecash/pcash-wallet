package cash.p.terminal.core.managers

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import io.horizontalsystems.core.IEncryptionManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class KitDatabaseKeyProviderTest {
    private lateinit var context: Context
    private val encryptionManager = mockk<IEncryptionManager> {
        every { encrypt(any()) } answers { "$ENCRYPTED_PREFIX${firstArg<String>()}" }
        every { decrypt(any()) } answers { firstArg<String>().removePrefix(ENCRYPTED_PREFIX) }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearPreferences()
    }

    @After
    fun tearDown() {
        clearPreferences()
    }

    @Test
    fun awaitKey_bitcoinTonAndTonConnectProviders_keepSeparateKeys() = runTest {
        val bitcoinProvider = BitcoinKitDatabaseKeyProvider(context, encryptionManager)
        val tonProvider = TonKitDatabaseKeyProvider(context, encryptionManager)
        val tonConnectProvider = TonConnectDatabaseKeyProvider(context, encryptionManager)

        val bitcoinKey = bitcoinProvider.awaitKey(ACCOUNT_ID)
        val tonKey = tonProvider.awaitKey(ACCOUNT_ID)
        val tonConnectKey = tonConnectProvider.awaitKey(ACCOUNT_ID)

        assertFalse(bitcoinKey.contentEquals(tonKey))
        assertFalse(tonKey.contentEquals(tonConnectKey))
        assertTrue(bitcoinPreferences().contains(BITCOIN_PREFERENCE_KEY))
        assertTrue(tonPreferences().contains(TON_PREFERENCE_KEY))
        assertTrue(tonConnectPreferences().contains(TON_CONNECT_PREFERENCE_KEY))

        tonProvider.remove(ACCOUNT_ID)

        assertFalse(tonPreferences().contains(TON_PREFERENCE_KEY))
        assertArrayEquals(bitcoinKey, bitcoinProvider.awaitKey(ACCOUNT_ID))
        assertArrayEquals(tonConnectKey, tonConnectProvider.awaitKey(ACCOUNT_ID))

        val newTonKey = tonProvider.awaitKey(ACCOUNT_ID)
        bitcoinProvider.remove(ACCOUNT_ID)
        tonConnectProvider.remove(ACCOUNT_ID)

        assertFalse(bitcoinPreferences().contains(BITCOIN_PREFERENCE_KEY))
        assertFalse(tonConnectPreferences().contains(TON_CONNECT_PREFERENCE_KEY))
        assertArrayEquals(newTonKey, tonProvider.awaitKey(ACCOUNT_ID))
    }

    @Test
    fun awaitDatabaseKey_createThenReuseThenRemove_reportsIsNew() = runTest {
        val provider = TonConnectDatabaseKeyProvider(context, encryptionManager)

        val created = provider.awaitDatabaseKey(ACCOUNT_ID)
        val reused = provider.awaitDatabaseKey(ACCOUNT_ID)
        provider.remove(ACCOUNT_ID)
        val recreated = provider.awaitDatabaseKey(ACCOUNT_ID)

        assertTrue(created.isNew)
        assertFalse(reused.isNew)
        assertArrayEquals(created.bytes, reused.bytes)
        assertTrue(recreated.isNew)
        assertFalse(recreated.bytes.contentEquals(created.bytes))
    }

    @Test
    fun awaitKey_commitFails_throwsAndLeavesNoUnsavedKey() = runTest {
        val failingContext = FirstPutCommitFailsContext(context)
        val provider = TonKitDatabaseKeyProvider(failingContext, encryptionManager)

        assertFailsWith<KitDatabaseKeyException> { provider.awaitKey(ACCOUNT_ID) }

        assertFalse(tonPreferences().contains(TON_PREFERENCE_KEY))

        val key = provider.awaitKey(ACCOUNT_ID)

        assertArrayEquals(key, TonKitDatabaseKeyProvider(context, encryptionManager).awaitKey(ACCOUNT_ID))
    }

    @Test
    fun awaitKey_concurrentFirstCalls_returnSameStoredKey() {
        val encryption = RendezvousEncryptionManager(CONCURRENT_CALLERS)
        val provider = TonKitDatabaseKeyProvider(context, encryption)

        val keys = Executors.newFixedThreadPool(CONCURRENT_CALLERS).asCoroutineDispatcher().use { dispatcher ->
            runBlocking {
                withTimeout(TEST_TIMEOUT_MS) {
                    List(CONCURRENT_CALLERS) { async(dispatcher) { provider.awaitKey(ACCOUNT_ID) } }.awaitAll()
                }
            }
        }

        val stored = requireNotNull(tonPreferences().getString(TON_PREFERENCE_KEY, null))
        val storedKey = Base64.decode(encryption.decrypt(stored), Base64.NO_WRAP)
        keys.forEach { assertArrayEquals(storedKey, it) }
        assertEquals(1, encryption.encryptCalls.get())
    }

    private fun clearPreferences() {
        bitcoinPreferences().edit().clear().commit()
        tonPreferences().edit().clear().commit()
        tonConnectPreferences().edit().clear().commit()
    }

    private fun bitcoinPreferences() =
        context.getSharedPreferences("bitcoin_kit_database_keys", Context.MODE_PRIVATE)

    private fun tonPreferences() =
        context.getSharedPreferences("ton_kit_database_keys", Context.MODE_PRIVATE)

    private fun tonConnectPreferences() =
        context.getSharedPreferences("ton_connect_database_keys", Context.MODE_PRIVATE)

    /** Holds each encrypt() until all callers arrive (bounded), so unserialized callers all create a key. */
    private class RendezvousEncryptionManager(callers: Int) : IEncryptionManager {
        val encryptCalls = AtomicInteger()
        private val arrivals = CountDownLatch(callers)

        override fun encrypt(data: String): String {
            encryptCalls.incrementAndGet()
            arrivals.countDown()
            arrivals.await(RENDEZVOUS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            return "$ENCRYPTED_PREFIX$data"
        }

        override fun decrypt(data: String) = data.removePrefix(ENCRYPTED_PREFIX)
    }

    /** Mimics SharedPreferencesImpl: a failed commit() still leaves the edit applied in memory. */
    private class FirstPutCommitFailsContext(base: Context) : ContextWrapper(base) {
        private var failNextPutCommit = true

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val preferences = super.getSharedPreferences(name, mode)
            return object : SharedPreferences by preferences {
                override fun edit(): SharedPreferences.Editor {
                    val editor = preferences.edit()
                    var hasPut = false
                    return object : SharedPreferences.Editor by editor {
                        override fun putString(key: String, value: String?): SharedPreferences.Editor {
                            hasPut = true
                            editor.putString(key, value)
                            return this
                        }

                        override fun remove(key: String): SharedPreferences.Editor {
                            editor.remove(key)
                            return this
                        }

                        override fun commit(): Boolean {
                            val committed = editor.commit()
                            if (hasPut && failNextPutCommit) {
                                failNextPutCommit = false
                                return false
                            }
                            return committed
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val ACCOUNT_ID = "account-id"
        const val BITCOIN_PREFERENCE_KEY = "bitcoin_kit_database_key_$ACCOUNT_ID"
        const val TON_PREFERENCE_KEY = "ton_kit_database_key_$ACCOUNT_ID"
        const val TON_CONNECT_PREFERENCE_KEY = "ton_connect_database_key_$ACCOUNT_ID"
        const val ENCRYPTED_PREFIX = "encrypted:"
        const val CONCURRENT_CALLERS = 4
        const val RENDEZVOUS_TIMEOUT_MS = 500L
        const val TEST_TIMEOUT_MS = 10_000L
    }
}
