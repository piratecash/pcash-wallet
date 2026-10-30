package cash.p.terminal.modules.send.offline

import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.entities.OfflineSignedTransaction
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.ui.compose.components.animatedQrFrames
import cash.p.terminal.ui.compose.components.isReadablePcashQrCode
import cash.p.terminal.wallet.Wallet
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineSigningControllerTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val payloadEncoder = mockk<OfflineTransactionPayloadEncoder>()
    private val repository = mockk<OfflineSignedTransactionRepository>(relaxed = true)

    @Test
    fun sign_pcashPayloadFitsStaticQr_keepsPcashTransferFormat() {
        assertSelectedFormat(
            format = OfflineTransactionFormat.Pcash,
            payload = "pcash:tx:v1:bitcoin:body",
        )
    }

    @Test
    fun sign_pcashPayloadTooLargeRawFits_keepsPcashTransferFormat() {
        val payload = "z".repeat(TOO_LARGE_QR_PAYLOAD_SIZE)
        assertFalse(isReadablePcashQrCode(payload))
        assertNotNull(animatedQrFrames(payload))
        assertTrue(isReadablePcashQrCode(SMALL_RAW_HEX))

        assertSelectedFormat(OfflineTransactionFormat.Pcash, payload)
    }

    @Test
    fun sign_pcashPayloadTooLargeAndRawTooLarge_keepsPcashTransferFormat() {
        val payload = "z".repeat(TOO_LARGE_QR_PAYLOAD_SIZE)
        val rawHex = "ab".repeat(TOO_LARGE_RAW_BYTES)
        assertFalse(isReadablePcashQrCode(rawHex))
        assertNotNull(animatedQrFrames(rawHex))

        assertSelectedFormat(OfflineTransactionFormat.Pcash, payload, rawHex)
    }

    @Test
    fun sign_pcashOverAnimatedBudgetRawFits_keepsPcashTransferFormat() {
        val payload = "z".repeat(PAYLOAD_SIZE_OVER_ANIMATED_BUDGET)
        val rawHex = "ab".repeat(TOO_LARGE_RAW_BYTES)
        assertNull(animatedQrFrames(payload))
        assertFalse(isReadablePcashQrCode(rawHex))
        assertNotNull(animatedQrFrames(rawHex))

        assertSelectedFormat(OfflineTransactionFormat.Pcash, payload, rawHex)
    }

    @Test
    fun sign_rawSelectedPcashFitsStaticQr_keepsRawTransferFormat() {
        assertSelectedFormat(
            format = OfflineTransactionFormat.Raw,
            payload = "pcash:tx:v1:bitcoin:body",
        )
    }

    private fun assertSelectedFormat(
        format: OfflineTransactionFormat,
        payload: String,
        rawHex: String = SMALL_RAW_HEX,
    ) = runTest(dispatcher) {
        val controller = controller(this)
        val draft = draft(rawHex)
        every { payloadEncoder.encode(draft) } returns payload

        controller.sign(
            format = format,
            producer = { Unit },
            draftBuilder = { draft },
        )
        advanceUntilIdle()

        assertEquals(OfflineSignState.Signed(format), controller.signState)
        val transaction = requireNotNull(controller.signedTransaction)
        assertEquals(
            OfflineSignedTransaction(rawHex, payload, draft.txHash, draft.createdAt),
            transaction,
        )
        val transferFormat = (controller.signState as OfflineSignState.Signed).format
        val expectedContent = if (format == OfflineTransactionFormat.Pcash) payload else rawHex
        assertEquals(expectedContent, transferFormat.content(transaction))
        coVerify(exactly = 1) { repository.save(draft, payload) }
    }

    private fun controller(scope: CoroutineScope) = OfflineSigningController<Unit>(
        scope = scope,
        dispatcherProvider = TestDispatcherProvider(dispatcher, scope),
        payloadEncoder = payloadEncoder,
        repository = repository,
        cautionFactory = { mockk<HSCaution>() },
        isSilentCancellation = { false },
    )

    private fun draft(
        rawHex: String,
    ) = OfflineSignedTransactionDraft(
        wallet = mockk<Wallet>(relaxed = true),
        amount = BigDecimal.ONE,
        fee = BigDecimal.ZERO,
        toAddress = "address",
        rawHex = rawHex,
        txHash = "tx-hash",
        inputOutpoints = emptyList(),
        createdAt = 1_700_000_000_000L,
    )

    private companion object {
        const val SMALL_RAW_HEX = "deadbeefdeadbeef"
        const val TOO_LARGE_RAW_BYTES = 2_000
        const val TOO_LARGE_QR_PAYLOAD_SIZE = 1_700
        const val PAYLOAD_SIZE_OVER_ANIMATED_BUDGET = 90_001
    }
}
