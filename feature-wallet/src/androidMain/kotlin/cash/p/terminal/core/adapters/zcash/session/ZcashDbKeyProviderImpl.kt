package cash.p.terminal.core.adapters.zcash.session

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import cash.p.terminal.core.tryOrNull
import io.horizontalsystems.core.IEncryptionManager
import java.security.SecureRandom

class ZcashDbKeyProviderImpl(
    context: Context,
    private val encryptionManager: IEncryptionManager,
) : ZcashDbKeyProvider {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun keyFor(accountId: String): ZcashDbKey {
        stored(accountId)?.let { return ZcashDbKey(it, newlyGenerated = false) }

        val generated = ByteArray(KEY_SIZE).also(SecureRandom()::nextBytes)
        val encoded = Base64.encodeToString(generated, Base64.NO_WRAP)
        prefs.edit(commit = true) { putString(accountId.prefKey(), encryptionManager.encrypt(encoded)) }
        return ZcashDbKey(generated, newlyGenerated = true)
    }

    override fun drop(accountId: String): Boolean =
        prefs.edit().remove(accountId.prefKey()).commit()

    private fun stored(accountId: String): ByteArray? {
        val encrypted = prefs.getString(accountId.prefKey(), null) ?: return null
        return tryOrNull { Base64.decode(encryptionManager.decrypt(encrypted), Base64.NO_WRAP) }
            ?.takeIf { it.size == KEY_SIZE }
    }

    private fun String.prefKey() = "$KEY_PREFIX$this"

    private companion object {
        const val PREFS_NAME = "zcash_db_keys"
        const val KEY_PREFIX = "zcash_db_key_"
        const val KEY_SIZE = 32
    }
}
