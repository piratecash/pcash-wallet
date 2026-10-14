package cash.p.terminal.vault

import java.nio.ByteBuffer

internal class VaultSlot(val params: Argon2Params, val salt: ByteArray, val sealedMasterKey: ByteArray)

internal class VaultFile(val slots: List<VaultSlot>)

internal object VaultFileFormat {
    const val SALT_SIZE = 16
    const val MASTER_KEY_SIZE = 32

    private const val VERSION: Byte = 1
    private const val KDF_ARGON2ID: Byte = 1
    private const val SLOT_HEADER_SIZE = 26
    private const val SEALED_KEY_SIZE = AesGcm.NONCE_SIZE + MASTER_KEY_SIZE + AesGcm.TAG_SIZE
    private const val SLOT_SIZE = SLOT_HEADER_SIZE + SEALED_KEY_SIZE
    private const val MAX_SLOTS = 8
    private val HEADER = "PCKV".toByteArray(Charsets.US_ASCII) + VERSION

    fun decode(bytes: ByteArray): VaultFile? {
        val slotsSize = bytes.size - HEADER.size
        if (slotsSize < SLOT_SIZE || slotsSize % SLOT_SIZE != 0 || slotsSize / SLOT_SIZE > MAX_SLOTS) return null
        if (!bytes.copyOfRange(0, HEADER.size).contentEquals(HEADER)) return null

        val buffer = ByteBuffer.wrap(bytes, HEADER.size, slotsSize)
        return VaultFile(List(slotsSize / SLOT_SIZE) { decodeSlot(buffer) ?: return null })
    }

    fun encode(file: VaultFile): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER.size + file.slots.size * SLOT_SIZE).put(HEADER)
        file.slots.forEach { buffer.put(slotHeader(it.params, it.salt)).put(it.sealedMasterKey) }
        return buffer.array()
    }

    /** No slot index: a slot can be added later without re-sealing the others. */
    fun slotAad(params: Argon2Params, salt: ByteArray): ByteArray = HEADER + slotHeader(params, salt)

    private fun decodeSlot(buffer: ByteBuffer): VaultSlot? {
        if (buffer.get() != KDF_ARGON2ID) return null
        val memoryKib = buffer.int
        val iterations = buffer.int
        val parallelism = buffer.get().toInt()
        val params = Argon2Params.ofOrNull(memoryKib, iterations, parallelism) ?: return null
        val salt = ByteArray(SALT_SIZE).also(buffer::get)
        return VaultSlot(params, salt, ByteArray(SEALED_KEY_SIZE).also(buffer::get))
    }

    private fun slotHeader(params: Argon2Params, salt: ByteArray): ByteArray =
        ByteBuffer.allocate(SLOT_HEADER_SIZE)
            .put(KDF_ARGON2ID)
            .putInt(params.memoryKib)
            .putInt(params.iterations)
            .put(params.parallelism.toByte())
            .put(salt)
            .array()
}
