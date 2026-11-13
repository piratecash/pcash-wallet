package cash.p.terminal.core.adapters

import cash.p.terminal.entities.transactionrecords.LiquidityOperation.Direction

internal object EvmLiquidityClassifier {

    private val ADD_SELECTORS = setOf(0xe8e33700L, 0xf305d719L, 0x88316456L, 0x219f5d17L)
    private val REMOVE_SELECTORS = setOf(0xbaa2abdeL, 0x02751cecL, 0x2195995cL, 0xded9382aL, 0xaf2979ebL, 0x5b0d5984L)
    private const val DECREASE_LIQUIDITY = 0x0c49ccbeL
    private const val MODIFY_LIQUIDITIES = 0xdd46508fL
    private const val MODIFY_LIQUIDITIES_WITHOUT_UNLOCK = 0x4afe393cL

    private val V4_ADD_ACTIONS = setOf(0x00, 0x02, 0x04, 0x05, 0x19, 0x1b)
    private val V4_REMOVE_ACTIONS = setOf(0x03, 0x1a)
    private const val V4_DECREASE_ACTION = 0x01
    private const val LIQUIDITY_WORD = 1

    private val LABEL_DIRECTIONS = listOf(
        "Add Liquidity", "Add Market Liquidity Dual", "Add Market Liquidity Single",
        "Add Native Liquidity", "Increase Liquidity", "Provide Liquidity",
    ).associateWith { Direction.Add } + listOf(
        "Redeem Liquidity", "Remove Liquidity", "Remove Liquidity With Permit",
        "Remove Market Liquidity Dual", "Remove Market Liquidity Single",
    ).associateWith { Direction.Remove }

    /** Add and Remove together are a rebalance, which has no single direction. */
    fun operation(input: ByteArray, methodLabel: String?): Direction? {
        val directions = flattenCalls(input).flatMap(::callDirections).toSet()
        return if (directions.isEmpty()) LABEL_DIRECTIONS[methodLabel] else directions.singleOrNull()
    }

    private fun callDirections(call: ByteArray): List<Direction> {
        val arguments = EvmCallInput(call, SELECTOR_SIZE)
        return when (call.selector) {
            in ADD_SELECTORS -> listOf(Direction.Add)
            in REMOVE_SELECTORS -> listOf(Direction.Remove)
            DECREASE_LIQUIDITY -> listOfNotNull(decreaseDirection(arguments))
            MODIFY_LIQUIDITIES -> arguments.bytes(0)?.let { v4Directions(EvmCallInput(it)) }.orEmpty()
            MODIFY_LIQUIDITIES_WITHOUT_UNLOCK -> v4Directions(arguments)
            else -> emptyList()
        }
    }

    /** A decrease of zero liquidity only collects fees. */
    private fun decreaseDirection(params: EvmCallInput): Direction? =
        Direction.Remove.takeIf { params.isWordZero(LIQUIDITY_WORD) == false }

    private fun v4Directions(payload: EvmCallInput): List<Direction> {
        val actions = payload.bytes(0) ?: return emptyList()
        val params = payload.bytesArray(1) ?: return emptyList()
        if (actions.size != params.size) return emptyList()
        return actions.indices.mapNotNull { v4Direction(actions[it].toInt(), params[it]) }
    }

    private fun v4Direction(action: Int, params: ByteArray): Direction? = when (action) {
        in V4_ADD_ACTIONS -> Direction.Add
        in V4_REMOVE_ACTIONS -> Direction.Remove
        V4_DECREASE_ACTION -> decreaseDirection(EvmCallInput(params))
        else -> null
    }
}
