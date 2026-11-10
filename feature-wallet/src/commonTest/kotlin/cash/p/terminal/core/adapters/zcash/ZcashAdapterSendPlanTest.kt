package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.OfflineZcashSignRequest
import cash.p.terminal.core.ZcashFeeEstimate
import cash.p.terminal.core.managers.NotBroadcastException
import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import cash.p.zcash.BroadcastResult
import cash.p.zcash.MaxSendable
import cash.p.zcash.PaymentOptions
import cash.p.zcash.Pool
import cash.p.zcash.PoolSet
import cash.p.zcash.PreparedTransaction
import cash.p.zcash.Recipient
import cash.p.zcash.TransactionPlan
import cash.p.zcash.ZcashException
import cash.p.zcash.ZcashSdk
import cash.p.zcash.ZcashWallet
import cash.p.zcash.deriveSpendingKey
import cash.p.zcash.transactionId
import io.mockk.coEvery
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertFailsWith

/** What the adapter asks the SDK to plan: the sweep contract, shown fee and the send must agree. */
@OptIn(ExperimentalCoroutinesApi::class)
class ZcashAdapterSendPlanTest : ZcashAdapterTestFixture() {

    private val planned = mutableListOf<Pair<Recipient, PaymentOptions>>()

    override fun stubWallet() {
        coEvery { zcashWallet.maxSendable(any(), any(), any(), any(), any()) } returns SWEEP
        coEvery { zcashWallet.prepare(any(), any(), any()) } coAnswers {
            planned += secondArg<List<Recipient>>().single() to thirdArg<PaymentOptions>()
            PREPARED
        }
        coEvery { zcashWallet.plan(any()) } returns TransactionPlan(
            height = HEIGHT,
            inputs = emptyList(),
            outputs = emptyList(),
            fee = SWEEP.fee,
            canSign = true,
            canBroadcast = true,
        )
        coEvery { zcashWallet.sign(any(), any(), any()) } returns PREPARED
        coEvery { zcashWallet.extract(any()) } returns RAW
        coEvery { zcashWallet.broadcast(any(), any(), any(), any()) } returns
            BroadcastResult(errorCode = 0, message = TX_ID)
    }

    @Before
    fun stubSdkKeyDerivation() {
        mockkStatic("cash.p.zcash.ZcashSdkKt")
        coEvery { ZcashSdk.deriveSpendingKey(any(), any(), any(), any()) } returns ByteArray(32)
        coEvery { ZcashSdk.transactionId(any()) } returns TX_ID
    }

    @Test
    fun maxSendable_threeNotesAtTheLegacyRate_isTheBalanceMinusTheActualFee() = runTest(dispatcher) {
        startAdapter()

        assertEquals(zec(65_000), adapter.maxSendable(RECIPIENT, memo = ""))
    }

    @Test
    fun maxSendable_passesTheSendOptionsAndTheMemo() = runTest(dispatcher) {
        startAdapter()
        var seen: Triple<String, String?, PaymentOptions>? = null
        coEvery { zcashWallet.maxSendable(any(), any(), any(), any(), any()) } coAnswers {
            seen = Triple(secondArg(), thirdArg(), arg(3))
            SWEEP
        }

        adapter.maxSendable(RECIPIENT, memo = "memo")

        assertEquals(
            Triple(RECIPIENT, "memo", PaymentOptions(sourcePools = UNIFIED_POOLS)),
            seen,
        )
    }

    @Test
    fun maxSendable_softwareAccount_leavesTheRecipientPoolOpen() = runTest(dispatcher) {
        startAdapter()

        assertNull(quotedPools())
    }

    @Test
    fun maxSendable_trezorAccount_quotesTheTransparentPoolItWillPay() = runTest(dispatcher) {
        stubTrezorAccount()
        startAdapter(RecordingSigner())

        assertEquals(TRANSPARENT_POOL, quotedPools())
    }

    @Test
    fun maxSendable_planningFails_isNull() = runTest(dispatcher) {
        startAdapter()
        coEvery { zcashWallet.maxSendable(any(), any(), any(), any(), any()) } throws ZcashException("no server")

        assertNull(adapter.maxSendable(RECIPIENT, memo = ""))
    }

    @Test
    fun estimateFee_amountBelowTheMaximum_plansAPlainSend() = runTest(dispatcher) {
        startAdapter()

        val fee = adapter.estimateFee(zec(40_000), RECIPIENT, memo = "memo")

        assertEquals(ZcashFeeEstimate.Planned(zec(SWEEP.fee)), fee)
        assertEquals(
            Recipient(RECIPIENT, 40_000, memo = "memo") to PaymentOptions(sourcePools = UNIFIED_POOLS),
            planned.single(),
        )
    }

    @Test
    fun estimateFee_amountIsTheMaximum_plansTheBalanceWithRecipientPayingTheFee() = runTest(dispatcher) {
        startAdapter()

        adapter.estimateFee(zec(SWEEP.amount), RECIPIENT, memo = "")

        assertSwept(planned.single())
    }

    @Test
    fun estimateFee_otherPlanningFailure_isUnavailable() = runTest(dispatcher) {
        startAdapter()
        coEvery { zcashWallet.prepare(any(), any(), any()) } throws ZcashException("no server")

        assertEquals(ZcashFeeEstimate.Unavailable, adapter.estimateFee(zec(40_000), RECIPIENT, memo = ""))
    }

    @Test
    fun estimateFee_amountJustBelowTheMaximumWithoutAFeasiblePlan_isInsufficientBalance() = runTest(dispatcher) {
        stubNotesOf127000ToATransparentRecipient()
        startAdapter()

        assertEquals(ZcashFeeEstimate.InsufficientBalance, adapter.estimateFee(zec(111_000), RECIPIENT, memo = ""))
    }

    @Test
    fun estimateFee_amountIsTheMaximumOfTheSameNotes_isPlanned() = runTest(dispatcher) {
        stubNotesOf127000ToATransparentRecipient()
        startAdapter()

        assertEquals(ZcashFeeEstimate.Planned(zec(15_000)), adapter.estimateFee(zec(112_000), RECIPIENT, memo = ""))
    }

    @Test
    fun send_amountIsTheMaximum_spendsTheBalanceWithRecipientPayingTheFee() = runTest(dispatcher) {
        startAdapter()

        adapter.send(zec(SWEEP.amount), RECIPIENT, memo = "")

        assertSwept(planned.single())
    }

    @Test
    fun send_amountBelowTheMaximum_isAPlainSend() = runTest(dispatcher) {
        startAdapter()

        adapter.send(zec(40_000), RECIPIENT, memo = "")

        val (recipient, options) = planned.single()
        assertEquals(40_000L, recipient.amount)
        assertFalse(options.recipientPaysFee)
    }

    @Test
    fun send_maximumCannotBePlanned_stillPlansThePlainSend() = runTest(dispatcher) {
        startAdapter()
        coEvery { zcashWallet.maxSendable(any(), any(), any(), any(), any()) } throws ZcashException("too many notes")

        adapter.send(zec(40_000), RECIPIENT, memo = "")

        assertEquals(40_000L, planned.single().first.amount)
    }

    @Test
    fun signOffline_amountIsTheMaximum_spendsTheBalanceWithRecipientPayingTheFee() = runTest(dispatcher) {
        startAdapter()

        adapter.signOffline(OfflineZcashSignRequest(zec(SWEEP.amount), RECIPIENT, memo = ""))

        assertSwept(planned.single())
    }

    @Test
    fun send_nodeRefusedAndReleasedTheInputs_isMarkedNotBroadcast() = runTest(dispatcher) {
        coEvery { zcashWallet.broadcast(any(), any(), any(), any()) } returns
            BroadcastResult(errorCode = -25, message = REFUSAL, inputsReleased = true)
        startAdapter()

        val failure = assertFailsWith<NotBroadcastException> {
            adapter.send(zec(40_000), RECIPIENT, memo = "")
        }

        assertTrue(failure.cause.message.orEmpty().contains(REFUSAL))
    }

    @Test
    fun send_nodeRefusedAndKeptTheInputs_staysUnmarked() = runTest(dispatcher) {
        coEvery { zcashWallet.broadcast(any(), any(), any(), any()) } returns
            BroadcastResult(errorCode = -25, message = REFUSAL, inputsReleased = false)
        startAdapter()

        val failure = assertFailsWith<IllegalStateException> {
            adapter.send(zec(40_000), RECIPIENT, memo = "")
        }

        assertTrue(failure.message.orEmpty().contains(REFUSAL))
    }

    @Test
    fun send_feesMatch_signsTheSweep() = runTest(dispatcher) {
        val signer = stubPlanCharging(SWEEP, chargedFee = SWEEP.fee)
        startAdapter(signer)

        adapter.send(zec(SWEEP.amount), RECIPIENT, memo = "")

        assertSwept(planned.single())
        assertArrayEquals(SWEEP_TX.bytes, signer.signed.single().bytes)
    }

    @Test
    fun send_rateBoundaryCrossedAfterTheMaximum_signsAPlainSendOfTheConfirmedAmount() = runTest(dispatcher) {
        val signer = stubPlanCharging(BOUNDARY_MAX, chargedFee = 2_000)
        startAdapter(signer)

        adapter.send(zec(BOUNDARY_MAX.amount), RECIPIENT, memo = "")

        assertPlainSendSigned(BOUNDARY_RECIPIENT, signer, PaymentOptions(sourcePools = UNIFIED_POOLS))
    }

    @Test
    fun send_trezorFeeDiffersFromTheMaximum_signsAPlainSendOfTheConfirmedAmount() = runTest(dispatcher) {
        stubTrezorAccount()
        val signer = stubPlanCharging(BOUNDARY_MAX, chargedFee = 2_000)
        startAdapter(signer)

        adapter.send(zec(BOUNDARY_MAX.amount), RECIPIENT, memo = "")

        assertPlainSendSigned(
            Recipient(RECIPIENT, BOUNDARY_MAX.amount, pools = TRANSPARENT_POOL, memo = null),
            signer,
            PaymentOptions(sourcePools = UNIFIED_POOLS, hardwareSigning = true),
        )
    }

    @Test
    fun send_noteLostAfterTheMaximum_failsAsInsufficientWithoutSigning() = runTest(dispatcher) {
        val signer = stubPlanCharging(BOUNDARY_MAX, chargedFee = 15_000, plainIsFeasible = false)
        startAdapter(signer)

        val failure = assertFailsWith<NotBroadcastException> {
            adapter.send(zec(BOUNDARY_MAX.amount), RECIPIENT, memo = "")
        }

        assertTrue(failure.cause is LocalizedException)
        assertEquals(BOUNDARY_MAX.amount, planned.last().first.amount)
        assertTrue(signer.signed.isEmpty())
    }

    @Test
    fun estimateFee_rateBoundaryCrossedAfterTheMaximum_isTheFeeOfThePlainSend() = runTest(dispatcher) {
        startAdapter(stubPlanCharging(BOUNDARY_MAX, chargedFee = 2_000))

        val estimate = adapter.estimateFee(zec(BOUNDARY_MAX.amount), RECIPIENT, memo = "")

        assertEquals(ZcashFeeEstimate.Planned(zec(2_000)), estimate)
        assertEquals(BOUNDARY_MAX.amount, planned.last().first.amount)
        assertFalse(planned.last().second.recipientPaysFee)
    }

    @Test
    fun estimateFee_noteLostAfterTheMaximum_isInsufficientBalance() = runTest(dispatcher) {
        startAdapter(stubPlanCharging(BOUNDARY_MAX, chargedFee = 15_000, plainIsFeasible = false))

        assertEquals(
            ZcashFeeEstimate.InsufficientBalance,
            adapter.estimateFee(zec(BOUNDARY_MAX.amount), RECIPIENT, memo = ""),
        )
    }

    @Test
    fun signOffline_rateBoundaryCrossedAfterTheMaximum_signsAPlainSendOfTheConfirmedAmount() = runTest(dispatcher) {
        val signer = stubPlanCharging(BOUNDARY_MAX, chargedFee = 2_000)
        startAdapter(signer)

        val signed = adapter.signOffline(OfflineZcashSignRequest(zec(BOUNDARY_MAX.amount), RECIPIENT, memo = ""))

        assertPlainSendSigned(BOUNDARY_RECIPIENT, signer, PaymentOptions(sourcePools = UNIFIED_POOLS))
        assertEquals(zec(2_000), signed.fee)
    }

    /** The SDK charges [chargedFee] on either form; each form is told apart by what gets signed. */
    private fun stubPlanCharging(
        max: MaxSendable,
        chargedFee: Long,
        plainIsFeasible: Boolean = true,
    ): RecordingSigner {
        coEvery { zcashWallet.maxSendable(any(), any(), any(), any(), any()) } returns max
        coEvery { zcashWallet.prepare(any(), any(), any()) } coAnswers {
            val options = thirdArg<PaymentOptions>()
            planned += secondArg<List<Recipient>>().single() to options
            when {
                options.recipientPaysFee -> SWEEP_TX
                plainIsFeasible -> PLAIN_TX
                else -> throw ZcashException("No feasible note selection found")
            }
        }
        coEvery { zcashWallet.plan(any()) } returns TransactionPlan(
            height = HEIGHT,
            inputs = emptyList(),
            outputs = emptyList(),
            fee = chargedFee,
            canSign = true,
            canBroadcast = true,
        )
        return RecordingSigner()
    }

    private suspend fun quotedPools(): PoolSet? {
        var pools: PoolSet? = null
        coEvery { zcashWallet.maxSendable(any(), any(), any(), any(), any()) } coAnswers {
            pools = arg(4)
            SWEEP
        }
        adapter.maxSendable(RECIPIENT, memo = "")
        return pools
    }

    private fun assertPlainSendSigned(recipient: Recipient, signer: RecordingSigner, options: PaymentOptions) {
        assertEquals(recipient to options, planned.last())
        assertArrayEquals(PLAIN_TX.bytes, signer.signed.single().bytes)
    }

    private class RecordingSigner : ZcashTransactionSigner {
        val signed = mutableListOf<PreparedTransaction>()

        override suspend fun sign(
            wallet: ZcashWallet,
            account: Int,
            transaction: PreparedTransaction,
        ): PreparedTransaction = transaction.also { signed += it }
    }

    /**
     * Notes 100000/20000/7000: a plain plan pays for the hypothetical change output (20000), and
     * only spending everything with the recipient paying the fee gets by on 15000.
     */
    private fun stubNotesOf127000ToATransparentRecipient() {
        coEvery { zcashWallet.maxSendable(any(), any(), any(), any(), any()) } returns MaxSendable(112_000, 15_000)
        coEvery { zcashWallet.prepare(any(), any(), any()) } coAnswers {
            val amount = secondArg<List<Recipient>>().single().amount
            val feasible = thirdArg<PaymentOptions>().recipientPaysFee && amount == 127_000L ||
                amount + 20_000 <= 127_000
            if (!feasible) throw ZcashException("No feasible note selection found")
            PREPARED
        }
        coEvery { zcashWallet.plan(any()) } returns TransactionPlan(
            height = HEIGHT,
            inputs = emptyList(),
            outputs = emptyList(),
            fee = 15_000,
            canSign = true,
            canBroadcast = true,
        )
    }

    private fun TestScope.startAdapter(signer: ZcashTransactionSigner? = null) {
        adapter = if (signer != null) {
            createAdapter(AddressSpecType.Unified, signer)
        } else {
            createAdapter(AddressSpecType.Unified)
        }
        adapter.start()
        advanceUntilIdle()
        planned.clear()
    }

    private fun assertSwept(plannedSend: Pair<Recipient, PaymentOptions>) {
        val (recipient, options) = plannedSend
        assertEquals(SWEEP.amount + SWEEP.fee, recipient.amount)
        assertTrue(options.recipientPaysFee)
        assertEquals(UNIFIED_POOLS, options.sourcePools)
    }

    private fun zec(zatoshi: Long): BigDecimal = zatoshi.convertZatoshiToZec()

    private companion object {
        const val RECIPIENT = "u1recipient"
        const val HEIGHT = 3_500_000
        const val TX_ID = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val REFUSAL = "tx has incorrect consensus branch id"
        val SWEEP = MaxSendable(amount = 65_000, fee = 15_000)
        val UNIFIED_POOLS = PoolSet.of(Pool.ORCHARD, Pool.IRONWOOD)
        val TRANSPARENT_POOL = PoolSet.of(Pool.TRANSPARENT)
        val BOUNDARY_MAX = MaxSendable(amount = 70_000, fee = 10_000)
        val BOUNDARY_RECIPIENT = Recipient(RECIPIENT, BOUNDARY_MAX.amount, memo = null)
        val PREPARED = PreparedTransaction(byteArrayOf(1))
        val SWEEP_TX = PreparedTransaction(byteArrayOf(3))
        val PLAIN_TX = PreparedTransaction(byteArrayOf(4))
        val RAW = byteArrayOf(2)
    }
}
