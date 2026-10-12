package cash.p.terminal.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class AesGcmTest {

    private val key = ByteArray(32) { it.toByte() }
    private val otherKey = ByteArray(32) { (it + 1).toByte() }
    private val plaintext = "master key material".toByteArray()
    private val aad = "header".toByteArray()

    @Test
    fun decrypt_withoutAad_returnsPlaintext() {
        assertArrayEquals(plaintext, AesGcm.decrypt(AesGcm.encrypt(plaintext, key), key))
    }

    @Test
    fun decrypt_withSameAad_returnsPlaintext() {
        assertArrayEquals(plaintext, AesGcm.decrypt(AesGcm.encrypt(plaintext, key, aad), key, aad))
    }

    @Test
    fun decrypt_wrongKey_returnsNull() {
        assertNull(AesGcm.decrypt(AesGcm.encrypt(plaintext, key), otherKey))
    }

    @Test
    fun decrypt_differentAad_returnsNull() {
        val sealed = AesGcm.encrypt(plaintext, key, aad)

        assertNull(AesGcm.decrypt(sealed, key, "headeR".toByteArray()))
        assertNull(AesGcm.decrypt(sealed, key))
        assertNull(AesGcm.decrypt(AesGcm.encrypt(plaintext, key), key, aad))
    }

    @Test
    fun decrypt_anyByteCorrupted_returnsNull() {
        val sealed = AesGcm.encrypt(plaintext, key, aad)

        for (index in sealed.indices) {
            val corrupted = sealed.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertNull("byte $index", AesGcm.decrypt(corrupted, key, aad))
        }
    }

    @Test
    fun decrypt_shorterThanNonceAndTag_returnsNull() {
        assertNull(AesGcm.decrypt(ByteArray(AesGcm.NONCE_SIZE + AesGcm.TAG_SIZE - 1), key))
    }

    @Test
    fun encrypt_output_isNonceThenCiphertextAndTag() {
        val sealed = AesGcm.encrypt(plaintext, key, aad)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, sealed.copyOfRange(0, 12)),
        )
        cipher.updateAAD(aad)

        assertEquals(12 + plaintext.size + 16, sealed.size)
        assertArrayEquals(plaintext, cipher.doFinal(sealed.copyOfRange(12, sealed.size)))
    }
}
