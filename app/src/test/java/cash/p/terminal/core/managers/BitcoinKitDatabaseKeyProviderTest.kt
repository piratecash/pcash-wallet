package cash.p.terminal.core.managers

import android.content.Context
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import io.horizontalsystems.core.IEncryptionManager
import io.mockk.every
import io.mockk.mockk
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
    fun keyFor_newAndExistingAccount_persistsEncryptedStableKey() {
        val firstProvider = DefaultBitcoinKitDatabaseKeyProvider(context, encryptionManager)

        val firstKey = firstProvider.keyFor(ACCOUNT_ID)
        val storedValue = preferences().getString(preferenceKey(), null)
        val restoredKey = DefaultBitcoinKitDatabaseKeyProvider(context, encryptionManager).keyFor(ACCOUNT_ID)

        assertArrayEquals(firstKey, restoredKey)
        assertTrue(firstKey.size == KEY_SIZE)
        assertNotEquals(Base64.encodeToString(firstKey, Base64.NO_WRAP), storedValue)
    }

    @Test
    fun keyFor_presentCorruptValue_throwsWithoutReplacingIt() {
        val corruptValue = "not-encrypted"
        preferences().edit().putString(preferenceKey(), corruptValue).commit()
        val provider = DefaultBitcoinKitDatabaseKeyProvider(context, encryptionManager)

        assertFailsWith<BitcoinKitDatabaseKeyException> {
            provider.keyFor(ACCOUNT_ID)
        }

        assertTrue(preferences().contains(preferenceKey()))
        assertTrue(preferences().getString(preferenceKey(), null) == corruptValue)
    }

    @Test
    fun keyFor_storedKeyRequiresAuthentication_reportsRetryableLock() {
        preferences().edit().putString(preferenceKey(), "encrypted-key").commit()
        val authenticationRequired = mockk<UserNotAuthenticatedException>()
        val lockedEncryptionManager = mockk<IEncryptionManager> {
            every { decrypt(any()) } throws authenticationRequired
        }
        val provider = DefaultBitcoinKitDatabaseKeyProvider(context, lockedEncryptionManager)

        val error = assertFailsWith<BitcoinKitDatabaseKeyLockedException> {
            provider.keyFor(ACCOUNT_ID)
        }

        assertTrue(error.cause === authenticationRequired)
    }

    @Test
    fun remove_existingKey_removesStoredKey() {
        val provider = DefaultBitcoinKitDatabaseKeyProvider(context, encryptionManager)
        provider.keyFor(ACCOUNT_ID)

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
