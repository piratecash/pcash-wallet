package cash.p.terminal.wallet.useCases

import android.content.Context
import cash.p.terminal.wallet.Account
import com.m2049r.xmrwallet.util.KeyStoreHelper
import com.piratecash.monero.MoneroWalletFiles as MoneroKitWalletFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class RemoveMoneroWalletFilesUseCase(
    private val appContext: Context,
    private val getMoneroWalletFilesNameUseCase: IGetMoneroWalletFilesNameUseCase
) : IRemoveMoneroWalletFilesUseCase {

    override suspend operator fun invoke(account: Account): Boolean = withContext(Dispatchers.IO) {
        getMoneroWalletFilesNameUseCase(account)?.let { walletInnerName ->
            invoke(walletInnerName)
        } ?: run {
            false
        }
    }

    override suspend operator fun invoke(walletInnerName: String): Boolean = withContext(Dispatchers.IO) {
        val file = MoneroKitWalletFiles.file(appContext, walletInnerName)
        deleteWallet(file)
    }

    private fun deleteWallet(walletFile: File): Boolean {
        val success = MoneroWalletFiles(walletFile).delete()
        KeyStoreHelper.removeWalletUserPass(appContext, walletFile.name)
        return success
    }
}
