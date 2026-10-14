package cash.p.terminal.vault

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.SecureRandom

sealed interface CreateResult {
    class Created(val session: VaultSession) : CreateResult
    data object AlreadyExists : CreateResult
    data object Failed : CreateResult
}

sealed interface UnlockResult {
    class Success(val session: VaultSession) : UnlockResult
    data object WrongPassword : UnlockResult
    data object Corrupted : UnlockResult
}

sealed interface ChangePasswordResult {
    data object Success : ChangePasswordResult
    data object WrongPassword : ChangePasswordResult
    data object Corrupted : ChangePasswordResult
}

/** Not safe for concurrent [create]/[changePassword]: the caller serializes them. */
class PasswordVault(private val file: File, private val kdfParams: Argon2Params = Argon2Params.DEFAULT) {
    private val secureRandom = SecureRandom()
    private val directory: Path get() = file.toPath().toAbsolutePath().parent

    fun exists(): Boolean = file.exists()

    fun create(password: CharArray): CreateResult {
        val masterKey = randomBytes(VaultFileFormat.MASTER_KEY_SIZE)
        val result = try {
            deleteOrphanTemps()
            val bytes = VaultFileFormat.encode(VaultFile(listOf(sealSlot(masterKey, password))))
            // A hard link publishes the complete file and fails if the target exists, in one atomic step.
            writeAndPublish(bytes) { temp -> Files.createLink(file.toPath(), temp) }
            CreateResult.Created(VaultSession(masterKey))
        } catch (e: FileAlreadyExistsException) {
            CreateResult.AlreadyExists
        } catch (e: IOException) {
            CreateResult.Failed
        } catch (e: UnsupportedOperationException) {
            CreateResult.Failed
        }
        if (result !is CreateResult.Created) masterKey.fill(0)
        return result
    }

    fun unlock(password: CharArray): UnlockResult {
        val vault = readVault() ?: return UnlockResult.Corrupted
        val masterKey = password.withUtf8Bytes { bytes -> vault.slots.firstNotNullOfOrNull { openSlot(it, bytes) } }
        return masterKey?.let { UnlockResult.Success(VaultSession(it)) } ?: UnlockResult.WrongPassword
    }

    fun changePassword(old: CharArray, new: CharArray): ChangePasswordResult {
        val vault = readVault() ?: return ChangePasswordResult.Corrupted
        deleteOrphanTemps()
        val masterKeys = old.withUtf8Bytes { bytes -> vault.slots.map { openSlot(it, bytes) } }
        val masterKey = masterKeys.firstNotNullOfOrNull { it } ?: return ChangePasswordResult.WrongPassword
        try {
            val sameKey = masterKeys.map { it?.contentEquals(masterKey) == true }
            val slots = replaceSlots(vault.slots, sameKey, sealSlot(masterKey, new))
            writeAndPublish(VaultFileFormat.encode(VaultFile(slots))) { temp ->
                Files.move(temp, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }
            return ChangePasswordResult.Success
        } finally {
            masterKeys.forEach { it?.fill(0) }
        }
    }

    private fun readVault(): VaultFile? = try {
        VaultFileFormat.decode(Files.readAllBytes(file.toPath()))
    } catch (e: NoSuchFileException) {
        null
    }

    private fun sealSlot(masterKey: ByteArray, password: CharArray): VaultSlot {
        val salt = randomBytes(VaultFileFormat.SALT_SIZE)
        val kek = password.withUtf8Bytes { deriveKek(it, salt, kdfParams) }
        try {
            val sealed = AesGcm.encrypt(masterKey, kek, VaultFileFormat.slotAad(kdfParams, salt))
            return VaultSlot(kdfParams, salt, sealed)
        } finally {
            kek.fill(0)
        }
    }

    private fun openSlot(slot: VaultSlot, password: ByteArray): ByteArray? {
        val kek = deriveKek(password, slot.salt, slot.params)
        try {
            return AesGcm.decrypt(slot.sealedMasterKey, kek, VaultFileFormat.slotAad(slot.params, slot.salt))
        } finally {
            kek.fill(0)
        }
    }

    // Replaced slots collapse into the one new slot at the first replaced position; a slot holding
    // another master key is kept even if the old password opens it, or that key would be lost.
    private fun replaceSlots(slots: List<VaultSlot>, replaced: List<Boolean>, newSlot: VaultSlot) =
        slots.filterIndexed { index, _ -> !replaced[index] }
            .toMutableList()
            .apply { add(replaced.indexOf(true), newSlot) }

    private inline fun writeAndPublish(bytes: ByteArray, publish: (Path) -> Unit) {
        val temp = Files.createTempFile(directory, "${file.name}.", ".tmp")
        try {
            FileOutputStream(temp.toFile()).use {
                it.write(bytes)
                it.fd.sync()
            }
            publish(temp)
            syncDirectory()
        } finally {
            temp.toFile().delete()
        }
    }

    // Makes the published directory entry survive power loss.
    // Best effort: the file is already published, so a failure here must not report the operation as failed.
    // Windows cannot open a directory as a channel; NTFS journals directory entries itself.
    private fun syncDirectory() {
        try {
            FileChannel.open(directory, StandardOpenOption.READ).use { it.force(true) }
        } catch (e: IOException) {
            return
        }
    }

    // A temp left by a crash could still hold a slot of a password that has since been changed.
    private fun deleteOrphanTemps() {
        val isOrphan = { candidate: Path ->
            candidate.fileName.toString().let { it.startsWith("${file.name}.") && it.endsWith(".tmp") }
        }
        Files.newDirectoryStream(directory, isOrphan).use { orphans ->
            orphans.forEach(Files::deleteIfExists)
        }
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

    private inline fun <T> CharArray.withUtf8Bytes(block: (ByteArray) -> T): T {
        val bytes = toUtf8Bytes()
        try {
            return block(bytes)
        } finally {
            bytes.fill(0)
        }
    }
}
