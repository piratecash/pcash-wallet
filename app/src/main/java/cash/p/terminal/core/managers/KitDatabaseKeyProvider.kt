package cash.p.terminal.core.managers

import android.content.Context
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import androidx.core.content.edit
import io.horizontalsystems.core.IEncryptionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom

class KitDatabaseKeyException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class DatabaseKey(val bytes: ByteArray, val isNew: Boolean)

class KitDatabaseKeyLockedException(kitName: String, cause: UserNotAuthenticatedException) :
    IllegalStateException("$kitName database key requires user authentication", cause)

open class KitDatabaseKeyProvider(
    context: Context,
    private val encryptionManager: IEncryptionManager,
    preferencesName: String,
    private val keyPrefix: String,
    private val kitName: String,
) {

    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    // Concurrent first calls would otherwise each create and persist a different key.
    private val keyMutex = Mutex()

    suspend fun awaitKey(accountId: String): ByteArray = awaitDatabaseKey(accountId).bytes

    /** [DatabaseKey.isNew] is true when this call created and stored the key. */
    suspend fun awaitDatabaseKey(accountId: String): DatabaseKey {
        while (true) {
            try {
                return keyMutex.withLock { keyFor(accountId) }
            } catch (_: KitDatabaseKeyLockedException) {
                delay(KEYSTORE_RETRY_DELAY_MS)
            }
        }
    }

    private fun keyFor(accountId: String): DatabaseKey {
        val preferenceKey = accountId.preferenceKey()
        if (preferences.contains(preferenceKey)) {
            return DatabaseKey(storedKey(preferenceKey), isNew = false)
        }

        val key = ByteArray(KEY_SIZE).also(SecureRandom()::nextBytes)
        val encoded = Base64.encodeToString(key, Base64.NO_WRAP)
        val encrypted = accessKeyStore { encryptionManager.encrypt(encoded) }
        if (!preferences.edit().putString(preferenceKey, encrypted).commit()) {
            // commit() applies the edit in memory even when the disk write fails.
            preferences.edit(commit = true) { remove(preferenceKey) }
            throw KitDatabaseKeyException("Unable to persist $kitName database key")
        }
        return DatabaseKey(key, isNew = true)
    }

    fun remove(accountId: String) {
        if (!preferences.edit().remove(accountId.preferenceKey()).commit()) {
            throw KitDatabaseKeyException("Unable to remove $kitName database key")
        }
    }

    private fun storedKey(preferenceKey: String): ByteArray {
        val encrypted = try {
            preferences.getString(preferenceKey, null)
        } catch (error: ClassCastException) {
            invalidStoredKey(error)
        } ?: invalidStoredKey()

        val key = try {
            Base64.decode(accessKeyStore { encryptionManager.decrypt(encrypted) }, Base64.NO_WRAP)
        } catch (error: KitDatabaseKeyLockedException) {
            throw error
        } catch (error: Exception) {
            invalidStoredKey(error)
        }

        if (key.size != KEY_SIZE) {
            invalidStoredKey(message = "Stored $kitName database key has invalid size")
        }
        return key
    }

    private inline fun <T> accessKeyStore(block: () -> T): T = try {
        block()
    } catch (error: UserNotAuthenticatedException) {
        throw KitDatabaseKeyLockedException(kitName, error)
    }

    private fun invalidStoredKey(
        cause: Throwable? = null,
        message: String = "Stored $kitName database key is invalid",
    ): Nothing = throw KitDatabaseKeyException(message, cause)

    private fun String.preferenceKey() = "$keyPrefix$this"

    private companion object {
        const val KEY_SIZE = 32
    }
}

private const val KEYSTORE_RETRY_DELAY_MS = 500L

class BitcoinKitDatabaseKeyProvider(context: Context, encryptionManager: IEncryptionManager) :
    KitDatabaseKeyProvider(
        context,
        encryptionManager,
        preferencesName = "bitcoin_kit_database_keys",
        keyPrefix = "bitcoin_kit_database_key_",
        kitName = "BitcoinKit",
    )

class TonKitDatabaseKeyProvider(context: Context, encryptionManager: IEncryptionManager) :
    KitDatabaseKeyProvider(
        context,
        encryptionManager,
        preferencesName = "ton_kit_database_keys",
        keyPrefix = "ton_kit_database_key_",
        kitName = "TonKit",
    )

class TonConnectDatabaseKeyProvider(context: Context, encryptionManager: IEncryptionManager) :
    KitDatabaseKeyProvider(
        context,
        encryptionManager,
        preferencesName = "ton_connect_database_keys",
        keyPrefix = "ton_connect_database_key_",
        kitName = "TonConnectKit",
    )

class TronKitDatabaseKeyProvider(context: Context, encryptionManager: IEncryptionManager) :
    KitDatabaseKeyProvider(
        context,
        encryptionManager,
        preferencesName = "tron_kit_database_keys",
        keyPrefix = "tron_kit_database_key_",
        kitName = "TronKit",
    )
