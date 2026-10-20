package cash.p.terminal.core.storage

import io.horizontalsystems.core.IEncryptionManager

/** Keeps stored secret columns readable so assertions can compare them to the input. */
class PlainTextEncryptionManager : IEncryptionManager {
    override fun encrypt(data: String) = data
    override fun decrypt(data: String) = data
}
