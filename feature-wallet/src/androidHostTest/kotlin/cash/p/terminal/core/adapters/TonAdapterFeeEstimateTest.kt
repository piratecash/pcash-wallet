package cash.p.terminal.core.adapters

import cash.p.terminal.core.TonFeeEstimate
import cash.p.terminal.core.managers.TonKitWrapper
import com.tonapps.wallet.data.core.entity.SendRequestEntity
import io.horizontalsystems.tonkit.FriendlyAddress
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.core.TonKit.SendAmount
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import io.horizontalsystems.tonkit.models.TonFeeEstimate as KitTonFeeEstimate

// Robolectric: the payload request is built with org.json, which the plain Android stubs do not implement.
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TonAdapterFeeEstimateTest {

    private val tonKit: TonKit = mockk(relaxed = true)
    private val wrapper: TonKitWrapper = mockk(relaxed = true)
    private val kitEstimate = KitTonFeeEstimate(fee = BigInteger("5000000"), totalDebit = BigInteger("305000000"))

    private fun createAdapter(): TonAdapter {
        every { wrapper.tonKit } returns tonKit
        every { tonKit.account } returns null
        return TonAdapter(wrapper)
    }

    @Test
    fun estimateFee_nativeTransfer_requiredTonEqualsFee() = runTest {
        coEvery { tonKit.estimateFee(any<FriendlyAddress>(), any<SendAmount>(), any()) } returns kitEstimate

        val estimate = createAdapter().estimateFee(BigDecimal.ONE, mockk(relaxed = true), null)

        assertEquals(TonFeeEstimate(fee = BigDecimal("0.005"), requiredTon = BigDecimal("0.005")), estimate)
    }

    @Test
    fun estimateFeeWithPayload_swapPayload_requiredTonIsTotalDebit() = runTest {
        coEvery { tonKit.estimateFee(any<SendRequestEntity>()) } returns kitEstimate

        val estimate = createAdapter().estimateFeeWithPayload(
            amount = BigInteger("300000000"),
            address = "EQDestination",
            payload = "te6ccgEBAQEAAgAAAA==",
        )

        assertEquals(TonFeeEstimate(fee = BigDecimal("0.005"), requiredTon = BigDecimal("0.305")), estimate)
    }
}
