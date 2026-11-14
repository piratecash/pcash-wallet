package cash.p.terminal.modules.send.tron

import cash.p.terminal.core.managers.NumberFormatter
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.tron_fee_covered_free
import cash.p.terminal.resources.tron_fee_energy_own
import cash.p.terminal.resources.tron_resource_bandwidth
import cash.p.terminal.resources.tron_resource_energy
import cash.p.terminal.resources.tron_resource_free_bandwidth
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.ILanguageManager
import io.horizontalsystems.tronkit.models.AccountResources
import io.horizontalsystems.tronkit.transaction.Fee
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class TronFeeBreakdownTest {

    @Test
    fun toTronFeeBreakdown_ownEnergyCoversAll_chargesNothingButFeeLimitCoversWholeCall() {
        val breakdown = listOf(
            Fee.Energy(required = 64_895, price = 100, fromAccount = 64_895),
            Fee.Bandwidth(points = 345, price = 1_000, source = Fee.Bandwidth.Source.Staked),
        ).toTronFeeBreakdown(TRX_DECIMALS)

        assertAmount("0", breakdown.totalFee)
        assertEquals(6_489_500L, breakdown.feeLimit)
        assertAmount("0", checkNotNull(breakdown.energy).burned)
    }

    @Test
    fun toTronFeeBreakdown_partlyOwnEnergyAndBurnedBandwidth_totalsBurnedParts() {
        val breakdown = listOf(
            Fee.Energy(required = 64_895, price = 100, fromAccount = 20_000),
            Fee.Bandwidth(points = 345, price = 1_000),
        ).toTronFeeBreakdown(TRX_DECIMALS)

        assertAmount("4.4895", checkNotNull(breakdown.energy).burned)
        assertAmount("0.345", checkNotNull(breakdown.bandwidth).burned)
        assertAmount("4.8345", breakdown.totalFee)
        assertEquals(6_489_500L, breakdown.feeLimit)
    }

    @Test
    fun toTronFeeBreakdown_contractPaysShare_burnsOnlyCallerPart() {
        val breakdown = listOf(
            Fee.Energy(required = 64_895, price = 100, fromContract = 30_000),
            Fee.Bandwidth(points = 345, price = 1_000, source = Fee.Bandwidth.Source.Free),
        ).toTronFeeBreakdown(TRX_DECIMALS)

        assertAmount("3.4895", breakdown.totalFee)
        assertEquals(30_000L, checkNotNull(breakdown.energy).fromContract)
        assertEquals(6_489_500L, breakdown.feeLimit)
    }

    @Test
    fun toTronFeeBreakdown_unactivatedRecipient_includesActivationAndCreationBurn() {
        val breakdown = listOf(
            Fee.Bandwidth(points = 269, price = 1_000, burnInSuns = 100_000),
            Fee.AccountActivation(amount = 1_000_000),
        ).toTronFeeBreakdown(TRX_DECIMALS)

        assertAmount("1", checkNotNull(breakdown.activationFee))
        assertAmount("1.1", breakdown.totalFee)
        assertNull(breakdown.feeLimit)
        assertNull(breakdown.energy)
    }

    @Test
    fun toTronFeeBreakdown_freeBandwidthCovers_chargesNothing() {
        val breakdown = listOf(
            Fee.Bandwidth(points = 269, price = 1_000, source = Fee.Bandwidth.Source.Free),
        ).toTronFeeBreakdown(TRX_DECIMALS)

        assertAmount("0", breakdown.totalFee)
        assertNull(breakdown.activationFee)
    }

    @Test
    fun toTronResourceRows_zeroLimitResource_hidesRow() {
        val rows = AccountResources(
            freeBandwidth = AccountResources.Resource(limit = 600, used = 345),
            stakedBandwidth = AccountResources.Resource(),
            energy = AccountResources.Resource(limit = 65_000, used = 45_000),
        ).toTronResourceRows(formatter)

        assertEquals(
            listOf(
                TronResourceRow(Res.string.tron_resource_energy, "20000", "65000"),
                TronResourceRow(Res.string.tron_resource_free_bandwidth, "255", "600"),
            ),
            rows,
        )
    }

    @Test
    fun toTronResourceRows_realFormatter_formatsPoints() {
        val formatter = NumberFormatter(object : ILanguageManager {
            override var currentLocale: Locale = Locale.US
            override var currentLocaleTag: String = "en-US"
            override val currentLanguage: String = "en"
        })

        val rows = AccountResources(
            freeBandwidth = AccountResources.Resource(),
            stakedBandwidth = AccountResources.Resource(),
            energy = AccountResources.Resource(limit = 65_000, used = 45_000),
        ).toTronResourceRows(formatter)

        assertEquals(listOf(TronResourceRow(Res.string.tron_resource_energy, "20,000", "65,000")), rows)
    }

    @Test
    fun toTronFeeRows_partlyOwnEnergyAndBandwidthFromQuota_showsCoverage() {
        val rows = listOf(
            Fee.Energy(required = 64_895, price = 100, fromAccount = 20_000),
            Fee.Bandwidth(points = 345, price = 1_000, source = Fee.Bandwidth.Source.Free),
        ).toTronFeeBreakdown(TRX_DECIMALS).toTronFeeRows(formatter, "TRX", TRX_DECIMALS)

        assertEquals(
            listOf(
                TronFeeRow(
                    Res.string.tron_resource_energy,
                    "64895",
                    listOf(TronFeeDetail(Res.string.tron_fee_energy_own, "20000"), TronFeeDetail(null, "4.4895 TRX")),
                ),
                TronFeeRow(
                    Res.string.tron_resource_bandwidth,
                    "345",
                    listOf(TronFeeDetail(Res.string.tron_fee_covered_free)),
                ),
            ),
            rows,
        )
    }

    @Test
    fun toTronFeeRows_unactivatedRecipient_showsBurnedBandwidthOnly() {
        val rows = listOf(
            Fee.Bandwidth(points = 269, price = 1_000, burnInSuns = 100_000),
            Fee.AccountActivation(amount = 1_000_000),
        ).toTronFeeBreakdown(TRX_DECIMALS).toTronFeeRows(formatter, "TRX", TRX_DECIMALS)

        assertEquals(
            listOf(TronFeeRow(Res.string.tron_resource_bandwidth, "269", listOf(TronFeeDetail(null, "0.1 TRX")))),
            rows,
        )
    }

    private val formatter = mockk<IAppNumberFormatter> {
        every { format(any(), 0, 0) } answers { firstArg<Number>().toString() }
        every { formatCoinFull(any(), any(), any()) } answers {
            "${firstArg<BigDecimal>().stripTrailingZeros().toPlainString()} ${secondArg<String>()}"
        }
    }

    private fun assertAmount(expected: String, actual: BigDecimal) {
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }

    private companion object {
        const val TRX_DECIMALS = 6
    }
}
