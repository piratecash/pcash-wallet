package cash.p.terminal.wallet.entities

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.zcash_shielded_description
import cash.p.terminal.resources.zcash_transparent_description
import cash.p.terminal.resources.zcash_unified_description
import org.jetbrains.compose.resources.StringResource

enum class ZCashCoinType(val value: String) {
    Shielded("shielded"),
    Transparent("transparent"),
    Unified("unified");

    val title: String
        get() {
            return when (this) {
                Shielded -> "Shielded"
                Transparent -> "Transparent"
                Unified -> "Unified"
            }
        }

    val description: StringResource
        get() {
            return when (this) {
                Shielded -> Res.string.zcash_shielded_description
                Transparent -> Res.string.zcash_transparent_description
                Unified -> Res.string.zcash_unified_description
            }
        }

    companion object {
        private val map = values().associateBy(ZCashCoinType::value)

        fun fromString(value: String?): ZCashCoinType? = map[value]
    }
}
