package cash.p.terminal.modules.manageaccount.privatekeys

import cash.p.terminal.modules.manageaccount.showextendedkey.ShowExtendedKeyModule
import io.horizontalsystems.hdwalletkit.HDExtendedKey

object PrivateKeysModule {
    data class ViewState(
        val evmPrivateKey: String? = null,
        val bip32RootKey: ExtendedKey? = null,
        val accountExtendedPrivateKey: ExtendedKey? = null,
        val stellarSecretKey: String? = null,
        val hasZcashKeys: Boolean = false,
    )

    data class ExtendedKey(
        val hdKey: HDExtendedKey,
        val displayKeyType: ShowExtendedKeyModule.DisplayKeyType
    )
}
