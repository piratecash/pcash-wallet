package cash.p.terminal.featureStacking.ui.stackingCoinScreen

import cash.p.terminal.featureStacking.ui.StakingChartDisplayMode
import cash.p.terminal.featureStacking.ui.StakingIncomePoint
import cash.p.terminal.featureStacking.ui.StakingIncomeSnapshot
import io.horizontalsystems.core.IAppNumberFormatter

internal const val COIN_LABEL_FRACTION_DIGITS = 5

/** Header value and hint for [point]; fiat is always labelled with the currency of the snapshot it came from. */
internal fun StakingIncomeSnapshot.labels(
    point: StakingIncomePoint,
    ticker: String,
    noFiatEstimate: String,
    numberFormatter: IAppNumberFormatter,
): Pair<String, String?> {
    val coinsLabel = "${numberFormatter.formatNumberShort(point.coins, COIN_LABEL_FRACTION_DIGITS)} $ticker"
    val fiatLabel = point.fiat?.let { numberFormatter.formatFiatFull(it, currency.symbol) } ?: noFiatEstimate
    return when (mode) {
        StakingChartDisplayMode.COINS -> coinsLabel to null
        StakingChartDisplayMode.FIAT -> fiatLabel to null
        StakingChartDisplayMode.BOTH -> coinsLabel to fiatLabel
    }
}
