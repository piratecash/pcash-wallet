package cash.p.terminal.shared.desktop.vault

import io.horizontalsystems.sqlcipher.SqlCipherDriver

interface SqlCipherDriverFactory {
    /** The driver keeps its own copy of [key] and wipes that copy on close. */
    fun create(key: ByteArray): SqlCipherDriver
}

class DefaultSqlCipherDriverFactory : SqlCipherDriverFactory {
    override fun create(key: ByteArray): SqlCipherDriver = SqlCipherDriver(key)
}
