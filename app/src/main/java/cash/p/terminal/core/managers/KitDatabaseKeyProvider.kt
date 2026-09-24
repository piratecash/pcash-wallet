package cash.p.terminal.core.managers

import android.content.Context
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import io.horizontalsystems.core.IEncryptionManager
import java.security.SecureRandom

interface KitDatabaseKeyProvider {
    fun keyFor(accountId: String): ByteArray
    fun remove(accountId: String)
}

class KitDatabaseKeyException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class KitDatabaseKeyLockedException(cause: UserNotAuthenticatedException) :
    IllegalStateException("Kit database key requires user authentication", cause)

class DefaultKitDatabaseKeyProvider(
    context: Context,
    private val encryptionManager: IEncryptionManager,
) : KitDatabaseKeyProvider {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun keyFor(accountId: String): ByteArray {
        val preferenceKey = accountId.preferenceKey()
        if (preferences.contains(preferenceKey)) {
            return storedKey(preferenceKey)
        }

        val key = ByteArray(KEY_SIZE).also(SecureRandom()::nextBytes)
        val encoded = Base64.encodeToString(key, Base64.NO_WRAP)
        val encrypted = accessKeyStore { encryptionManager.encrypt(encoded) }
        if (!preferences.edit().putString(preferenceKey, encrypted).commit()) {
            throw KitDatabaseKeyException("Unable to persist kit database key")
        }
        return key
    }

    override fun remove(accountId: String) {
        if (!preferences.edit().remove(accountId.preferenceKey()).commit()) {
            throw KitDatabaseKeyException("Unable to remove kit database key")
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
            invalidStoredKey(message = "Stored kit database key has invalid size")
        }
        return key
    }

    private inline fun <T> accessKeyStore(block: () -> T): T = try {
        block()
    } catch (error: UserNotAuthenticatedException) {
        throw KitDatabaseKeyLockedException(error)
    }

    private fun invalidStoredKey(
        cause: Throwable? = null,
        message: String = "Stored kit database key is invalid",
    ): Nothing = throw KitDatabaseKeyException(message, cause)

    private fun String.preferenceKey() = "$KEY_PREFIX$this"

    private companion object {
        // Legacy bitcoin-kit names: keys persisted before the provider became shared live there.
        const val PREFERENCES_NAME = "bitcoin_kit_database_keys"
        const val KEY_PREFIX = "bitcoin_kit_database_key_"
        const val KEY_SIZE = 32
    }
}
