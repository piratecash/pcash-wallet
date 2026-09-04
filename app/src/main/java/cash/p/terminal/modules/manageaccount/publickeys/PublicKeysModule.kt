package cash.p.terminal.modules.manageaccount.publickeys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.App
import cash.p.terminal.modules.address.ZcashKeyParser
import cash.p.terminal.wallet.Account
import cash.p.terminal.modules.manageaccount.showextendedkey.ShowExtendedKeyModule.DisplayKeyType.AccountPublicKey
import io.horizontalsystems.hdwalletkit.HDExtendedKey

object PublicKeysModule {

    class Factory(private val account: Account) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PublicKeysViewModel(account, App.evmBlockchainManager) as T
        }
    }

    data class ViewState(
        val evmAddress: String? = null,
        val extendedPublicKey: ExtendedPublicKey? = null,
        val zcashViewKey: ZcashViewKey? = null,
        val zcashViewKeyFailed: Boolean = false
    )

    enum class ZcashViewKeyKind { Unified, Sapling, Transparent }

    /** A transparent-only account has no UFVK to export — the SDK hands back a bare xpub. */
    data class ZcashViewKey(val key: String) {
        val kind: ZcashViewKeyKind
            get() = when {
                ZcashKeyParser.isUfvk(key) -> ZcashViewKeyKind.Unified
                ZcashKeyParser.isSaplingViewingKeyFormat(key) -> ZcashViewKeyKind.Sapling
                else -> ZcashViewKeyKind.Transparent
            }
    }

    data class ExtendedPublicKey(
        val hdKey: HDExtendedKey,
        val accountPublicKey: AccountPublicKey
    )
}