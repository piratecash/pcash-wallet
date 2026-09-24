package cash.p.terminal.core.adapters.thorchain

import cash.p.terminal.R
import cash.p.terminal.core.BroadcastRawTransactionResult
import cash.p.terminal.core.BroadcastRawTransactionStatus
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.OfflineThorchainSignRequest
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.ThorchainKitWrapper
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.thorchainkit.ThorchainKit
import io.horizontalsystems.thorchainkit.models.Asset
import io.horizontalsystems.thorchainkit.models.SignedTransaction
import io.horizontalsystems.thorchainkit.network.Network
import io.horizontalsystems.thorchainkit.transaction.TransactionSender.SendError
import io.horizontalsystems.thorchainkit.transaction.TxBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
class ThorchainAdapterTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val kit = mockk<ThorchainKit>(relaxed = true)
    private lateinit var wrapper: ThorchainKitWrapper

    @Test
    fun balanceData_runeBaseUnits_scaledByEightDecimals() {
        every { kit.getDenomBalance("rune") } returns BigInteger("150000000")

        assertAmount("1.5", adapter().balanceData.available)
    }

    @Test
    fun balanceData_cacaoBaseUnits_scaledByTenDecimals() {
        every { kit.getDenomBalance("cacao") } returns BigInteger("15000000000")

        assertAmount("1.5", adapter(Network.MayaMainnet).balanceData.available)
    }

    @Test
    fun balanceData_tokenWallet_readsItsOwnDenom() {
        every { kit.getDenomBalance("tcy") } returns BigInteger("250000000")
        every { kit.getDenomBalance("rune") } returns BigInteger("100000000")

        val adapter = adapter(tokenType = TokenType.ThorchainAsset("tcy"))

        assertAmount("2.5", adapter.balanceData.available)
        assertAmount("1", adapter.nativeBalanceData.available)
    }

    @Test
    fun send_rune_scalesAmountToEightDecimalBaseUnits() = runTest(dispatcher) {
        every { kit.getDenomBalance("rune") } returns BigInteger("1000000000")
        coEvery { kit.send(any(), any(), any(), any(), any()) } returns "HASH"

        val hash = adapter().send(BigDecimal("1.5"), RUNE_ADDRESS, "memo")

        assertEquals("HASH", hash)
        coVerify { kit.send(any(), BigInteger("150000000"), "rune", "memo", any()) }
    }

    @Test
    fun send_cacao_scalesAmountToTenDecimalBaseUnits() = runTest(dispatcher) {
        every { kit.getDenomBalance("cacao") } returns BigInteger("100000000000")
        coEvery { kit.send(any(), any(), any(), any(), any()) } returns "HASH"

        adapter(Network.MayaMainnet).send(BigDecimal("1.5"), MAYA_ADDRESS, null)

        coVerify { kit.send(any(), BigInteger("15000000000"), "cacao", null, any()) }
    }

    @Test
    fun send_possiblyAccepted_returnsItsHash() = runTest(dispatcher) {
        every { kit.getDenomBalance("rune") } returns BigInteger("1000000000")
        coEvery { kit.send(any(), any(), any(), any(), any()) } throws SendError.PossiblyAccepted("ab12")

        assertEquals("AB12", adapter().send(BigDecimal("1"), RUNE_ADDRESS, null))
    }

    @Test
    fun deposit_rune_depositsAssetInBaseUnits() = runTest(dispatcher) {
        every { kit.getDenomBalance("rune") } returns BigInteger("1000000000")
        coEvery { kit.deposit(any(), any(), any(), any()) } returns "HASH"

        val hash = adapter().deposit("THOR.RUNE", BigDecimal("1.5"), "=:ETH.ETH:0xabc")

        assertEquals("HASH", hash)
        coVerify { kit.deposit(Asset.Rune, BigInteger("150000000"), "=:ETH.ETH:0xabc", any()) }
    }

    @Test
    fun deposit_possiblyAccepted_returnsItsHash() = runTest(dispatcher) {
        every { kit.getDenomBalance("rune") } returns BigInteger("1000000000")
        coEvery { kit.deposit(any(), any(), any(), any()) } throws SendError.PossiblyAccepted("cd34")

        assertEquals("CD34", adapter().deposit("THOR.RUNE", BigDecimal("1"), "memo"))
    }

    @Test
    fun getMinimumSendAmount_anyAddress_returnsNull() = runTest(dispatcher) {
        assertEquals(null, adapter().getMinimumSendAmount(RUNE_ADDRESS))
    }

    @Test
    fun send_tokenWithRuneBelowFee_throwsInsufficientBalanceForFee() = runTest(dispatcher) {
        every { kit.getDenomBalance("tcy") } returns BigInteger("500000000")
        every { kit.getDenomBalance("rune") } returns BigInteger("1000000")
        val adapter = adapter(tokenType = TokenType.ThorchainAsset("tcy"))

        val error = runCatchingSuspend { adapter.send(BigDecimal("1"), RUNE_ADDRESS, null) }

        assertEquals(R.string.Error_InsufficientBalanceForFee, (error as LocalizedException).errorTextRes)
        coVerify(exactly = 0) { kit.send(any(), any(), any(), any(), any()) }
    }

    @Test
    fun send_tokenWithRuneCoveringFee_sendsTokenDenom() = runTest(dispatcher) {
        every { kit.getDenomBalance("tcy") } returns BigInteger("500000000")
        every { kit.getDenomBalance("rune") } returns BigInteger("2000000")
        coEvery { kit.send(any(), any(), any(), any(), any()) } returns "HASH"

        adapter(tokenType = TokenType.ThorchainAsset("tcy")).send(BigDecimal("5"), RUNE_ADDRESS, null)

        coVerify { kit.send(any(), BigInteger("500000000"), "tcy", null, any()) }
    }

    @Test
    fun send_nativeAmountPlusFeeAboveBalance_throwsInsufficientBalanceForFee() = runTest(dispatcher) {
        every { kit.getDenomBalance("rune") } returns BigInteger("100000000")

        val error = runCatchingSuspend { adapter().send(BigDecimal("1"), RUNE_ADDRESS, null) }

        assertEquals(R.string.Error_InsufficientBalanceForFee, (error as LocalizedException).errorTextRes)
    }

    @Test
    fun validate_otherNetworkAddress_throws() {
        assertThrows(Exception::class.java) { adapter().validate(MAYA_ADDRESS) }
    }

    @Test
    fun maxSpendableBalance_native_subtractsFeeFlooredAtZero() {
        every { kit.getDenomBalance("rune") } returns BigInteger("100000000")
        assertAmount("0.98", adapter().maxSpendableBalance)

        every { kit.getDenomBalance("rune") } returns BigInteger("1000000")
        assertAmount("0", adapter().maxSpendableBalance)
    }

    @Test
    fun maxSpendableBalance_token_returnsWholeTokenBalance() {
        every { kit.getDenomBalance("tcy") } returns BigInteger("100000000")

        val adapter = adapter(tokenType = TokenType.ThorchainAsset("tcy"))

        assertAmount("1", adapter.maxSpendableBalance)
        assertAmount("0", adapter.fee.value)
        assertAmount("0.02", adapter.sendFee)
    }

    @Test
    fun fee_wrapperFeeReported_followsItWithoutFetchingItself() = runTest(dispatcher) {
        val adapter = adapter()
        adapter.attachLocalData()
        adapter.resumeNetwork()

        wrapper.nativeFeeState.value = BigInteger("3000000")

        assertAmount("0.03", adapter.sendFee)
        assertAmount("0.03", adapter.fee.value)
        coVerify(exactly = 0) { kit.estimateFee() }
    }

    @Test
    fun maxSpendableBalance_nativeAfterFeeReport_subtractsNewFee() = runTest(dispatcher) {
        every { kit.getDenomBalance("rune") } returns BigInteger("100000000")
        val adapter = adapter()
        var feeUpdates = 0
        backgroundScope.launch { adapter.sendFeeUpdatedFlow.collect { feeUpdates++ } }

        wrapper.nativeFeeState.value = BigInteger("3000000")

        assertAmount("0.97", adapter.maxSpendableBalance)
        assertEquals(2, feeUpdates)
    }

    @Test
    fun fee_mayaBeforeNodeReport_usesCacaoDefault() {
        val adapter = adapter(Network.MayaMainnet)

        assertAmount("0.2", adapter.sendFee)
        assertAmount("0.2", adapter.fee.value)
    }

    @Test
    fun signOffline_request_returnsKitRawHexAndHash() = runTest(dispatcher) {
        every { kit.getDenomBalance("rune") } returns BigInteger("1000000000")
        coEvery { kit.signSend(any(), any(), any(), any(), any()) } returns
            SignedTransaction(byteArrayOf(0x0a, 0x1b), "AB12", 7, 3)

        val signed = adapter().signOffline(OfflineThorchainSignRequest(BigDecimal("2"), RUNE_ADDRESS, "m"))

        assertEquals("0a1b", signed.rawHex)
        assertEquals("AB12", signed.txHash)
        assertAmount("0.02", signed.fee)
        coVerify { kit.signSend(any(), BigInteger("200000000"), any(), "rune", "m") }
    }

    @Test
    fun broadcastRawTransaction_accepted_returnsSubmittedWithHash() = runTest(dispatcher) {
        coEvery { kit.broadcastRawTransaction(any()) } returns "AB12"

        val result = adapter().broadcastRawTransaction(RAW_HEX)

        assertEquals(BroadcastRawTransactionResult("AB12", BroadcastRawTransactionStatus.Submitted), result)
    }

    @Test
    fun broadcastRawTransaction_possiblyAccepted_returnsOutcomeUnknownWithHash() = runTest(dispatcher) {
        coEvery { kit.broadcastRawTransaction(any()) } throws SendError.PossiblyAccepted("AB12")

        val result = adapter().broadcastRawTransaction(RAW_HEX)

        assertEquals(BroadcastRawTransactionResult("AB12", BroadcastRawTransactionStatus.OutcomeUnknown), result)
    }

    @Test
    fun broadcastRawTransaction_sequenceConsumed_returnsSeqnoConsumedWithRawHash() = runTest(dispatcher) {
        coEvery { kit.broadcastRawTransaction(any()) } throws SendError.SequenceConsumed()

        val result = adapter().broadcastRawTransaction(RAW_HEX)

        val expectedHash = TxBuilder.txHash(RAW_BYTES)
        assertEquals(BroadcastRawTransactionResult(expectedHash, BroadcastRawTransactionStatus.SeqnoConsumed), result)
    }

    @Test
    fun transactionExists_lowercaseHash_asksKitForUppercaseHash() = runTest(dispatcher) {
        coEvery { kit.transactionExists("AB12") } returns true

        assertEquals(true, adapter().transactionExists(" ab12 "))
    }

    private fun adapter(
        network: Network = Network.Mainnet,
        tokenType: TokenType = TokenType.Native,
    ): ThorchainAdapter {
        every { kit.network } returns network
        every { kit.receiveAddress } returns RUNE_ADDRESS
        val wallet = mockk<Wallet> {
            every { token.type } returns tokenType
            every { account } returns ACCOUNT
        }
        wrapper = ThorchainKitWrapper(kit)
        return ThorchainAdapter(
            wrapper,
            wallet,
            TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        )
    }

    private suspend fun runCatchingSuspend(block: suspend () -> Unit): Throwable? =
        try {
            block()
            null
        } catch (error: Throwable) {
            error
        }

    private fun assertAmount(expected: String, actual: BigDecimal) =
        assertEquals(0, BigDecimal(expected).compareTo(actual))

    private companion object {
        const val RUNE_ADDRESS = "thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws"
        const val MAYA_ADDRESS = "maya166n4w5039meulfa3p6ydg60ve6ueac7tlu37cq"
        const val RAW_HEX = "0a1b2c3d4e5f60718293a4b5c6d7e8f9"
        val RAW_BYTES = byteArrayOf(
            0x0a, 0x1b, 0x2c, 0x3d, 0x4e, 0x5f, 0x60, 0x71,
            0x82.toByte(), 0x93.toByte(), 0xa4.toByte(), 0xb5.toByte(),
            0xc6.toByte(), 0xd7.toByte(), 0xe8.toByte(), 0xf9.toByte(),
        )
        val ACCOUNT = Account(
            id = "account-id",
            name = "Account",
            type = AccountType.Mnemonic(List(11) { "abandon" } + "about", ""),
            origin = AccountOrigin.Created,
            level = 0,
        )
    }
}
