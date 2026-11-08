package cash.p.terminal.vault

import java.nio.ByteBuffer

internal class VaultFile(val params: Argon2Params, val salt: ByteArray, val envelopes: List<ByteArray>)

/** `[magic+version][kdf+params][shared salt][32 envelopes]`; a free envelope is random bytes of the same size. */
internal object VaultFileFormat {
    const val SALT_SIZE = 16
    private const val PAYLOAD_SIZE = Byte.SIZE_BYTES + 2 * EnvelopePayload.KEY_SIZE
    const val ENVELOPE_SIZE = AesGcm.NONCE_SIZE + PAYLOAD_SIZE + AesGcm.TAG_SIZE

    private const val KDF_ARGON2ID: Byte = 1
    private const val PARAMS_SIZE = 10
    private val MAGIC = "PCKV".toByteArray(Charsets.US_ASCII)
    private val LEGACY_HEADER = MAGIC + 1
    private val HEADER = MAGIC + 2
    private val FILE_SIZE = HEADER.size + PARAMS_SIZE + SALT_SIZE + PasswordVault.ENVELOPE_COUNT * ENVELOPE_SIZE

    /** A version 2 file is always [FILE_SIZE]: the same size with a damaged version byte is corruption, not legacy. */
    fun isLegacy(bytes: ByteArray): Boolean =
        bytes.size != FILE_SIZE &&
            bytes.size >= LEGACY_HEADER.size &&
            bytes.copyOfRange(0, LEGACY_HEADER.size).contentEquals(LEGACY_HEADER)

    fun decode(bytes: ByteArray): VaultFile? {
        if (bytes.size != FILE_SIZE || !bytes.copyOfRange(0, HEADER.size).contentEquals(HEADER)) return null

        val buffer = ByteBuffer.wrap(bytes, HEADER.size, bytes.size - HEADER.size)
        if (buffer.get() != KDF_ARGON2ID) return null
        val memoryKib = buffer.int
        val iterations = buffer.int
        val parallelism = buffer.get().toInt()
        val params = Argon2Params.ofOrNull(memoryKib, iterations, parallelism) ?: return null
        val salt = ByteArray(SALT_SIZE).also(buffer::get)
        val envelopes = List(PasswordVault.ENVELOPE_COUNT) { ByteArray(ENVELOPE_SIZE).also(buffer::get) }
        return VaultFile(params, salt, envelopes)
    }

    fun encode(file: VaultFile): ByteArray {
        val buffer = ByteBuffer.allocate(FILE_SIZE).put(prefix(file.params, file.salt))
        file.envelopes.forEach(buffer::put)
        return buffer.array()
    }

    /** The index keeps an envelope from opening at any position other than the one it was sealed for. */
    fun envelopeAad(params: Argon2Params, salt: ByteArray, index: Int): ByteArray =
        prefix(params, salt) + index.toByte()

    fun encodePayload(payload: EnvelopePayload): ByteArray {
        require(payload.slot in 0 until PasswordVault.SLOT_COUNT) { "Slot ${payload.slot} is out of range" }
        require(listOf(payload.slotKey, payload.sharedKey).all { it.size == EnvelopePayload.KEY_SIZE }) {
            "Envelope keys must be ${EnvelopePayload.KEY_SIZE} bytes"
        }
        return ByteBuffer.allocate(PAYLOAD_SIZE)
            .put(payload.slot.toByte())
            .put(payload.slotKey)
            .put(payload.sharedKey)
            .array()
    }

    fun decodePayload(plaintext: ByteArray): EnvelopePayload {
        val buffer = ByteBuffer.wrap(plaintext)
        val slot = buffer.get().toInt()
        val slotKey = ByteArray(EnvelopePayload.KEY_SIZE).also(buffer::get)
        return EnvelopePayload(slot, slotKey, ByteArray(EnvelopePayload.KEY_SIZE).also(buffer::get))
    }

    private fun prefix(params: Argon2Params, salt: ByteArray): ByteArray =
        ByteBuffer.allocate(HEADER.size + PARAMS_SIZE + SALT_SIZE)
            .put(HEADER)
            .put(KDF_ARGON2ID)
            .putInt(params.memoryKib)
            .putInt(params.iterations)
            .put(params.parallelism.toByte())
            .put(salt)
            .array()
}
