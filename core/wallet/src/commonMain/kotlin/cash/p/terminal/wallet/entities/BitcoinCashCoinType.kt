package cash.p.terminal.wallet.entities

import cash.p.terminal.resources.CoinSettings_BitcoinCashCoinType_Type0_Description
import cash.p.terminal.resources.CoinSettings_BitcoinCashCoinType_Type145_Description
import cash.p.terminal.resources.Res
import org.jetbrains.compose.resources.StringResource

enum class BitcoinCashCoinType(val value: String) {
    type0("type0"), type145("type145");

    val title: String
        get() {
            return when (this) {
                type0 -> "Legacy (Type 0)"
                type145 -> "CashAddress (Type 145)"
            }
        }

    val description: StringResource
        get() {
            return when (this) {
                type0 -> Res.string.CoinSettings_BitcoinCashCoinType_Type0_Description
                type145 -> Res.string.CoinSettings_BitcoinCashCoinType_Type145_Description
            }
        }

        companion object {
            val default = type145
            private val map = values().associateBy(BitcoinCashCoinType::value)

            fun fromString(value: String?): BitcoinCashCoinType? = map[value]
        }
}
