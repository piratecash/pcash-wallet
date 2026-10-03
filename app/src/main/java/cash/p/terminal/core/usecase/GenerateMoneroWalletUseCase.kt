package cash.p.terminal.core.usecase

import android.content.Context
import com.piratecash.monero.MoneroWalletFiles

class GenerateMoneroWalletUseCase(
    private val appContext: Context
) {
    operator fun invoke(): String? {
        val walletName = generateWalletName()
        val walletFile = MoneroWalletFiles.file(appContext, walletName)

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
