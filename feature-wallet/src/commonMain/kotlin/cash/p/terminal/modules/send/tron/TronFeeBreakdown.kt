package cash.p.terminal.modules.send.tron

import io.horizontalsystems.tronkit.transaction.Fee
import java.math.BigDecimal

data class TronFeeBreakdown(
    val energy: EnergyUse?,
    val bandwidth: BandwidthUse?,
    val activationFee: BigDecimal?,
    val totalFee: BigDecimal,
    val feeLimit: Long?,
) {
    data class EnergyUse(
        val required: Long,
        val fromAccount: Long,
        val fromContract: Long,
        val burned: BigDecimal,
    )

    data class BandwidthUse(
        val points: Long,
        val source: Fee.Bandwidth.Source,
        val burned: BigDecimal,
    )
}

fun List<Fee>.toTronFeeBreakdown(decimals: Int): TronFeeBreakdown {
    fun Long.toCoin() = toBigDecimal().movePointLeft(decimals)

    val energy = filterIsInstance<Fee.Energy>().firstOrNull()
    val bandwidth = filterIsInstance<Fee.Bandwidth>().firstOrNull()
    return TronFeeBreakdown(
        energy = energy?.let {
            TronFeeBreakdown.EnergyUse(it.required, it.fromAccount, it.fromContract, it.feeInSuns.toCoin())
        },
        bandwidth = bandwidth?.let {
            TronFeeBreakdown.BandwidthUse(it.points, it.source, it.feeInSuns.toCoin())
        },
        activationFee = filterIsInstance<Fee.AccountActivation>().firstOrNull()?.feeInSuns?.toCoin(),
        totalFee = sumOf { it.feeInSuns }.toCoin(),
        // fee_limit must cover the whole call, whoever pays for the energy.
        feeLimit = energy?.feeLimitInSuns,
    )
}
