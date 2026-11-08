package cash.p.terminal.core.adapters

import cash.p.terminal.entities.transactionrecords.LiquidityOperation
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import io.horizontalsystems.erc20kit.events.TransferEventInstance
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.uniswapkit.v3.router.ExactInputSingleMethod
import io.horizontalsystems.uniswapkit.v3.router.ExactOutputSingleMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class EvmTransactionConverterSwapCallTest {

    private val fixture = EvmConverterFixture()
    private val oneToken = BigInteger.TEN.pow(18)

    private fun exactInputCall(
        from: EvmConverterFixture = fixture,
    ) = ExactInputSingleMethod(
        tokenIn = from.tokenA,
        tokenOut = from.tokenB,
        fee = BigInteger.valueOf(500),
        recipient = from.user,
        amountIn = oneToken,
        amountOutMinimum = BigInteger.ONE,
        sqrtPriceLimitX96 = BigInteger.ZERO,
    ).encodedABI()

    private fun exactOutputCall(recipient: Address) = ExactOutputSingleMethod(
        tokenIn = fixture.tokenA,
        tokenOut = fixture.tokenB,
        fee = BigInteger.valueOf(500),
        recipient = recipient,
        amountOut = oneToken * BigInteger.TWO,
        amountInMaximum = oneToken * BigInteger.TEN,
        sqrtPriceLimitX96 = BigInteger.ZERO,
    ).encodedABI()

    private val v2RouteCall = abiCall(0x472b43f3, word(1), word(1), word(0x80), word(1), word(0))

    private fun spent(tokens: Long, fixture: EvmConverterFixture = this.fixture) =
        fixture.transfer(fixture.tokenA, fixture.user, fixture.pool, tokens)

    private fun received(tokens: Long, fixture: EvmConverterFixture = this.fixture) =
        fixture.transfer(fixture.tokenB, fixture.pool, fixture.user, tokens)

    private fun assertAmount(expected: String, actual: BigDecimal?) {
        assertEquals("expected $expected, got $actual", 0, BigDecimal(expected).compareTo(actual))
    }

    private fun assertPlainContractCall(record: EvmTransactionRecord) {
        assertEquals(TransactionRecordType.EVM_CONTRACT_CALL, record.transactionRecordType)
        assertFalse(record.swapDecodedFromCall)
    }

    @Test
    fun transactionRecord_deadlineMulticallWithOneExactLeg_isDecoratedSwap() {
        val record = fixture.record(
            multicallWithDeadline(listOf(exactInputCall())),
            events = listOf(spent(1), received(2)),
        )

        assertEquals(TransactionRecordType.EVM_SWAP, record.transactionRecordType)
        assertTrue(record.swapDecodedFromCall)
        assertEquals(fixture.router.eip55, record.exchangeAddress)
        assertAmount("-1", record.valueIn?.decimalValue)
        assertAmount("2", record.valueOut?.decimalValue)
        assertNull(record.recipient)
    }

    @Test
    fun transactionRecord_exactOutputToSender_isDecoratedSwap() {
        val record = fixture.record(
            multicallWithDeadline(listOf(exactOutputCall(fixture.user))),
            events = listOf(spent(1), received(2)),
        )

        assertEquals(TransactionRecordType.EVM_SWAP, record.transactionRecordType)
        assertTrue(record.swapDecodedFromCall)
        assertAmount("-1", record.valueIn?.decimalValue)
        assertAmount("2", record.valueOut?.decimalValue)
    }

    @Test
    fun transactionRecord_exactOutputToOtherRecipient_staysContractCallWithActualSpend() {
        val otherRecipient = Address("0x7777777777777777777777777777777777777777")
        val deliveredElsewhere = fixture.transfer(fixture.tokenB, fixture.pool, otherRecipient, 2)

        val record = fixture.record(
            multicallWithDeadline(listOf(exactOutputCall(otherRecipient))),
            events = listOf(spent(1), deliveredElsewhere),
        )

        assertPlainContractCall(record)
        assertAmount("-1", record.outgoingEvents?.single()?.value?.decimalValue)
    }

    @Test
    fun transactionRecord_failedSingleExactLeg_isDecoratedSwap() {
        val record = fixture.record(multicallWithDeadline(listOf(exactInputCall())), failed = true)

        assertEquals(TransactionRecordType.EVM_SWAP, record.transactionRecordType)
        assertTrue(record.failed)
    }

    @Test
    fun transactionRecord_v2RouteAtTopLevel_isUnknownSwapFromTransfers() {
        val record = fixture.record(v2RouteCall, events = listOf(spent(1), received(2)))

        assertEquals(TransactionRecordType.EVM_UNKNOWN_SWAP, record.transactionRecordType)
        assertTrue(record.swapDecodedFromCall)
        assertEquals(fixture.router.eip55, record.exchangeAddress)
        assertAmount("-1", record.valueIn?.decimalValue)
        assertAmount("2", record.valueOut?.decimalValue)
    }

    @Test
    fun transactionRecord_twoExactLegs_isUnknownSwapWithSummedAmounts() {
        val record = fixture.record(
            multicallWithDeadline(listOf(exactInputCall(), exactInputCall())),
            events = listOf(spent(1), spent(1), received(2), received(3)),
        )

        assertEquals(TransactionRecordType.EVM_UNKNOWN_SWAP, record.transactionRecordType)
        assertTrue(record.swapDecodedFromCall)
        assertAmount("-2", record.valueIn?.decimalValue)
        assertAmount("5", record.valueOut?.decimalValue)
    }

    @Test
    fun transactionRecord_exactLegAndV2Route_isUnknownSwapWithSummedAmounts() {
        val record = fixture.record(
            multicallWithDeadline(listOf(exactInputCall(), v2RouteCall)),
            events = listOf(spent(1), spent(1), received(2), received(3)),
        )

        assertEquals(TransactionRecordType.EVM_UNKNOWN_SWAP, record.transactionRecordType)
        assertAmount("-2", record.valueIn?.decimalValue)
        assertAmount("5", record.valueOut?.decimalValue)
    }

    @Test
    fun transactionRecord_failedRouteWithNativeValue_keepsOutSideOnly() {
        val record = fixture.record(v2RouteCall, failed = true, value = oneToken)

        assertEquals(TransactionRecordType.EVM_UNKNOWN_SWAP, record.transactionRecordType)
        assertAmount("-1", record.valueIn?.decimalValue)
        assertNull(record.valueOut)
    }

    @Test
    fun transactionRecord_failedRouteWithoutValue_isUnknownSwapWithoutAmounts() {
        val record = fixture.record(v2RouteCall, failed = true)

        assertEquals(TransactionRecordType.EVM_UNKNOWN_SWAP, record.transactionRecordType)
        assertNull(record.valueIn)
        assertNull(record.valueOut)
    }

    @Test
    fun transactionRecord_routeSpendingTwoTokens_staysContractCall() {
        val secondSpent: TransferEventInstance =
            fixture.transfer(fixture.tokenC, fixture.user, fixture.pool, 1)

        assertPlainContractCall(fixture.record(v2RouteCall, events = listOf(spent(1), secondSpent, received(2))))
    }

    @Test
    fun transactionRecord_succeededRouteWithoutIncoming_staysContractCall() {
        assertPlainContractCall(fixture.record(v2RouteCall, events = listOf(spent(1))))
    }

    @Test
    fun transactionRecord_routeWithUnresolvableToken_staysContractCall() {
        val unresolved = TransferEventInstance(fixture.tokenC, fixture.pool, fixture.user, oneToken, tokenInfo = null)

        assertPlainContractCall(fixture.record(v2RouteCall, events = listOf(spent(1), unresolved)))
    }

    @Test
    fun transactionRecord_multicallWithoutSwap_staysContractCall() {
        assertPlainContractCall(fixture.record(multicall(listOf(selector(0x12210e8a)))))
    }

    @Test
    fun transactionRecord_chainWithoutWeth_exactLegUsesActualTransfers() {
        val gnosis = EvmConverterFixture(Chain.Gnosis)

        val record = gnosis.record(
            multicallWithDeadline(listOf(exactInputCall(gnosis))),
            events = listOf(spent(1, gnosis), received(2, gnosis)),
        )

        assertEquals(TransactionRecordType.EVM_UNKNOWN_SWAP, record.transactionRecordType)
        assertTrue(record.swapDecodedFromCall)
    }

    @Test
    fun transactionRecord_liquidityAndSwapInOneMulticall_isLiquidity() {
        val record = fixture.record(
            multicall(listOf(exactInputCall(), mintCall)),
            events = listOf(spent(1), received(2)),
        )

        assertEquals(LiquidityOperation.Direction.Add, record.liquidityOperation?.direction)
        assertPlainContractCall(record)
    }
}
