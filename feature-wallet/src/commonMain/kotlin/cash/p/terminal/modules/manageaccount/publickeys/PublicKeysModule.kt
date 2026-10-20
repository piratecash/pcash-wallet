package cash.p.terminal.modules.manageaccount.publickeys

import cash.p.terminal.modules.address.ZcashKeyParser
import cash.p.terminal.modules.manageaccount.showextendedkey.ShowExtendedKeyModule.DisplayKeyType.AccountPublicKey
import io.horizontalsystems.hdwalletkit.HDExtendedKey

object PublicKeysModule {
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
