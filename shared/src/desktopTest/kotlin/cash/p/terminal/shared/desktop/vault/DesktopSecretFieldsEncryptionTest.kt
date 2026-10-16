package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.StorageLockedException
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

private const val SECRET = "aggregate twelve words of a seed phrase"

class DesktopSecretFieldsEncryptionTest {

    private val fixture = DesktopVaultFixture()
    private val vault = fixture.vault()
    private val encryption = DesktopSecretFieldsEncryption(vault)

    @AfterTest
    fun tearDown() = fixture.tearDown()

    @Test
    fun encrypt_unlockedSession_roundTripsAndHidesThePlaintext() = runTest {
        vault.create(password())

        val sealed = encryption.encrypt(SECRET)

        assertNotEquals(SECRET, sealed)
        assertEquals(SECRET, encryption.decrypt(sealed))
    }

    @Test
    fun encrypt_twiceWithTheSameKey_producesDifferentCiphertexts() = runTest {
        vault.create(password())

        assertNotEquals(encryption.encrypt(SECRET), encryption.encrypt(SECRET))
    }

    @Test
    fun encrypt_withoutSession_throwsIllegalState() {
        assertFailsWith<IllegalStateException> { encryption.encrypt(SECRET) }
        assertFailsWith<IllegalStateException> { encryption.decrypt("dGVzdA==") }
    }

    @Test
    fun decrypt_afterWipe_throwsStorageLocked() = runTest {
        vault.create(password())
        val sealed = encryption.encrypt(SECRET)

        vault.wipe()

        assertFailsWith<StorageLockedException> { encryption.decrypt(sealed) }
    }
}
