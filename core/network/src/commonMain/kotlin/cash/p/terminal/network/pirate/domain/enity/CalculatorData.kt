package cash.p.terminal.network.pirate.domain.enity

data class CalculatorData(
    val items: List<CalculatorItemData>
)

data class CalculatorItemData(
    val periodType: PeriodType,
    val amount: Double,
    val price: Map<String, Double>,
)

/** Annual yield as a percentage of the stake the calculator was queried with. */
fun CalculatorData.annualRoiPercent(stake: Double): Double? {
    if (stake <= 0) return null
    val yearly = items.find { it.periodType == PeriodType.YEAR } ?: return null
    return yearly.amount / stake * 100
}
