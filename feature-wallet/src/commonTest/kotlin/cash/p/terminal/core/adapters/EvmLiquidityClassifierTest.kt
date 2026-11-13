package cash.p.terminal.core.adapters

import cash.p.terminal.entities.transactionrecords.LiquidityOperation.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal fun selector(value: Long) = ByteArray(4) { (value shr (24 - 8 * it)).toByte() }

internal fun word(value: Long) = ByteArray(24) + ByteArray(8) { (value shr (56 - 8 * it)).toByte() }

internal fun abiCall(selectorValue: Long, vararg words: ByteArray) =
    words.fold(selector(selectorValue)) { encoded, next -> encoded + next }

internal fun abiBytes(payload: ByteArray) =
    word(payload.size.toLong()) + payload + ByteArray((32 - payload.size % 32) % 32)

internal fun abiBytesArray(items: List<ByteArray>): ByteArray {
    val tails = items.map(::abiBytes)
    var offset = items.size * 32L
    val heads = tails.map { tail -> word(offset).also { offset += tail.size } }
    return (listOf(word(items.size.toLong())) + heads + tails).reduce { encoded, next -> encoded + next }
}

internal fun multicall(calls: List<ByteArray>) = selector(0xac9650d8) + word(0x20) + abiBytesArray(calls)

internal fun multicallWithDeadline(calls: List<ByteArray>) =
    selector(0x5ae401dc) + word(1_700_000_000) + word(0x40) + abiBytesArray(calls)

internal val mintCall = abiCall(0x88316456, *Array(11) { word(1) })

class EvmLiquidityClassifierTest {

    private val refundCall = selector(0x12210e8a)
    private val collectCall = abiCall(0xfc6f7865, word(7), word(1), word(1), word(1))
    private val burnCall = abiCall(0x42966c68, word(7))

    private fun decreaseCall(liquidity: Long) =
        abiCall(0x0c49ccbe, word(7), word(liquidity), word(0), word(0), word(1_700_000_000))

    private fun v4Payload(actions: ByteArray, params: List<ByteArray>): ByteArray {
        val encodedActions = abiBytes(actions)
        return word(0x40) + word(0x40L + encodedActions.size) + encodedActions + abiBytesArray(params)
    }

    private fun modifyLiquidities(actions: ByteArray, params: List<ByteArray>) =
        selector(0xdd46508f) + word(0x40) + word(1_700_000_000) + abiBytes(v4Payload(actions, params))

    private fun modifyWithoutUnlock(actions: ByteArray, params: List<ByteArray>) =
        selector(0x4afe393c) + v4Payload(actions, params)

    private fun v4Decrease(liquidity: Long) = word(7) + word(liquidity) + word(0) + word(0)

    private fun operation(input: ByteArray, label: String? = null) = EvmLiquidityClassifier.operation(input, label)

    @Test
    fun operation_v2AddSelectors_returnsAdd() {
        listOf(0xe8e33700, 0xf305d719).forEach {
            assertEquals(Direction.Add, operation(abiCall(it, word(1))))
        }
    }

    @Test
    fun operation_v2RemoveSelectors_returnsRemove() {
        listOf(0xbaa2abde, 0x02751cec, 0x2195995c, 0xded9382a, 0xaf2979eb, 0x5b0d5984).forEach {
            assertEquals(Direction.Remove, operation(abiCall(it, word(1))))
        }
    }

    @Test
    fun operation_v3MintAndIncrease_returnsAdd() {
        assertEquals(Direction.Add, operation(mintCall))
        assertEquals(Direction.Add, operation(abiCall(0x219f5d17, word(7), word(1))))
    }

    @Test
    fun operation_v3DecreaseWithLiquidity_returnsRemove() {
        assertEquals(Direction.Remove, operation(decreaseCall(98_607_321_246)))
    }

    @Test
    fun operation_v3DecreaseZeroLiquidity_returnsNull() {
        assertNull(operation(decreaseCall(0)))
    }

    @Test
    fun operation_v3DecreaseTruncatedBeforeLiquidity_returnsNull() {
        assertNull(operation(abiCall(0x0c49ccbe, word(7))))
    }

    @Test
    fun operation_collectOrBurnAlone_returnsNull() {
        assertNull(operation(collectCall))
        assertNull(operation(burnCall))
    }

    @Test
    fun operation_liveMintMulticall_returnsAdd() {
        assertEquals(Direction.Add, operation(multicall(listOf(mintCall, refundCall))))
    }

    @Test
    fun operation_liveDecreaseMulticall_returnsRemove() {
        val calls = listOf(
            decreaseCall(98_607_321_246),
            collectCall,
            abiCall(0x49404b7c, word(1), word(1)),
            abiCall(0xdf2ab5bb, word(1), word(1), word(1)),
        )

        assertEquals(Direction.Remove, operation(multicall(calls)))
    }

    @Test
    fun operation_eachMulticallSelector_unwrapsInnerCalls() {
        val bytes32Multicall = selector(0x1f0464d1) + word(1) + word(0x40) + abiBytesArray(listOf(mintCall))

        assertEquals(Direction.Add, operation(multicallWithDeadline(listOf(mintCall))))
        assertEquals(Direction.Add, operation(bytes32Multicall))
    }

    @Test
    fun operation_v4MintAndIncrease_returnsAdd() {
        val mint = modifyLiquidities(byteArrayOf(0x02, 0x0d), listOf(word(1), word(1)))
        val increase = modifyLiquidities(byteArrayOf(0x00), listOf(word(1)))

        assertEquals(Direction.Add, operation(mint))
        assertEquals(Direction.Add, operation(increase))
    }

    @Test
    fun operation_v4DecreaseWithLiquidity_returnsRemove() {
        assertEquals(Direction.Remove, operation(modifyLiquidities(byteArrayOf(0x01), listOf(v4Decrease(5)))))
    }

    @Test
    fun operation_v4DecreaseZeroLiquidity_returnsNull() {
        assertNull(operation(modifyLiquidities(byteArrayOf(0x01), listOf(v4Decrease(0)))))
    }

    @Test
    fun operation_v4Burn_returnsRemove() {
        assertEquals(Direction.Remove, operation(modifyLiquidities(byteArrayOf(0x03), listOf(word(1)))))
    }

    @Test
    fun operation_v4WithoutUnlock_readsActionsDirectly() {
        assertEquals(Direction.Add, operation(modifyWithoutUnlock(byteArrayOf(0x02), listOf(word(1)))))
    }

    @Test
    fun operation_v4OtherActionsOnly_returnsNull() {
        assertNull(operation(modifyLiquidities(byteArrayOf(0x0d, 0x11), listOf(word(1), word(1)))))
    }

    @Test
    fun operation_v4ActionsAndParamsDiffer_returnsNull() {
        assertNull(operation(modifyLiquidities(byteArrayOf(0x02, 0x03), listOf(word(1)))))
    }

    @Test
    fun operation_pancakeBinActions_followDirection() {
        assertEquals(Direction.Add, operation(modifyLiquidities(byteArrayOf(0x19), listOf(word(1)))))
        assertEquals(Direction.Add, operation(modifyLiquidities(byteArrayOf(0x1b), listOf(word(1)))))
        assertEquals(Direction.Remove, operation(modifyLiquidities(byteArrayOf(0x1a), listOf(word(1)))))
    }

    @Test
    fun operation_addAndRemoveInOneCall_returnsNull() {
        assertNull(operation(multicall(listOf(mintCall, decreaseCall(5)))))
        assertNull(operation(modifyLiquidities(byteArrayOf(0x02, 0x03), listOf(word(1), word(1)))))
    }

    @Test
    fun operation_addAndRemoveWithMatchingLabel_returnsNull() {
        assertNull(operation(multicall(listOf(mintCall, decreaseCall(5))), "Add Liquidity"))
    }

    @Test
    fun operation_addTogetherWithSwap_returnsAdd() {
        val swap = abiCall(0x04e45aaf, *Array(7) { word(1) })

        assertEquals(Direction.Add, operation(multicall(listOf(swap, mintCall))))
    }

    @Test
    fun operation_nestedMulticallWithinCap_returnsAdd() {
        assertEquals(Direction.Add, operation(multicall(listOf(multicall(listOf(mintCall))))))
    }

    @Test
    fun operation_nestedMulticallOverCap_returnsNull() {
        assertNull(operation(multicall(listOf(multicall(listOf(multicall(listOf(mintCall))))))))
    }

    @Test
    fun operation_truncatedMulticall_returnsNull() {
        assertNull(operation(multicall(listOf(mintCall)).copyOf(40)))
    }

    @Test
    fun operation_shorterThanSelector_returnsNull() {
        assertNull(operation(ByteArray(2)))
    }

    @Test
    fun operation_offsetBeyondBuffer_returnsNull() {
        assertNull(operation(selector(0xac9650d8) + word(0x10000)))
    }

    @Test
    fun operation_offsetWithHighBytesSet_returnsNull() {
        assertNull(operation(selector(0xac9650d8) + ByteArray(32) { 0xFF.toByte() }))
    }

    @Test
    fun operation_hugeArrayCount_returnsNull() {
        assertNull(operation(selector(0xac9650d8) + word(0x20) + word(Int.MAX_VALUE.toLong())))
    }

    @Test
    fun operation_elementsAliasingOnePayload_returnsNull() {
        val count = 64L
        val payload = abiBytes(mintCall + ByteArray(4000))
        val heads = List(count.toInt()) { word(count * 32) }
        val input = (listOf(selector(0xac9650d8), word(0x20), word(count)) + heads + payload)
            .reduce { encoded, next -> encoded + next }

        assertNull(operation(input))
    }

    @Test
    fun operation_recognisedLabels_followDirection() {
        listOf(
            "Add Liquidity", "Add Market Liquidity Dual", "Add Market Liquidity Single",
            "Add Native Liquidity", "Increase Liquidity", "Provide Liquidity",
        ).forEach { assertEquals(it, Direction.Add, operation(unknownCall, it)) }

        listOf(
            "Redeem Liquidity", "Remove Liquidity", "Remove Liquidity With Permit",
            "Remove Market Liquidity Dual", "Remove Market Liquidity Single",
        ).forEach { assertEquals(it, Direction.Remove, operation(unknownCall, it)) }
    }

    @Test
    fun operation_excludedLabels_returnsNull() {
        listOf(
            "Liquidation Call", "Liquidate", "Liquidate Borrow", "Liquid Exit",
            "Mint", "Burn", "Multicall", "Decrease Liquidity",
        ).forEach { assertNull(it, operation(unknownCall, it)) }
    }

    private val unknownCall = abiCall(0xdeadbeef, word(1))
}
