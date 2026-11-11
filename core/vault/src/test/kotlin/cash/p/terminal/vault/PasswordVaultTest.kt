package cash.p.terminal.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    private val otherPassword = "battery staple".toCharArray()
    private val file by lazy { File(folder.root, "vault.bin") }
    private val crypto = CountingCrypto()
    private val vault by lazy { PasswordVault(file, lightParams, crypto) }

    private class CountingCrypto : VaultCrypto by DefaultVaultCrypto {
        var derivations = 0
        var attempts = 0

        fun reset() {
            derivations = 0
            attempts = 0
        }

        override fun deriveKek(password: ByteArray, salt: ByteArray, params: Argon2Params): ByteArray {
            derivations++
            return DefaultVaultCrypto.deriveKek(password, salt, params)
        }

        override fun openEnvelope(envelope: ByteArray, kek: ByteArray, aad: ByteArray): ByteArray? {
            attempts++
            return DefaultVaultCrypto.openEnvelope(envelope, kek, aad)
        }
    }

    @Test
    fun unlock_samePasswordAsCreate_returnsTheRootEnvelope() {
        val created = created().session

        val opened = (vault.unlock(password) as UnlockResult.Success).session

        assertEquals(created.envelopeIndex, opened.envelopeIndex)
        assertEquals(created.fields(), opened.fields())
    }

    @Test
    fun unlock_wrongPassword_returnsWrongPassword() {
        vault.create(password)

        assertEquals(UnlockResult.WrongPassword, vault.unlock(otherPassword))
    }

    @Test
    fun unlock_passwordAtAnyPosition_triesAll32EnvelopesAndDerivesOnce() {
        vault.create(otherPassword)

        for (position in listOf(0, 13, 31)) {
            vault.sealEnvelope(position, password, payload(slot = 5))
            crypto.reset()

            val opened = (vault.unlock(password) as UnlockResult.Success).session

            assertEquals(position, opened.envelopeIndex)
            assertEquals(32, crypto.attempts)
            assertEquals(1, crypto.derivations)
            vault.freeEnvelopes(listOf(position))
        }
    }

    @Test
    fun unlock_wrongPassword_triesAll32EnvelopesAndDerivesOnce() {
        vault.create(password)
        crypto.reset()

        vault.unlock(otherPassword)

        assertEquals(32, crypto.attempts)
        assertEquals(1, crypto.derivations)
    }

    @Test
    fun opensAny_wrongPassword_returnsFalseAfterTheSameWorkAsUnlock() {
        vault.create(password)
        crypto.reset()

        assertFalse(vault.opensAny(otherPassword))
        assertEquals(32, crypto.attempts)
        assertEquals(1, crypto.derivations)
    }

    @Test
    fun opensAny_passwordOfAnyEnvelope_returnsTrue() {
        val root = created().session.envelopeIndex
        vault.sealEnvelope(otherPosition(root), otherPassword, payload(slot = 1))

        assertTrue(vault.opensAny(password))
        assertTrue(vault.opensAny(otherPassword))
    }

    @Test
    fun opensAny_unusableFile_throwsInsteadOfClaimingThePasswordIsFree() {
        assertThrows(IOException::class.java) { vault.opensAny(password) }

        file.writeBytes(byteArrayOf(1, 2, 3))
        assertThrows(IOException::class.java) { vault.opensAny(password) }

        file.writeBytes(legacyFile())
        assertThrows(IOException::class.java) { vault.opensAny(password) }
    }

    @Test
    fun create_fileExists_returnsAlreadyExistsAndKeepsFile() {
        vault.create(password)
        val before = file.readBytes()

        assertEquals(CreateResult.AlreadyExists, vault.create(otherPassword))
        assertArrayEquals(before, file.readBytes())
    }

    @Test
    fun create_repeatedly_keepsPoolsDisjointFromRootAndLeavesDecoysInRange() {
        repeat(200) {
            val created = created()
            val root = checkNotNull(created.session.payload())

            assertAllocation(root.slot, created.slotPool, positions = 24)
            assertAllocation(created.session.envelopeIndex, created.envelopePool, positions = 32)
            file.delete()
        }
    }

    @Test
    fun create_repeatedly_drawsEveryDecoyCountForSlotsAndEnvelopes() {
        val allocations = List(300) { created().also { file.delete() } }

        assertEquals((3..8).toSet(), allocations.map { 24 - 1 - it.slotPool.size }.toSet())
        assertEquals((3..8).toSet(), allocations.map { 32 - 1 - it.envelopePool.size }.toSet())
    }

    @Test
    fun create_decoyAndPoolEnvelopes_doNotOpenWithThePassword() {
        val created = created()
        val decoded = checkNotNull(VaultFileFormat.decode(file.readBytes()))
        val kek = DefaultVaultCrypto.deriveKek(password.toUtf8Bytes(), decoded.salt, decoded.params)

        val opening = decoded.envelopes.indices.filter { index ->
            val aad = VaultFileFormat.envelopeAad(decoded.params, decoded.salt, index)
            AesGcm.decrypt(decoded.envelopes[index], kek, aad) != null
        }

        assertEquals(listOf(created.session.envelopeIndex), opening)
    }

    @Test
    fun create_keys_areDistinct32ByteValues() {
        val payload = checkNotNull(created().session.payload())

        assertEquals(32, payload.slotKey.size)
        assertEquals(32, payload.sharedKey.size)
        assertFalse(payload.slotKey.contentEquals(payload.sharedKey))
    }

    @Test
    fun sealEnvelope_anyNumberOfSealedEnvelopes_keepsFileSizeAndPrefix() {
        val root = created().session.envelopeIndex
        val before = file.readBytes()

        vault.sealEnvelope(otherPosition(root), otherPassword, payload(slot = 1))
        val after = file.readBytes()

        assertEquals(before.size, after.size)
        assertArrayEquals(before.copyOfRange(0, 31), after.copyOfRange(0, 31))
    }

    @Test
    fun create_envelopes_haveNoByteOffsetSharedAcrossAllOfThem() {
        val root = created().session.envelopeIndex
        vault.sealEnvelope(otherPosition(root), otherPassword, payload(slot = 1))

        val envelopes = checkNotNull(VaultFileFormat.decode(file.readBytes())).envelopes

        for (offset in 0 until 93) {
            assertTrue("offset $offset is a marker", envelopes.map { it[offset] }.toSet().size > 1)
        }
    }

    @Test
    fun sealEnvelope_newPassword_opensItsPayloadAndChangesOnlyThatEnvelope() {
        val root = created().session.envelopeIndex
        val target = otherPosition(root)
        val before = envelopes()

        vault.sealEnvelope(target, otherPassword, payload(slot = 7))

        val opened = (vault.unlock(otherPassword) as UnlockResult.Success).session
        assertEquals(target, opened.envelopeIndex)
        assertEquals(7, opened.fields().slot)
        assertEquals(listOf(target), changedEnvelopes(before, envelopes()))
        assertTrue(vault.unlock(password) is UnlockResult.Success)
    }

    @Test
    fun sealEnvelope_sameIndexNewPassword_keepsTheKeysAndRejectsTheOldPassword() {
        val session = created().session

        checkNotNull(session.payload()).use { vault.sealEnvelope(session.envelopeIndex, otherPassword, it) }

        assertEquals(UnlockResult.WrongPassword, vault.unlock(password))
        val opened = (vault.unlock(otherPassword) as UnlockResult.Success).session
        assertEquals(session.envelopeIndex, opened.envelopeIndex)
        assertEquals(session.fields(), opened.fields())
    }

    @Test
    fun sealEnvelope_instanceWithOtherDefaultParams_usesTheParamsAndSaltOfTheFile() {
        val root = created().session.envelopeIndex
        val before = file.readBytes()
        val otherDefaults = PasswordVault(file, checkNotNull(Argon2Params.ofOrNull(2048, 2, 1)))

        otherDefaults.sealEnvelope(otherPosition(root), otherPassword, payload(slot = 2))

        assertArrayEquals(before.copyOfRange(0, 31), file.readBytes().copyOfRange(0, 31))
        assertTrue(vault.unlock(otherPassword) is UnlockResult.Success)
    }

    @Test
    fun sealEnvelope_indexOutOfRange_throwsAndKeepsFile() {
        vault.create(password)
        val before = file.readBytes()

        assertThrows(IllegalArgumentException::class.java) { vault.sealEnvelope(32, otherPassword, payload(slot = 1)) }
        assertThrows(IllegalArgumentException::class.java) { vault.freeEnvelopes(listOf(-1)) }
        assertArrayEquals(before, file.readBytes())
    }

    @Test
    fun freeEnvelopes_sealedEnvelope_noLongerOpensAndOnlyThatEnvelopeChanges() {
        val root = created().session.envelopeIndex
        val target = otherPosition(root)
        vault.sealEnvelope(target, otherPassword, payload(slot = 1))
        val before = envelopes()

        vault.freeEnvelopes(listOf(target))

        assertEquals(UnlockResult.WrongPassword, vault.unlock(otherPassword))
        assertEquals(listOf(target), changedEnvelopes(before, envelopes()))
        assertEquals(root, (vault.unlock(password) as UnlockResult.Success).session.envelopeIndex)
    }

    @Test
    fun sealThenFree_unusableFile_throwAndLeaveItUntouched() {
        val damaged = byteArrayOf(1, 2, 3)
        for (content in listOf(damaged, legacyFile())) {
            file.writeBytes(content)

            assertThrows(IOException::class.java) { vault.sealEnvelope(0, password, payload(slot = 1)) }
            assertThrows(IOException::class.java) { vault.freeEnvelopes(listOf(0)) }
            assertArrayEquals(content, file.readBytes())
        }
    }

    @Test
    fun unlock_envelopesSwapped_doesNotOpen() {
        val root = created().session.envelopeIndex
        val other = otherPosition(root)
        val decoded = checkNotNull(VaultFileFormat.decode(file.readBytes()))
        val swapped = decoded.envelopes.toMutableList().apply {
            this[root] = decoded.envelopes[other]
            this[other] = decoded.envelopes[root]
        }
        file.writeBytes(VaultFileFormat.encode(VaultFile(decoded.params, decoded.salt, swapped)))

        assertEquals(UnlockResult.WrongPassword, vault.unlock(password))
    }

    @Test
    fun unlock_prefixFieldTampered_neverSucceeds() {
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
    fun unlock_rootCiphertextByteCorrupted_returnsWrongPassword() {
        val root = created().session.envelopeIndex
        val offset = 31 + root * 93 + 40
        file.writeBytes(file.readBytes().also { it[offset] = (it[offset] + 1).toByte() })

        assertEquals(UnlockResult.WrongPassword, vault.unlock(password))
    }

    @Test
    fun unlock_missingFile_returnsCorrupted() {
        assertEquals(UnlockResult.Corrupted, vault.unlock(password))
    }

    @Test
    fun unlock_wrongSizeOrMagic_returnsCorruptedWithoutRunningArgon2() {
        vault.create(password)
        val original = file.readBytes()
        crypto.reset()

        for (content in listOf(original + 0, original.copyOf(original.size - 1), ByteArray(original.size))) {
            file.writeBytes(content)
            assertEquals(UnlockResult.Corrupted, vault.unlock(password))
        }
        assertEquals(0, crypto.derivations)
    }

    @Test
    fun unlock_version1File_returnsLegacyFormatWithoutRunningArgon2() {
        file.writeBytes(legacyFile())

        assertEquals(UnlockResult.LegacyFormat, vault.unlock(password))
        assertEquals(0, crypto.derivations)
    }

    @Test
    fun unlock_version2FileWithVersionByteDamagedToOne_isCorruptedNotLegacyAndHealsWhenRestored() {
        vault.create(password)
        val original = file.readBytes()
        file.writeBytes(original.copyOf().also { it[VERSION_BYTE_INDEX] = 1 })

        assertEquals(UnlockResult.Corrupted, vault.unlock(password))

        file.writeBytes(original)
        val result = vault.unlock(password)
        assertTrue(result is UnlockResult.Success)
        (result as UnlockResult.Success).session.close()
    }

    @Test
    fun sealEnvelope_undeletableOrphanTemp_throwsAndKeepsVault() {
        vault.create(password)
        val before = file.readBytes()
        undeletableOrphanTemp()

        assertThrows(IOException::class.java) { vault.sealEnvelope(0, otherPassword, payload(slot = 1)) }
        assertArrayEquals(before, file.readBytes())
    }

    @Test
    fun sealEnvelope_directoryNotWritable_throwsAndKeepsVault() {
        val dir = folder.root.toPath()
        assumeTrue(Files.getFileAttributeView(dir, PosixFileAttributeView::class.java) != null)
        val root = created().session.envelopeIndex
        val before = file.readBytes()
        val permissions = Files.getPosixFilePermissions(dir)

        Files.setPosixFilePermissions(dir, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE))
        try {
            assertThrows(IOException::class.java) {
                vault.sealEnvelope(otherPosition(root), otherPassword, payload(slot = 1))
            }
        } finally {
            Files.setPosixFilePermissions(dir, permissions)
        }

        assertArrayEquals(before, file.readBytes())
        assertEquals(root, (vault.unlock(password) as UnlockResult.Success).session.envelopeIndex)
    }

    @Test
    fun rewrite_orphanTemp_isDeletedAndNoTempIsLeft() {
        vault.create(password)
        File(folder.root, "vault.bin.123.tmp").writeBytes(byteArrayOf(1))

        vault.freeEnvelopes(listOf(0))

        assertEquals(listOf("vault.bin"), folder.root.list()?.toList())
    }

    @Test
    fun create_orphanTemp_isDeleted() {
        val orphan = File(folder.root, "vault.bin.123.tmp").apply { writeBytes(byteArrayOf(1)) }

        vault.create(password)

        assertFalse(orphan.exists())
    }

    @Test
    fun create_undeletableOrphanTemp_returnsFailedAndNoVaultFile() {
        undeletableOrphanTemp()

        assertEquals(CreateResult.Failed, vault.create(password))
        assertFalse(file.exists())
    }

    @Test
    fun create_prepareStorageFails_publishesNoVaultAndWipesTheKeys() {
        var session: VaultSession? = null

        assertThrows(IllegalStateException::class.java) {
            vault.create(password) { created ->
                session = created.session
                error("storage cannot be prepared")
            }
        }

        assertFalse(file.exists())
        assertEquals(null, checkNotNull(session).payload())
    }

    @Test
    fun create_prepareStorageIoFailure_reportsFailedAndPublishesNoVault() {
        val result = vault.create(password) { throw IOException("no space left on device") }

        assertEquals(CreateResult.Failed, result)
        assertFalse(file.exists())
    }

    @Test
    fun create_prepareStorage_runsBeforeTheFileIsPublished() {
        var existedDuringPreparation = true

        val result = vault.create(password) { existedDuringPreparation = file.exists() }

        assertTrue(result is CreateResult.Created)
        assertFalse(existedDuringPreparation)
        assertTrue(file.exists())
    }

    @Test
    fun freeEnvelopes_severalEnvelopes_allStopOpeningInOneRewrite() {
        val root = created().session.envelopeIndex
        val first = otherPosition(root)
        val second = (0 until PasswordVault.ENVELOPE_COUNT).first { it != root && it != first }
        vault.sealEnvelope(first, otherPassword, payload(slot = 1))
        vault.sealEnvelope(second, "third".toCharArray(), payload(slot = 2))

        vault.freeEnvelopes(listOf(first, second))

        assertEquals(UnlockResult.WrongPassword, vault.unlock(otherPassword))
        assertEquals(UnlockResult.WrongPassword, vault.unlock("third".toCharArray()))
        assertTrue(vault.unlock(password) is UnlockResult.Success)
    }

    @Test
    fun secretFieldsKey_anotherEnvelopeWithTheSharedKey_isTheSame() {
        val session = created().session
        val shared = checkNotNull(session.payload()).use { it.sharedKey.copyOf() }
        val other = otherPosition(session.envelopeIndex)
        vault.sealEnvelope(other, otherPassword, EnvelopePayload(1, ByteArray(EnvelopePayload.KEY_SIZE) { 5 }, shared))

        val otherSession = (vault.unlock(otherPassword) as UnlockResult.Success).session

        assertArrayEquals(session.secretFieldsKey(), otherSession.secretFieldsKey())
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
    fun subkeys_openedSession_distinctAnd32Bytes() {
        val keys = created().session.subkeys()

        assertEquals(2, keys.toSet().size)
        assertTrue(keys.all { it?.size == 32 })
    }

    @Test
    fun close_session_wipesKeysAndGettersReturnNull() {
        val slotKey = ByteArray(32) { 7 }
        val sharedKey = ByteArray(32) { 8 }
        val session = VaultSession(0, EnvelopePayload(0, slotKey, sharedKey))
        assertNotNull(session.secretFieldsKey())

        session.close()
        session.close()

        assertTrue(slotKey.all { it == 0.toByte() })
        assertTrue(sharedKey.all { it == 0.toByte() })
        assertEquals(listOf(null, null), session.subkeys())
        assertEquals(null, session.payload())
    }

    @Test
    fun payload_closingTheCopy_leavesTheSessionUsable() {
        val session = created().session
        val keysBefore = session.subkeys()

        checkNotNull(session.payload()).close()

        assertEquals(keysBefore, session.subkeys())
    }

    @Test
    fun unlock_defaultParams_opensTheSameEnvelope() {
        val defaultVault = PasswordVault(file)
        val created = (defaultVault.create(password) as CreateResult.Created).session.fields()

        assertEquals(created, (defaultVault.unlock(password) as UnlockResult.Success).session.fields())
        assertEquals(UnlockResult.WrongPassword, defaultVault.unlock(otherPassword))
    }

    private fun created(): CreateResult.Created = vault.create(password) as CreateResult.Created

    private fun assertAllocation(root: Int, pool: Set<Int>, positions: Int) {
        assertFalse(root in pool)
        assertTrue(root in 0 until positions)
        assertTrue(pool.all { it in 0 until positions })
        assertTrue("decoys: ${positions - 1 - pool.size}", positions - 1 - pool.size in 3..8)
    }

    private fun otherPosition(position: Int): Int = (position + 1) % PasswordVault.ENVELOPE_COUNT

    private fun payload(slot: Int) = EnvelopePayload(slot, ByteArray(32) { 1 }, ByteArray(32) { 2 })

    private fun envelopes(): List<ByteArray> = checkNotNull(VaultFileFormat.decode(file.readBytes())).envelopes

    private fun changedEnvelopes(before: List<ByteArray>, after: List<ByteArray>): List<Int> =
        before.indices.filterNot { before[it].contentEquals(after[it]) }

    private fun legacyFile(): ByteArray = "PCKV".toByteArray() + 1 + ByteArray(86)

    // A non-empty directory makes Files.delete fail deterministically on every OS.
    private fun undeletableOrphanTemp() {
        File(folder.root, "vault.bin.x.tmp").apply { mkdir() }.resolve("content").writeBytes(byteArrayOf(1))
    }

    private data class Fields(val slot: Int, val slotKey: List<Byte>, val sharedKey: List<Byte>)

    private fun VaultSession.fields(): Fields =
        checkNotNull(payload()).let { Fields(it.slot, it.slotKey.toList(), it.sharedKey.toList()) }

    private fun VaultSession.subkeys(): List<List<Byte>?> =
        listOf(secretFieldsKey(), sharedNamesKey()).map { it?.toList() }

    private companion object {
        const val VERSION_BYTE_INDEX = 4
    }
}
