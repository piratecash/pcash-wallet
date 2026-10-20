package cash.p.terminal.core.usecase

import java.io.File

interface MoneroWalletStorage {
    fun walletsRoot(): File
    fun newWalletPassword(): String
}
