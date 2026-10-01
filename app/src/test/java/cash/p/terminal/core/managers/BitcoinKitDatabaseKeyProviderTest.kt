package cash.p.terminal.core.managers

import android.content.Context
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import io.horizontalsystems.core.IEncryptionManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BitcoinKitDatabaseKeyProviderTest {
    private lateinit var context: Context
    private lateinit var encryptionManager: IEncryptionManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences().edit().clear().commit()
        encryptionManager = PrefixEncryptionManager()
    }

    @After
    fun tearDown() {
        preferences().edit().clear().commit()
    }

    @Test
    fun awaitKey_newAndExistingAccount_persistsEncryptedStableKey() = runTest {
        val firstProvider = BitcoinKitDatabaseKeyProvider(context, encryptionManager)

        val firstKey = firstProvider.awaitKey(ACCOUNT_ID)
        val storedValue = preferences().getString(preferenceKey(), null)
        val restoredKey = BitcoinKitDatabaseKeyProvider(context, encryptionManager).awaitKey(ACCOUNT_ID)

        assertArrayEquals(firstKey, restoredKey)
        assertTrue(firstKey.size == KEY_SIZE)
        assertNotEquals(Base64.encodeToString(firstKey, Base64.NO_WRAP), storedValue)
    }

    @Test
    fun awaitKey_keyStoredByPreviousRelease_returnsIt() = runTest {
        val storedKey = ByteArray(KEY_SIZE) { it.toByte() }
        val encoded = Base64.encodeToString(storedKey, Base64.NO_WRAP)
        preferences().edit().putString(preferenceKey(), encryptionManager.encrypt(encoded)).commit()

        val key = BitcoinKitDatabaseKeyProvider(context, encryptionManager).awaitKey(ACCOUNT_ID)

        assertArrayEquals(storedKey, key)
    }

    @Test
    fun awaitKey_presentCorruptValue_throwsWithoutReplacingIt() = runTest {
        val corruptValue = "not-encrypted"
        preferences().edit().putString(preferenceKey(), corruptValue).commit()
        val provider = BitcoinKitDatabaseKeyProvider(context, encryptionManager)

        assertFailsWith<KitDatabaseKeyException> {
            provider.awaitKey(ACCOUNT_ID)
        }

        assertTrue(preferences().contains(preferenceKey()))
        assertTrue(preferences().getString(preferenceKey(), null) == corruptValue)
    }

    @Test
    fun awaitKey_storedKeyRequiresAuthentication_retriesUntilAuthenticationSucceeds() = runTest {
        val storedKey = ByteArray(KEY_SIZE) { it.toByte() }
        val encrypted = encryptionManager.encrypt(Base64.encodeToString(storedKey, Base64.NO_WRAP))
        preferences().edit().putString(preferenceKey(), encrypted).commit()
        val lockedEncryptionManager = mockk<IEncryptionManager> {
            every { decrypt(encrypted) } throws mockk<UserNotAuthenticatedException>() andThen
                encryptionManager.decrypt(encrypted)
        }
        val provider = BitcoinKitDatabaseKeyProvider(context, lockedEncryptionManager)

        val key = provider.awaitKey(ACCOUNT_ID)

        assertArrayEquals(storedKey, key)
        verify(exactly = 2) { lockedEncryptionManager.decrypt(encrypted) }
    }

    @Test
    fun remove_existingKey_removesStoredKey() = runTest {
        val provider = BitcoinKitDatabaseKeyProvider(context, encryptionManager)
        provider.awaitKey(ACCOUNT_ID)

        provider.remove(ACCOUNT_ID)

        assertFalse(preferences().contains(preferenceKey()))
    }

    private fun preferences() = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun preferenceKey() = "$KEY_PREFIX$ACCOUNT_ID"

    private class PrefixEncryptionManager : IEncryptionManager {
        override fun encrypt(data: String) = "$ENCRYPTED_PREFIX$data"

        override fun decrypt(data: String): String {
            require(data.startsWith(ENCRYPTED_PREFIX))
            return data.removePrefix(ENCRYPTED_PREFIX)
        }
    }

    private companion object {
        const val ACCOUNT_ID = "account-id"
        const val PREFERENCES_NAME = "bitcoin_kit_database_keys"
        const val KEY_PREFIX = "bitcoin_kit_database_key_"
        const val KEY_SIZE = 32
        const val ENCRYPTED_PREFIX = "encrypted:"
    }
}
