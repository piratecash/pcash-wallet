package cash.p.terminal.core.managers

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import io.horizontalsystems.core.IEncryptionManager
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EvmKitDatabaseKeyProviderTest {
    private lateinit var context: Context
    private val encryptionManager = object : IEncryptionManager {
        override fun encrypt(data: String) = "encrypted:$data"
        override fun decrypt(data: String) = data.removePrefix("encrypted:")
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
    fun awaitKey_evmAndBitcoinOnSameContext_keepSeparateKeys() = runTest {
        val evmKeys = evmKeys(context)
        val bitcoinKeys = bitcoinKeys()

        val evmKey = evmKeys.awaitKey(ACCOUNT_ID)
        val bitcoinKey = bitcoinKeys.awaitKey(ACCOUNT_ID)

        assertFalse(evmKey.contentEquals(bitcoinKey))
        assertTrue(preferences(EVM_PREFERENCES_NAME).contains(EVM_PREFERENCE_KEY))
        assertTrue(preferences(BITCOIN_PREFERENCES_NAME).contains(BITCOIN_PREFERENCE_KEY))
    }

    @Test
    fun remove_evmKey_keepsBitcoinKey() = runTest {
        val evmKeys = evmKeys(context)
        val bitcoinKeys = bitcoinKeys()
        val evmKey = evmKeys.awaitKey(ACCOUNT_ID)
        val bitcoinKey = bitcoinKeys.awaitKey(ACCOUNT_ID)

        evmKeys.remove(ACCOUNT_ID)

        assertFalse(preferences(EVM_PREFERENCES_NAME).contains(EVM_PREFERENCE_KEY))
        assertArrayEquals(bitcoinKey, bitcoinKeys.awaitKey(ACCOUNT_ID))
        assertFalse(evmKey.contentEquals(evmKeys.awaitKey(ACCOUNT_ID)))
    }

    @Test
    fun remove_bitcoinKey_keepsEvmKey() = runTest {
        val evmKeys = evmKeys(context)
        val bitcoinKeys = bitcoinKeys()
        val evmKey = evmKeys.awaitKey(ACCOUNT_ID)
        bitcoinKeys.awaitKey(ACCOUNT_ID)

        bitcoinKeys.remove(ACCOUNT_ID)

        assertFalse(preferences(BITCOIN_PREFERENCES_NAME).contains(BITCOIN_PREFERENCE_KEY))
        assertArrayEquals(evmKey, evmKeys.awaitKey(ACCOUNT_ID))
    }

    @Test
    fun awaitKey_commitFails_throwsAndLeavesNoUnsavedKey() = runTest {
        val keys = evmKeys(FirstPutCommitFailsContext(context))

        assertFailsWith<KitDatabaseKeyException> { keys.awaitKey(ACCOUNT_ID) }

        assertFalse(preferences(EVM_PREFERENCES_NAME).contains(EVM_PREFERENCE_KEY))

        val key = keys.awaitKey(ACCOUNT_ID)

        assertArrayEquals(key, evmKeys(context).awaitKey(ACCOUNT_ID))
    }

    private fun evmKeys(context: Context) =
        EvmKitDatabaseKeyProvider(context, encryptionManager)

    private fun bitcoinKeys() = BitcoinKitDatabaseKeyProvider(context, encryptionManager)

    private fun preferences(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun clearPreferences() {
        listOf(EVM_PREFERENCES_NAME, BITCOIN_PREFERENCES_NAME).forEach {
            preferences(it).edit().clear().commit()
        }
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
        const val EVM_PREFERENCES_NAME = "evm_kit_database_keys"
        const val EVM_PREFERENCE_KEY = "evm_kit_database_key_$ACCOUNT_ID"
        const val BITCOIN_PREFERENCES_NAME = "bitcoin_kit_database_keys"
        const val BITCOIN_PREFERENCE_KEY = "bitcoin_kit_database_key_$ACCOUNT_ID"
    }
}
