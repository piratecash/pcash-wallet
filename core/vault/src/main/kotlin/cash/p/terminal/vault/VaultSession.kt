package cash.p.terminal.vault

/** The plaintext of one envelope; [close] wipes the keys. */
class EnvelopePayload(val slot: Int, val slotKey: ByteArray, val sharedKey: ByteArray) : AutoCloseable {
    override fun close() {
        slotKey.fill(0)
        sharedKey.fill(0)
    }

    companion object {
        const val KEY_SIZE = 32
    }
}

/** One opened envelope: takes ownership of [payload] and wipes it on [close]; getters return null once closed. */
class VaultSession internal constructor(val envelopeIndex: Int, private val payload: EnvelopePayload) : AutoCloseable {
    private var closed = false

    /** A copy the caller owns and closes. */
    fun payload(): EnvelopePayload? =
        if (closed) null else EnvelopePayload(payload.slot, payload.slotKey.copyOf(), payload.sharedKey.copyOf())

    /** From the key every level shares: secret fields live in account stores, which change levels without rewriting. */
    fun secretFieldsKey(): ByteArray? = subkey(payload.sharedKey, "pcash-vault/secret-fields/v1")

    /** Names what every level shares, so moving between levels changes no path. */
    fun sharedNamesKey(): ByteArray? = subkey(payload.sharedKey, "pcash-vault/shared-names")

    override fun close() {
        closed = true
        payload.close()
    }

    private fun subkey(key: ByteArray, info: String): ByteArray? =
        if (closed) null else Hkdf.expand(key, info.toByteArray(Charsets.UTF_8), SUBKEY_SIZE)

    private companion object {
        const val SUBKEY_SIZE = 32
    }
}
