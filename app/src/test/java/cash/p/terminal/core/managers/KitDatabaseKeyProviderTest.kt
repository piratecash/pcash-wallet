package cash.p.terminal.core.managers

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import io.horizontalsystems.core.IEncryptionManager
import io.mockk.every
import io.mockk.mockk
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
    fun keyFor_bitcoinAndSolanaProviders_keepSeparateKeys() {
        val bitcoinProvider = BitcoinKitDatabaseKeyProvider(context, encryptionManager)
        val solanaProvider = SolanaKitDatabaseKeyProvider(context, encryptionManager)

        val bitcoinKey = bitcoinProvider.keyFor(ACCOUNT_ID)
        val solanaKey = solanaProvider.keyFor(ACCOUNT_ID)

        assertFalse(bitcoinKey.contentEquals(solanaKey))
        assertTrue(solanaPreferences().contains(SOLANA_PREFERENCE_KEY))
        assertTrue(bitcoinPreferences().contains(BITCOIN_PREFERENCE_KEY))

        solanaProvider.remove(ACCOUNT_ID)

        assertFalse(solanaPreferences().contains(SOLANA_PREFERENCE_KEY))
        assertArrayEquals(bitcoinKey, bitcoinProvider.keyFor(ACCOUNT_ID))

        val newSolanaKey = solanaProvider.keyFor(ACCOUNT_ID)
        bitcoinProvider.remove(ACCOUNT_ID)

        assertFalse(bitcoinPreferences().contains(BITCOIN_PREFERENCE_KEY))
        assertArrayEquals(newSolanaKey, solanaProvider.keyFor(ACCOUNT_ID))
    }

    @Test
    fun keyFor_commitFails_throwsAndLeavesNoUnsavedKey() {
        val failingContext = FirstPutCommitFailsContext(context)
        val provider = SolanaKitDatabaseKeyProvider(failingContext, encryptionManager)

        assertFailsWith<KitDatabaseKeyException> { provider.keyFor(ACCOUNT_ID) }

        assertFalse(solanaPreferences().contains(SOLANA_PREFERENCE_KEY))

        val key = provider.keyFor(ACCOUNT_ID)

        assertArrayEquals(key, SolanaKitDatabaseKeyProvider(context, encryptionManager).keyFor(ACCOUNT_ID))
    }

    private fun clearPreferences() {
        bitcoinPreferences().edit().clear().commit()
        solanaPreferences().edit().clear().commit()
    }

    private fun bitcoinPreferences() =
        context.getSharedPreferences("bitcoin_kit_database_keys", Context.MODE_PRIVATE)

    private fun solanaPreferences() =
        context.getSharedPreferences("solana_kit_database_keys", Context.MODE_PRIVATE)

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
        const val SOLANA_PREFERENCE_KEY = "solana_kit_database_key_$ACCOUNT_ID"
        const val ENCRYPTED_PREFIX = "encrypted:"
    }
}
