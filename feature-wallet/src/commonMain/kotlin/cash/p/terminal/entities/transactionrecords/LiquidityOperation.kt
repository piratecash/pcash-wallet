package cash.p.terminal.entities.transactionrecords

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.transactions_liquidity_add_failed
import cash.p.terminal.resources.transactions_liquidity_added
import cash.p.terminal.resources.transactions_liquidity_remove_failed
import cash.p.terminal.resources.transactions_liquidity_removed
import org.jetbrains.compose.resources.StringResource

data class LiquidityOperation(val direction: Direction, val failed: Boolean) {

    enum class Direction { Add, Remove }

    val title: StringResource
        get() = when (direction) {
            Direction.Add -> if (failed) Res.string.transactions_liquidity_add_failed else Res.string.transactions_liquidity_added
            Direction.Remove -> if (failed) Res.string.transactions_liquidity_remove_failed else Res.string.transactions_liquidity_removed
        }
}
