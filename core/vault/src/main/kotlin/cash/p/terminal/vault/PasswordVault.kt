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
    /** The decoys are the positions in neither the session nor the pools: they are not recorded anywhere. */
    class Created(val session: VaultSession, val slotPool: Set<Int>, val envelopePool: Set<Int>) : CreateResult
    data object AlreadyExists : CreateResult
    data object Failed : CreateResult
}

sealed interface UnlockResult {
    class Success(val session: VaultSession) : UnlockResult
    data object WrongPassword : UnlockResult
    data object Corrupted : UnlockResult
    data object LegacyFormat : UnlockResult
}

/** Not safe for concurrent [create]/[sealEnvelope]/[freeEnvelope]: the caller serializes them. */
class PasswordVault internal constructor(
    private val file: File,
    private val kdfParams: Argon2Params,
    private val crypto: VaultCrypto,
) {
    constructor(file: File, kdfParams: Argon2Params = Argon2Params.DEFAULT) : this(file, kdfParams, DefaultVaultCrypto)

    private val secureRandom = SecureRandom()
    private val directory: Path get() = file.toPath().toAbsolutePath().parent

    fun exists(): Boolean = file.exists()

    /**
     * [prepareStorage] runs before the file is published: whatever goes wrong in it leaves no vault to unlock, so
     * a retry starts over. An [IOException] from it reports [CreateResult.Failed].
     */
    fun create(password: CharArray, prepareStorage: (CreateResult.Created) -> Unit = {}): CreateResult {
        val slots = allocate(SLOT_COUNT)
        val envelopes = allocate(ENVELOPE_COUNT)
        val payload = EnvelopePayload(slots.root, randomKey(), randomKey())
        val created = CreateResult.Created(VaultSession(envelopes.root, payload), slots.pool, envelopes.pool)
        var result: CreateResult = CreateResult.Failed
        try {
            prepareStorage(created)
            deleteOrphanTemps()
            val bytes = VaultFileFormat.encode(newVaultFile(envelopes.root, password, payload))
            // A hard link publishes the complete file and fails if the target exists, in one atomic step.
            writeAndPublish(bytes) { temp -> Files.createLink(file.toPath(), temp) }
            result = created
        } catch (e: FileAlreadyExistsException) {
            result = CreateResult.AlreadyExists
        } catch (e: IOException) {
            result = CreateResult.Failed
        } catch (e: UnsupportedOperationException) {
            result = CreateResult.Failed
        } finally {
            if (result !== created) created.session.close()
        }
        return result
    }

    /** Runs Argon2 once and tries every envelope whichever one opens, so the work does not reveal the position. */
    fun unlock(password: CharArray): UnlockResult {
        val bytes = readBytes() ?: return UnlockResult.Corrupted
        if (VaultFileFormat.isLegacy(bytes)) return UnlockResult.LegacyFormat
        val vault = VaultFileFormat.decode(bytes) ?: return UnlockResult.Corrupted

        val kek = password.withUtf8Bytes { crypto.deriveKek(it, vault.salt, vault.params) }
        val plaintexts = try {
            vault.envelopes.mapIndexed { index, envelope ->
                crypto.openEnvelope(envelope, kek, VaultFileFormat.envelopeAad(vault.params, vault.salt, index))
            }
        } finally {
            kek.fill(0)
        }
        val index = plaintexts.indexOfFirst { it != null }
        val session = plaintexts.getOrNull(index)?.let { VaultSession(index, VaultFileFormat.decodePayload(it)) }
        plaintexts.forEach { it?.fill(0) }
        return session?.let(UnlockResult::Success) ?: UnlockResult.WrongPassword
    }

    /** Throws [IOException] when the file cannot answer: "not in use" must not be assumed. */
    fun opensAny(password: CharArray): Boolean = when (val result = unlock(password)) {
        is UnlockResult.Success -> {
            result.session.close()
            true
        }
        UnlockResult.WrongPassword -> false
        UnlockResult.Corrupted, UnlockResult.LegacyFormat -> throw IOException("Vault file is unusable")
    }

    /** Takes the Argon2 parameters and salt from the file. Throws [IOException] and keeps the file on failure. */
    fun sealEnvelope(index: Int, password: CharArray, payload: EnvelopePayload) =
        replaceEnvelopes(listOf(index)) { vault -> sealedEnvelope(vault.params, vault.salt, index, password, payload) }

    /** One atomic rewrite: all of them stop opening together. Throws [IOException] and keeps the file on failure. */
    fun freeEnvelopes(indices: Collection<Int>) =
        replaceEnvelopes(indices) { randomBytes(VaultFileFormat.ENVELOPE_SIZE) }

    private inline fun replaceEnvelopes(indices: Collection<Int>, envelope: (VaultFile) -> ByteArray) {
        indices.forEach { require(it in 0 until ENVELOPE_COUNT) { "Envelope $it is out of range" } }
        val vault = readBytes()?.let(VaultFileFormat::decode) ?: throw IOException("Vault file is unusable")
        val envelopes = vault.envelopes.toMutableList().apply { indices.forEach { set(it, envelope(vault)) } }
        deleteOrphanTemps()
        writeAndPublish(VaultFileFormat.encode(VaultFile(vault.params, vault.salt, envelopes))) { temp ->
            Files.move(temp, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun readBytes(): ByteArray? = try {
        Files.readAllBytes(file.toPath())
    } catch (e: NoSuchFileException) {
        null
    }

    private fun newVaultFile(rootEnvelope: Int, password: CharArray, payload: EnvelopePayload): VaultFile {
        val salt = randomBytes(VaultFileFormat.SALT_SIZE)
        val envelopes = List(ENVELOPE_COUNT) { randomBytes(VaultFileFormat.ENVELOPE_SIZE) }.toMutableList()
        envelopes[rootEnvelope] = sealedEnvelope(kdfParams, salt, rootEnvelope, password, payload)
        return VaultFile(kdfParams, salt, envelopes)
    }

    private fun sealedEnvelope(
        params: Argon2Params,
        salt: ByteArray,
        index: Int,
        password: CharArray,
        payload: EnvelopePayload,
    ): ByteArray {
        val plaintext = VaultFileFormat.encodePayload(payload)
        val kek = password.withUtf8Bytes { crypto.deriveKek(it, salt, params) }
        try {
            return AesGcm.encrypt(plaintext, kek, VaultFileFormat.envelopeAad(params, salt, index))
        } finally {
            kek.fill(0)
            plaintext.fill(0)
        }
    }

    private fun allocate(positions: Int): Allocation {
        val shuffled = (0 until positions).shuffled(secureRandom)
        val decoys = DECOYS_MIN + secureRandom.nextInt(DECOYS_MAX - DECOYS_MIN + 1)
        return Allocation(root = shuffled.first(), pool = shuffled.drop(1 + decoys).toSet())
    }

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

    // A temp left by a crash could still hold an envelope of a password that has since been changed.
    private fun deleteOrphanTemps() {
        val isOrphan = { candidate: Path ->
            candidate.fileName.toString().let { it.startsWith("${file.name}.") && it.endsWith(".tmp") }
        }
        Files.newDirectoryStream(directory, isOrphan).use { orphans ->
            orphans.forEach(Files::deleteIfExists)
        }
    }

    private fun randomKey(): ByteArray = randomBytes(EnvelopePayload.KEY_SIZE)

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

    private inline fun <T> CharArray.withUtf8Bytes(block: (ByteArray) -> T): T {
        val bytes = toUtf8Bytes()
        try {
            return block(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private class Allocation(val root: Int, val pool: Set<Int>)

    companion object {
        const val ENVELOPE_COUNT = 32
        const val SLOT_COUNT = 24

        private const val DECOYS_MIN = 3
        private const val DECOYS_MAX = 8
    }
}
