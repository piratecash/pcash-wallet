package cash.p.terminal.wallet

import io.horizontalsystems.core.common.CommonParcelable
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Restore_Bip_Recommended
import io.horizontalsystems.core.Translator
import io.horizontalsystems.hdwalletkit.HDWallet
import io.horizontalsystems.core.common.CommonParcelize

@CommonParcelize
enum class Derivation(val value: String) : CommonParcelable {
    bip44("bip44"),
    bip49("bip49"),
    bip84("bip84"),
    bip86("bip86");

    val recommended: String
        get() = when (this) {
            bip84 -> " " + Translator.getString(Res.string.Restore_Bip_Recommended)
            else -> ""
        }

    val addressType: String
        get() = when (this) {
            bip44 -> "Legacy"
            bip49 -> "SegWit"
            bip84 -> "Native SegWit"
            bip86 -> "Taproot"
        }

    val rawName: String
        get() = when (this) {
            bip44 -> "BIP 44"
            bip49 -> "BIP 49"
            bip84 -> "BIP 84"
            bip86 -> "BIP 86"
        }

    val purpose: HDWallet.Purpose
        get() = when (this) {
            bip44 -> HDWallet.Purpose.BIP44
            bip49 -> HDWallet.Purpose.BIP49
            bip84 -> HDWallet.Purpose.BIP84
            bip86 -> HDWallet.Purpose.BIP86
        }

    val order: Int
        get() = when (this) {
            bip84 -> 0
            bip86 -> 1
            bip49 -> 2
            bip44 -> 3
        }

    companion object {
        val default = bip84
        private val map = values().associateBy(Derivation::value)

        fun fromString(value: String?): Derivation? = map[value]
    }
}
