package cash.p.terminal.core.managers

import android.content.Context
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import java.nio.file.Files
import io.horizontalsystems.core.IEncryptionManager
import java.security.MessageDigest
import java.security.SecureRandom

class BeamDatabaseKeyException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class BeamDatabaseKeyLockedException(cause: UserNotAuthenticatedException) :
    IllegalStateException("BEAM database key requires user authentication", cause)

class BeamDatabaseKeyProvider(
    context: Context,
    private val storageLocator: BeamStorageLocator,
    private val encryptionManager: IEncryptionManager,
    private val deletionState: BeamDeletionState,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun hasKey(accountId: String, network: BeamNetwork = BeamNetwork.Mainnet): Boolean =
        preferences.contains(storageLocator.storageId(accountId, network))

    fun hasAnyKey(): Boolean = preferences.all.isNotEmpty()

    internal fun storageIds(): Set<String> = synchronized(lock) { preferences.all.keys }

    internal fun ensureAvailable(accountId: String) = deletionState.ensureAvailable(accountId)

    fun keyFor(accountId: String, network: BeamNetwork = BeamNetwork.Mainnet): ByteArray =
        keyForInitialization(accountId, network).bytes

    internal fun keyForInitialization(accountId: String, network: BeamNetwork): Key =
        synchronized(lock) {
            ensureAvailable(accountId)
            val id = storageLocator.storageId(accountId, network)
            if (preferences.contains(id)) {
                return@synchronized Key(accessKeyStore { storedKey(id) }, isNew = false)
            }
            if (storageLocator.databaseFile(accountId, network).exists()) {
                throw BeamDatabaseKeyException("Existing BEAM database has no stored key")
            }
            Key(accessKeyStore { createKey(id) }, isNew = true)
        }

    internal class Key(val bytes: ByteArray, val isNew: Boolean)

    fun remove(accountId: String, network: BeamNetwork = BeamNetwork.Mainnet) =
        removeStorageKey(storageLocator.storageId(accountId, network))

    // Account deletion order: the ciphertext must be gone before the key that could still decrypt a
    // forensic copy of it, so a partially erased wallet keeps a usable key for the retry.
    internal fun removeStorageKey(id: String) = synchronized(lock) {
        check(Files.notExists(storageLocator.validatedPath(id).toPath())) {
            "BEAM database must be erased before its key"
        }
        shredStorageKey(id)
    }

    // Explicit (duress) reset only: drop the wrapper while the SQLCipher payload is still on disk.
    // That crypto-shreds the payload, so the file removal that follows may fail without leaking
    // anything. It intentionally skips both the erase-first check and path validation, so a wrapper
    // whose id no longer maps to a valid storage scope cannot block the reset forever.
    internal fun shredStorageKey(id: String) = synchronized(lock) {
        if (!preferences.edit().remove(id).commit()) {
            throw BeamDatabaseKeyException("Unable to remove BEAM database key")
        }
    }

    private fun createKey(id: String): ByteArray {
        val key = ByteArray(KEY_SIZE)
        try {
            SecureRandom().nextBytes(key)
            val encrypted = encryptKey(key)
            if (!preferences.edit().putString(id, encrypted).commit()) {
                // A failed commit can still publish the value in memory; never return that key on retry.
                preferences.edit().remove(id).commit()
                throw BeamDatabaseKeyException("Unable to persist BEAM database key")
            }
            return key
        } catch (error: Throwable) {
            key.fill(0)
            throw error
        }
    }

    private fun encryptKey(key: ByteArray): String {
        // The existing CBC wrapper has no integrity tag: detect damaged but decryptable payloads.
        val digest = MessageDigest.getInstance("SHA-256").digest(key)
        val payload = key + digest
        return try {
            encryptionManager.encrypt(Base64.encodeToString(payload, Base64.NO_WRAP))
        } finally {
            payload.fill(0)
            digest.fill(0)
        }
    }

    private fun storedKey(id: String): ByteArray {
        val encrypted = preferences.getString(id, null)
            ?: throw BeamDatabaseKeyException("Stored BEAM database key is missing")
        val encoded = encryptionManager.decrypt(encrypted)
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        return try {
            check(payload.size == KEY_SIZE * 2 && Base64.encodeToString(payload, Base64.NO_WRAP) == encoded)
            validatedKey(payload)
        } finally {
            payload.fill(0)
        }
    }

    private fun validatedKey(payload: ByteArray): ByteArray {
        val key = payload.copyOfRange(0, KEY_SIZE)
        val digest = MessageDigest.getInstance("SHA-256").digest(key)
        val storedDigest = payload.copyOfRange(KEY_SIZE, payload.size)
        try {
            check(MessageDigest.isEqual(digest, storedDigest))
            return key
        } catch (error: Throwable) {
            key.fill(0)
            throw error
        } finally {
            digest.fill(0)
            storedDigest.fill(0)
        }
    }

    private inline fun <T> accessKeyStore(block: () -> T): T = try {
        block()
    } catch (error: UserNotAuthenticatedException) {
        throw BeamDatabaseKeyLockedException(error)
    } catch (error: BeamDatabaseKeyException) {
        throw error
    } catch (error: Exception) {
        throw BeamDatabaseKeyException("Unable to access BEAM database key", error)
    }

    private companion object {
        const val PREFERENCES_NAME = "beam_database_keys"
        const val KEY_SIZE = 32
        val lock = Any()
    }
}
