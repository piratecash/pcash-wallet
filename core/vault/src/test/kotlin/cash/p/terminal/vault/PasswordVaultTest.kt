package cash.p.terminal.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission

class PasswordVaultTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val lightParams = checkNotNull(Argon2Params.ofOrNull(1024, 1, 1))
    private val password = "correct horse".toCharArray()
    private val newPassword = "battery staple".toCharArray()
    private val file by lazy { File(folder.root, "vault.bin") }
    private val vault by lazy { PasswordVault(file, lightParams) }

    @Test
    fun unlock_samePasswordAsCreate_returnsSameSubkeys() {
        val created = (vault.create(password) as CreateResult.Created).session.subkeys()

        assertEquals(created, vault.unlock(password).subkeys())
    }

    @Test
    fun unlock_wrongPassword_returnsWrongPassword() {
        vault.create(password)

        assertEquals(UnlockResult.WrongPassword, vault.unlock(newPassword))
    }

    @Test
    fun create_fileExists_returnsAlreadyExistsAndKeepsFile() {
        vault.create(password)
        val before = file.readBytes()

        assertEquals(CreateResult.AlreadyExists, vault.create(newPassword))
        assertArrayEquals(before, file.readBytes())
    }

    @Test
    fun changePassword_correctOld_newGivesSameSubkeysAndOldIsRejected() {
        val created = (vault.create(password) as CreateResult.Created).session.subkeys()

        assertEquals(ChangePasswordResult.Success, vault.changePassword(password, newPassword))
        assertEquals(UnlockResult.WrongPassword, vault.unlock(password))
        assertEquals(created, vault.unlock(newPassword).subkeys())
    }

    @Test
    fun changePassword_wrongOld_returnsWrongPasswordAndKeepsFile() {
        vault.create(password)
        val before = file.readBytes()

        assertEquals(ChangePasswordResult.WrongPassword, vault.changePassword(newPassword, password))
        assertArrayEquals(before, file.readBytes())
    }

    @Test
    fun changePassword_otherPasswordSlot_keptByteForByte() {
        vault.create(password)
        val otherFile = File(folder.root, "other.bin")
        val other = PasswordVault(otherFile, lightParams)
        val otherKeys = (other.create(newPassword) as CreateResult.Created).session.subkeys()
        val otherSlot = otherFile.readBytes().copyOfRange(5, 91)
        file.appendBytes(otherSlot)

        assertEquals(ChangePasswordResult.Success, vault.changePassword(password, "third".toCharArray()))
        assertArrayEquals(otherSlot, file.readBytes().copyOfRange(91, 177))
        assertEquals(otherKeys, vault.unlock(newPassword).subkeys())
    }

    @Test
    fun changePassword_oldPasswordOpensSlotWithOtherMasterKey_keepsThatSlot() {
        vault.create(password)
        val otherFile = File(folder.root, "other.bin")
        val other = PasswordVault(otherFile, lightParams)
        val otherKeys = (other.create(password) as CreateResult.Created).session.subkeys()
        val otherSlot = otherFile.readBytes().copyOfRange(5, 91)
        file.appendBytes(otherSlot)

        assertEquals(ChangePasswordResult.Success, vault.changePassword(password, newPassword))
        assertArrayEquals(otherSlot, file.readBytes().copyOfRange(91, 177))
        assertEquals(otherKeys, vault.unlock(password).subkeys())
    }

    @Test
    fun unlock_slotHeaderFieldTampered_neverSucceeds() {
        vault.create(password)
        val original = file.readBytes()
        val tamperedOffsets = listOf(5, 9, 13, 14, 15, 30) // kdf, memory, iterations, parallelism, salt

        for (offset in tamperedOffsets) {
            file.writeBytes(original.copyOf().also { it[offset] = (it[offset] + 1).toByte() })
            val result = vault.unlock(password)
            assertTrue(
                "offset $offset: $result",
                result == UnlockResult.WrongPassword || result == UnlockResult.Corrupted,
            )
        }
    }

    @Test
    fun unlock_ciphertextByteCorrupted_returnsWrongPassword() {
        vault.create(password)
        file.writeBytes(file.readBytes().also { it[60] = (it[60] + 1).toByte() })

        assertEquals(UnlockResult.WrongPassword, vault.unlock(password))
    }

    @Test
    fun unlock_missingFile_returnsCorrupted() {
        assertEquals(UnlockResult.Corrupted, vault.unlock(password))
        assertEquals(ChangePasswordResult.Corrupted, vault.changePassword(password, newPassword))
    }

    @Test
    fun changePassword_missingDirectory_returnsCorrupted() {
        val missingVault = PasswordVault(File(folder.root, "missing/vault.bin"), lightParams)

        assertEquals(ChangePasswordResult.Corrupted, missingVault.changePassword(password, newPassword))
    }

    @Test
    fun unlock_malformedFile_returnsCorrupted() {
        vault.create(password)
        file.appendBytes(byteArrayOf(0))

        assertEquals(UnlockResult.Corrupted, vault.unlock(password))
        assertEquals(ChangePasswordResult.Corrupted, vault.changePassword(password, newPassword))
    }

    @Test
    fun unlock_appendedSlotCopy_returnsSameSubkeys() {
        val created = (vault.create(password) as CreateResult.Created).session.subkeys()
        file.appendBytes(file.readBytes().copyOfRange(5, 91))

        assertEquals(created, vault.unlock(password).subkeys())
    }

    @Test
    fun changePassword_appendedSlotCopy_leavesOneSlotAndRejectsOld() {
        vault.create(password)
        file.appendBytes(file.readBytes().copyOfRange(5, 91))

        assertEquals(ChangePasswordResult.Success, vault.changePassword(password, newPassword))
        assertEquals(91, file.length())
        assertEquals(UnlockResult.WrongPassword, vault.unlock(password))
    }

    @Test
    fun subkeys_unlockedSession_distinctAnd32Bytes() {
        val keys = (vault.create(password) as CreateResult.Created).session.subkeys()

        assertEquals(3, keys.toSet().size)
        assertTrue(keys.all { it?.size == 32 })
    }

    @Test
    fun close_session_wipesMasterKeyAndGettersReturnNull() {
        val masterKey = ByteArray(32) { 7 }
        val session = VaultSession(masterKey)
        assertNotNull(session.appDatabaseKey())

        session.close()
        session.close()

        assertTrue(masterKey.all { it == 0.toByte() })
        assertEquals(listOf(null, null, null), session.subkeys())
    }

    @Test
    fun create_orphanTemp_isDeleted() {
        val orphan = File(folder.root, "vault.bin.123.tmp").apply { writeBytes(byteArrayOf(1)) }

        vault.create(password)

        assertFalse(orphan.exists())
    }

    @Test
    fun changePassword_orphanTemp_isDeleted() {
        vault.create(password)
        val orphan = File(folder.root, "vault.bin.123.tmp").apply { writeBytes(byteArrayOf(1)) }

        vault.changePassword(password, newPassword)

        assertFalse(orphan.exists())
    }

    @Test
    fun create_undeletableOrphanTemp_returnsFailedAndNoVaultFile() {
        undeletableOrphanTemp()

        assertEquals(CreateResult.Failed, vault.create(password))
        assertFalse(file.exists())
    }

    @Test
    fun changePassword_undeletableOrphanTemp_throwsAndKeepsVault() {
        vault.create(password)
        val before = file.readBytes()
        undeletableOrphanTemp()

        assertThrows(IOException::class.java) { vault.changePassword(password, newPassword) }
        assertArrayEquals(before, file.readBytes())
        assertTrue(vault.unlock(password) is UnlockResult.Success)
    }

    @Test
    fun changePassword_unlistableDirectory_throwsAndKeepsVault() {
        val dir = folder.root.toPath()
        assumeTrue(Files.getFileAttributeView(dir, PosixFileAttributeView::class.java) != null)
        vault.create(password)
        val before = file.readBytes()
        val permissions = Files.getPosixFilePermissions(dir)

        Files.setPosixFilePermissions(dir, setOf(PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE))
        try {
            assertThrows(IOException::class.java) { vault.changePassword(password, newPassword) }
        } finally {
            Files.setPosixFilePermissions(dir, permissions)
        }

        assertArrayEquals(before, file.readBytes())
        assertTrue(vault.unlock(password) is UnlockResult.Success)
    }

    @Test
    fun create_slot_isBoundToItsHeaderAsAad() {
        vault.create(password)
        val slot = checkNotNull(VaultFileFormat.decode(file.readBytes())).slots.single()
        val kek = deriveKek(password.toUtf8Bytes(), slot.salt, slot.params)
        val aad = VaultFileFormat.slotAad(slot.params, slot.salt)

        assertNull(AesGcm.decrypt(slot.sealedMasterKey, kek))
        assertEquals(32, AesGcm.decrypt(slot.sealedMasterKey, kek, aad)?.size)
    }

    @Test
    fun create_success_leavesNoTempFile() {
        vault.create(password)

        assertEquals(listOf("vault.bin"), folder.root.list()?.toList())
    }

    @Test
    fun create_missingDirectory_returnsFailedAndCreatesNothing() {
        val missingDir = File(folder.root, "missing")

        assertEquals(CreateResult.Failed, PasswordVault(File(missingDir, "vault.bin"), lightParams).create(password))
        assertFalse(missingDir.exists())
        assertEquals(0, folder.root.list()?.size)
    }

    @Test
    fun unlock_defaultParams_returnsSameSubkeys() {
        val defaultVault = PasswordVault(file)
        val created = (defaultVault.create(password) as CreateResult.Created).session.subkeys()

        assertEquals(created, defaultVault.unlock(password).subkeys())
        assertNull(defaultVault.unlock(newPassword).subkeys())
    }

    // A non-empty directory makes Files.delete fail deterministically on every OS.
    private fun undeletableOrphanTemp() {
        File(folder.root, "vault.bin.x.tmp").apply { mkdir() }.resolve("content").writeBytes(byteArrayOf(1))
    }

    private fun UnlockResult.subkeys(): List<List<Byte>?>? = (this as? UnlockResult.Success)?.session?.subkeys()

    private fun VaultSession.subkeys(): List<List<Byte>?> =
        listOf(appDatabaseKey(), secretFieldsKey(), kitKeysWrappingKey()).map { it?.toList() }
}
