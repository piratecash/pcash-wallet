package cash.p.terminal.vault

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM with a random nonce; sealed layout is `[nonce][ciphertext][tag]`. */
object AesGcm {
    const val NONCE_SIZE = 12
    const val TAG_SIZE = 16

    private val secureRandom = SecureRandom()

    fun encrypt(plaintext: ByteArray, key: ByteArray, aad: ByteArray? = null): ByteArray {
        val nonce = ByteArray(NONCE_SIZE)
        secureRandom.nextBytes(nonce)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE * 8, nonce))
        aad?.let(cipher::updateAAD)

        val ciphertext = cipher.doFinal(plaintext)

        return ByteArray(NONCE_SIZE + ciphertext.size).also {
            System.arraycopy(nonce, 0, it, 0, NONCE_SIZE)
            System.arraycopy(ciphertext, 0, it, NONCE_SIZE, ciphertext.size)
        }
    }

    /** Returns null when the data is too short or authentication fails. */
    fun decrypt(data: ByteArray, key: ByteArray, aad: ByteArray? = null): ByteArray? {
        if (data.size < NONCE_SIZE + TAG_SIZE) return null

        return try {
            val nonce = data.copyOfRange(0, NONCE_SIZE)
            val ciphertext = data.copyOfRange(NONCE_SIZE, data.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE * 8, nonce))
            aad?.let(cipher::updateAAD)
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            null
        }
    }
}
