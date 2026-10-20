package cash.p.terminal.core.usecase

import java.io.File

class GenerateMoneroWalletUseCase(
    private val moneroWalletStorage: MoneroWalletStorage
) {
    operator fun invoke(): String? {
        val walletName = generateWalletName()
        val walletFile = File(moneroWalletStorage.walletsRoot(), walletName)

        return if (!walletFile.exists()) {
            walletName
        } else {
            null
        }
    }

    private fun generateWalletName(): String {
        val timestamp = System.currentTimeMillis()
        return "wallet_$timestamp"
    }
}
