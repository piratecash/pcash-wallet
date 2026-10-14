package cash.p.terminal.wallet

import cash.p.terminal.resources.Balance_Sort_24hPriceChange
import cash.p.terminal.resources.Balance_Sort_Az
import cash.p.terminal.resources.Balance_Sort_Balance
import cash.p.terminal.resources.Res
import org.jetbrains.compose.resources.StringResource

sealed class BalanceSortType {
    object Name : BalanceSortType()
    object Value : BalanceSortType()
    object PercentGrowth : BalanceSortType()

    fun getTitleRes(): StringResource = when (this) {
        Value -> Res.string.Balance_Sort_Balance
        Name -> Res.string.Balance_Sort_Az
        PercentGrowth -> Res.string.Balance_Sort_24hPriceChange
    }

    fun getAsString(): String = when (this) {
        Value -> "value"
        Name -> "name"
        PercentGrowth -> "percent_growth"
    }

    companion object {
        fun getTypeFromString(value: String): BalanceSortType = when (value) {
            "value" -> Value
            "percent_growth" -> PercentGrowth
            else -> Name
        }
    }
}
