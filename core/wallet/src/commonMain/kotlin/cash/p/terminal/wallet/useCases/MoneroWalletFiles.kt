package cash.p.terminal.wallet.useCases

import java.io.File

class MoneroWalletFiles(walletFile: File) {
    val cache = walletFile
    val keys = walletFile.resolveSibling("${walletFile.name}.keys")
    val address = walletFile.resolveSibling("${walletFile.name}.address.txt")
    val required = listOf(cache, keys)
    val all = required + address

    /** Missing cache and address files are fine; a missing keys file is a failure. */
    fun delete(): Boolean {
        var success = true
        if (cache.exists()) {
            success = cache.delete()
        }
        success = keys.delete() && success
        if (address.exists()) {
            success = address.delete() && success
        }
        return success
    }
}
