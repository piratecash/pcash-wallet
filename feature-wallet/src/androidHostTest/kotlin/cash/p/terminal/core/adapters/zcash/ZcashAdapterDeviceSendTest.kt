package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.OfflineZcashSignRequest
import cash.p.terminal.core.managers.NotBroadcastException
import cash.p.terminal.wallet.hardware.HardwareAddressPosition
import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import cash.p.zcash.BroadcastResult
import cash.p.zcash.PaymentOptions
import cash.p.zcash.Pool
import cash.p.zcash.PoolSet
import cash.p.zcash.PreparedTransaction
import cash.p.zcash.Recipient
import cash.p.zcash.TransactionPlan
import cash.p.zcash.ZcashException
import cash.p.zcash.ZcashSdk
import cash.p.zcash.transactionId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertFailsWith

/**
 * A Trezor account signs only the transparent bundle, so every recipient it pays must be
 * transparent-only - and a memo, which rides in a shielded output, cannot be delivered at all.
 * A Ledger signs transparent and Orchard/Ironwood, but no Sapling and only as much as the app holds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZcashAdapterDeviceSendTest : ZcashAdapterTestFixture() {

    /** Recipient lists of every plan the adapter asked for. */
    private val plannedRecipients = mutableListOf<List<Recipient>>()
    private val plannedOptions = mutableListOf<PaymentOptions>()
    private val signer = mockk<ZcashTransactionSigner>()

    override fun stubWallet() {
        coEvery { zcashWallet.prepare(any(), any(), any()) } coAnswers {
            plannedRecipients += secondArg<List<Recipient>>()
            plannedOptions += thirdArg<PaymentOptions>()
            PREPARED
        }
        coEvery { zcashWallet.plan(any()) } returns TransactionPlan(
            height = HEIGHT,
            inputs = emptyList(),
            outputs = emptyList(),
            fee = FEE_ZAT,
            canSign = true,
            canBroadcast = true,
        )
        coEvery { zcashWallet.extract(any()) } returns RAW
        coEvery { zcashWallet.broadcast(any(), any(), any(), any()) } returns
            BroadcastResult(errorCode = 0, message = TX_ID)
    }

    @Before
    fun stubSigner() {
        mockkStatic("cash.p.zcash.ZcashSdkKt")
        coEvery { ZcashSdk.transactionId(any()) } returns TX_ID
        coEvery { signer.sign(any(), any(), any()) } returns PREPARED
    }

    @Test
    fun send_trezorAccount_withMemo_failsBeforeTheBroadcast() = runTest(dispatcher) {
        stubTrezorAccount()
        startAdapter(AddressSpecType.Transparent)

        val failure = assertFailsWith<NotBroadcastException> {
            adapter.send(AMOUNT, UNIFIED_RECIPIENT, memo = MEMO)
        }

        assertTrue(failure.cause is ZcashAdapter.ZcashError.TrezorMemoNotSupported)
        coVerify(exactly = 0) { zcashWallet.broadcast(any(), any(), any(), any()) }
    }

    @Test
    fun send_trezorAccount_withoutMemo_paysTheTransparentReceiverOnly() = runTest(dispatcher) {
        stubTrezorAccount()
        startAdapter(AddressSpecType.Transparent)

        adapter.send(AMOUNT, UNIFIED_RECIPIENT, memo = "")

        val recipient = plannedRecipients.single().single()
        assertEquals(PoolSet.of(Pool.TRANSPARENT), recipient.pools)
        assertNull(recipient.memo)
        assertTrue("the device signs the transparent bundle", plannedOptions.single().hardwareSigning)
    }

    @Test
    fun send_nonTrezorAccount_withMemo_keepsTheMemo() = runTest(dispatcher) {
        startAdapter(AddressSpecType.Shielded)

        adapter.send(AMOUNT, UNIFIED_RECIPIENT, memo = MEMO)

        val recipient = plannedRecipients.single().single()
        assertEquals(MEMO, recipient.memo)
        assertNull("an ordinary account is free to choose the pool", recipient.pools)
    }

    @Test
    fun signOffline_trezorAccount_withMemo_isRefused() = runTest(dispatcher) {
        stubTrezorAccount()
        startAdapter(AddressSpecType.Transparent)

        assertFailsWith<ZcashAdapter.ZcashError.TrezorMemoNotSupported> {
            adapter.signOffline(OfflineZcashSignRequest(AMOUNT, UNIFIED_RECIPIENT, memo = MEMO))
        }
    }

    @Test
    fun send_ledgerAccount_plansForExternalPcztSignerWithMemo() = runTest(dispatcher) {
        stubLedgerAccount()
        startAdapter(AddressSpecType.Unified)

        adapter.send(AMOUNT, UNIFIED_RECIPIENT, memo = MEMO)

        val options = plannedOptions.single()
        assertTrue(options.externalPcztSigning)
        assertFalse(options.hardwareSigning)
        assertEquals(MEMO, plannedRecipients.single().single().memo)
    }

    @Test
    fun send_ledgerAccount_planRefused_failsBeforeBroadcastWithDeviceReason() = runTest(dispatcher) {
        stubLedgerAccount()
        startAdapter(AddressSpecType.Unified)
        val reasons = mapOf(
            "sapling" to ZcashAdapter.ZcashError.LedgerSaplingRecipient,
            "transparent-inputs" to ZcashAdapter.ZcashError.LedgerTransactionTooLarge,
            "ironwood-actions" to ZcashAdapter.ZcashError.LedgerTransactionTooLarge,
        )

        reasons.forEach { (code, error) ->
            val refusal = ZcashException("refused [external-signing:$code]")
            coEvery { zcashWallet.prepare(any(), any(), any()) } throws refusal

            val failure = assertFailsWith<NotBroadcastException> { adapter.send(AMOUNT, UNIFIED_RECIPIENT, memo = "") }

            assertEquals(error, failure.cause)
        }
        coVerify(exactly = 0) { signer.sign(any(), any(), any()) }
    }

    @Test
    fun locate_ledgerAccount_ownAddressPastIndex1000_returnsItsExternalIndex() = runTest(dispatcher) {
        stubLedgerAccount()
        coEvery { zcashWallet.transparentAddressIndex(DB_ACCOUNT_ID, OWN_T_ADDRESS) } returns 1_000
        startAdapter(AddressSpecType.Transparent)

        assertEquals(HardwareAddressPosition(change = false, index = 1_000), adapter.locate(OWN_T_ADDRESS))
    }

    @Test
    fun locate_addressTheWalletDoesNotOwn_returnsNull() = runTest(dispatcher) {
        stubLedgerAccount()
        coEvery { zcashWallet.transparentAddressIndex(DB_ACCOUNT_ID, OWN_T_ADDRESS) } returns null
        startAdapter(AddressSpecType.Transparent)

        assertNull(adapter.locate(OWN_T_ADDRESS))
    }

    private fun TestScope.startAdapter(spec: AddressSpecType) {
        adapter = createAdapter(spec, signer)
        adapter.start()
        advanceUntilIdle()
        plannedRecipients.clear()
        plannedOptions.clear()
    }

    private companion object {
        const val HEIGHT = 3_428_200
        const val FEE_ZAT = 10_000L
        const val MEMO = "order-42"
        const val UNIFIED_RECIPIENT = "u1RecipientAddress"
        const val OWN_T_ADDRESS = "t1OwnAddress"
        val AMOUNT: BigDecimal = BigDecimal("0.1")
        val RAW = byteArrayOf(1, 2, 3, 4)
        val PREPARED = PreparedTransaction(byteArrayOf(9, 9, 9, 9))
        const val TX_ID =
            "b7c4a1f0d3e2b5a698877665544332211ffeeddccbbaa99887766554433221100"
    }
}
