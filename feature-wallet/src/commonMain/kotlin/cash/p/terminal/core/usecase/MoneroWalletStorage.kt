package cash.p.terminal.core.usecase

import cash.p.terminal.core.StorageLockedException
import co.touchlab.kermit.Logger
import java.io.File

interface MoneroWalletStorage {
    fun walletsRoot(): File
    fun newWalletPassword(): String
}

/** The storage can close under a running request: that ends the request, not the app. */
fun MoneroWalletStorage.walletsRootOrNull(): File? = try {
    walletsRoot()
} catch (e: StorageLockedException) {
    Logger.withTag("MoneroWalletStorage").w(e) { "The storage closed under a Monero wallet request" }
    null
}
