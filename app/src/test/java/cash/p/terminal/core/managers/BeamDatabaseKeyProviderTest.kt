package cash.p.terminal.core.managers

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import io.horizontalsystems.core.IEncryptionManager
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.BadPaddingException
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [27])
class BeamDatabaseKeyProviderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val locator = BeamStorageLocator(context)
    private val preferences = context.getSharedPreferences("beam_database_keys", Context.MODE_PRIVATE)
    private val encryption = FakeEncryption()
    private val id = locator.storageId(ACCOUNT, BeamNetwork.Mainnet)

    @Before
    fun setUp() = clearStorage()

    @After
    fun tearDown() = clearStorage()

    @Test
    fun keyFor_absentDatabaseAndWrapper_persistsBeforeReturningAndReuses() {
        val key = provider().keyFor(ACCOUNT)
        assertEquals(32, key.size)
        assertEquals(1, encryption.encryptions)
        assertTrue(preferences.contains(id))
        assertNotEquals(Base64.encodeToString(key, Base64.NO_WRAP), preferences.getString(id, null))
        assertArrayEquals(key, provider().keyFor(ACCOUNT))
        assertEquals(1, encryption.encryptions)
        assertFalse(locator.databaseFile(ACCOUNT).exists())
        assertTrue(context.getSharedPreferences("bitcoin_kit_database_keys", Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test
    fun keyForInitialization_concurrentProviders_onlyOneReportsNewKey() {
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val requests = List(2) {
                executor.submit<BeamDatabaseKeyProvider.Key> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    provider().keyForInitialization(ACCOUNT, BeamNetwork.Mainnet)
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            val keys = requests.map { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, keys.count { it.isNew })
            assertArrayEquals(keys.first().bytes, keys.last().bytes)
            assertEquals(1, encryption.encryptions)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun keyFor_existingDatabaseAndValidWrapper_reusesKey() {
        val key = provider().keyFor(ACCOUNT)
        createDatabase()
        assertArrayEquals(key, provider().keyFor(ACCOUNT))
        assertEquals(1, encryption.encryptions)
    }

    @Test
    fun keyFor_existingDatabaseAndMissingWrapper_failsWithoutGenerating() {
        createDatabase()
        assertFailsWith<BeamDatabaseKeyException> { provider().keyFor(ACCOUNT) }
        assertFalse(preferences.contains(id))
        assertEquals(0, encryption.encryptions)
        assertEquals("database-sentinel", locator.databaseFile(ACCOUNT).readText())
    }

    @Test
    fun keyFor_invalidWrapperWithEitherDatabaseState_failsWithoutReplacing() {
        val invalidValues = listOf(
            "corrupt-wrapper", "wrapped:%%%", "wrapped:",
            "wrapped:${Base64.encodeToString(ByteArray(31), Base64.NO_WRAP)}",
            "wrapped:${Base64.encodeToString(ByteArray(32), Base64.NO_WRAP)}",
            "wrapped:${Base64.encodeToString(ByteArray(64), Base64.NO_WRAP)}"
        )
        for (databaseExists in listOf(false, true)) {
            if (databaseExists) createDatabase()
            for (value in invalidValues) {
                preferences.edit().putString(id, value).commit()
                assertFailsWith<BeamDatabaseKeyException> { provider().keyFor(ACCOUNT) }
                assertEquals(value, preferences.getString(id, null))
            }
        }
        assertEquals(0, encryption.encryptions)
    }

    @Test
    fun keyFor_wrongPreferenceTypeWithEitherDatabaseState_failsClosed() {
        preferences.edit().putInt(id, 42).commit()
        for (databaseExists in listOf(false, true)) {
            if (databaseExists) createDatabase()
            assertFailsWith<BeamDatabaseKeyException> { provider().keyFor(ACCOUNT) }
            assertEquals(42, preferences.getInt(id, 0))
        }
        assertEquals(0, encryption.encryptions)
    }

    @Test
    fun keyFor_undecryptableWrapperWithEitherDatabaseState_failsClosed() {
        provider().keyFor(ACCOUNT)
        val wrapper = preferences.getString(id, null)
        encryption.failure = BadPaddingException()
        for (databaseExists in listOf(false, true)) {
            if (databaseExists) createDatabase()
            assertFailsWith<BeamDatabaseKeyException> { provider().keyFor(ACCOUNT) }
            assertEquals(wrapper, preferences.getString(id, null))
        }
        assertEquals(1, encryption.encryptions)
    }

    @Test
    fun keyFor_lockedWrapperWithEitherDatabaseState_retriesSameKeyAfterUnlock() {
        val key = provider().keyFor(ACCOUNT)
        val locked = UserNotAuthenticatedException()
        for (databaseExists in listOf(false, true)) {
            if (databaseExists) createDatabase()
            encryption.failure = locked
            val error = assertFailsWith<BeamDatabaseKeyLockedException> { provider().keyFor(ACCOUNT) }
            assertTrue(error.cause === locked)
            encryption.failure = null
            assertArrayEquals(key, provider().keyFor(ACCOUNT))
        }
        assertEquals(1, encryption.encryptions)
    }

    @Test
    fun keyFor_lockedCreation_doesNotPersistAndAllowsRetry() {
        encryption.failure = UserNotAuthenticatedException()
        assertFailsWith<BeamDatabaseKeyLockedException> { provider().keyFor(ACCOUNT) }
        assertFalse(preferences.contains(id))
        encryption.failure = null
        assertEquals(32, provider().keyFor(ACCOUNT).size)
    }

    @Test
    fun keyFor_commitFailure_doesNotReturnKey() {
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putString(any(), any()) } answers {
            preferences.edit().putString(firstArg(), secondArg()).commit()
            editor
        }
        every { editor.remove(any()) } answers {
            preferences.edit().remove(firstArg()).commit()
            editor
        }
        every { editor.commit() } returns false
        val failingPreferences = mockk<SharedPreferences> {
            every { contains(any()) } returns false
            every { edit() } returns editor
        }
        val failingContext = mockk<Context> {
            every { getSharedPreferences("beam_database_keys", Context.MODE_PRIVATE) } returns failingPreferences
        }
        val provider = BeamDatabaseKeyProvider(failingContext, locator, encryption, mockk(relaxed = true))
        assertFailsWith<BeamDatabaseKeyException> { provider.keyFor(ACCOUNT) }
        assertFalse(preferences.contains(id))
    }

    @Test
    fun remove_databaseStillPresent_preservesWrapper() {
        val provider = provider()
        provider.keyFor(ACCOUNT)
        createDatabase()

        assertFailsWith<IllegalStateException> { provider.remove(ACCOUNT) }

        assertTrue(preferences.contains(id))
        assertTrue(locator.databaseFile(ACCOUNT).exists())
    }

    @Test
    fun remove_oneAccount_preservesOtherKeyAndDatabase() {
        val provider = provider()
        val firstKey = provider.keyFor(ACCOUNT)
        val secondKey = provider.keyFor("other-account")
        assertFalse(firstKey.contentEquals(secondKey))
        provider.remove(ACCOUNT)
        assertFalse(preferences.contains(id))
        assertArrayEquals(secondKey, provider.keyFor("other-account"))
        assertFalse(locator.databaseFile(ACCOUNT).exists())
    }

    private fun provider() = BeamDatabaseKeyProvider(context, locator, encryption, mockk(relaxed = true))

    private fun createDatabase() {
        val database = locator.databaseFile(ACCOUNT)
        check(database.parentFile?.mkdirs() == true || database.parentFile?.isDirectory == true)
        database.writeText("database-sentinel")
    }

    private fun clearStorage() {
        preferences.edit().clear().commit()
        context.noBackupFilesDir.resolve("beam").deleteRecursively()
    }

    private class FakeEncryption : IEncryptionManager {
        var failure: Exception? = null
        var encryptions = 0

        override fun encrypt(data: String): String {
            failure?.let { throw it }
            encryptions++
            return "wrapped:$data"
        }

        override fun decrypt(data: String): String {
            failure?.let { throw it }
            require(data.startsWith("wrapped:"))
            return data.removePrefix("wrapped:")
        }
    }

    private companion object {
        const val ACCOUNT = "beam-test-account"
    }
}
