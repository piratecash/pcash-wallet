package cash.p.terminal.featureStacking.ui.staking

import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize

@CommonParcelize
enum class StackingType(
    val value: String,
    val minStackingAmount: Int,
    val maturationHours: Int,
    val accrualIntervalHours: Int,
) : CommonParcelable {
    PCASH("PIRATE", 100, 8, 1),
    COSANTA("COSA", 1, 24, 12);

    val maxHoursUntilFirstAccrual: Int
        get() = maturationHours + accrualIntervalHours
}
