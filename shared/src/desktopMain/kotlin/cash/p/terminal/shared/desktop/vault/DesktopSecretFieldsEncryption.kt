package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.StorageLockedException
import cash.p.terminal.vault.AesGcm
import io.horizontalsystems.core.IEncryptionManager
import java.util.Base64

/** Secret database fields, sealed with the session key as Base64(nonce‖ciphertext‖tag). */
class DesktopSecretFieldsEncryption(private val vault: DesktopVault) : IEncryptionManager {

    override fun encrypt(data: String): String =
        Base64.getEncoder().encodeToString(AesGcm.encrypt(data.toByteArray(Charsets.UTF_8), sessionKey()))

    override fun decrypt(data: String): String {
        val sealed = Base64.getDecoder().decode(data)
        val plaintext = checkNotNull(AesGcm.decrypt(sealed, sessionKey())) { "Secret field does not authenticate" }
        return String(plaintext, Charsets.UTF_8)
    }

    private fun sessionKey(): ByteArray = vault.openSession()?.secretFieldsKey() ?: throw StorageLockedException()
}
