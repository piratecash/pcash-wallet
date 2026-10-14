package cash.p.terminal.vault

/** Takes ownership of [masterKey] and wipes it on [close]; getters return null once closed. */
class VaultSession internal constructor(private val masterKey: ByteArray) : AutoCloseable {
    private var closed = false

    fun appDatabaseKey(): ByteArray? = subkey("pcash-vault/app-db/v1")

    fun secretFieldsKey(): ByteArray? = subkey("pcash-vault/secret-fields/v1")

    fun kitKeysWrappingKey(): ByteArray? = subkey("pcash-vault/kit-keys/v1")

    override fun close() {
        closed = true
        masterKey.fill(0)
    }

    private fun subkey(info: String): ByteArray? =
        if (closed) null else Hkdf.expand(masterKey, info.toByteArray(Charsets.UTF_8), SUBKEY_SIZE)

    private companion object {
        const val SUBKEY_SIZE = 32
    }
}
