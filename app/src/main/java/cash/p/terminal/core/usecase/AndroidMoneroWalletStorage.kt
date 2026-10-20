package cash.p.terminal.core.usecase

import android.content.Context
import com.m2049r.xmrwallet.util.KeyStoreHelper
import com.piratecash.monero.MoneroWalletFiles
import java.io.File

class AndroidMoneroWalletStorage(private val context: Context) : MoneroWalletStorage {
    override fun walletsRoot(): File = MoneroWalletFiles.root(context)

    override fun newWalletPassword(): String = KeyStoreHelper.getCrazyPass(context, "")
}
