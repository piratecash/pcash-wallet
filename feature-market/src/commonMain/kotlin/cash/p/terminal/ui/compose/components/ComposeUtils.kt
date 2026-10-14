package cash.p.terminal.ui.compose.components

import androidx.compose.runtime.Composable
import cash.p.terminal.core.getKoinInstance
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.Value
import org.koin.compose.koinInject
import java.math.BigDecimal

@Composable
fun formatValueAsDiff(value: Value): String =
    koinInject<IAppNumberFormatter>().formatValueAsDiff(value)

fun diffText(diff: BigDecimal?): String {
    if (diff == null) return ""
    val sign = diff.diffSign()
    return getKoinInstance<IAppNumberFormatter>().format(diff.abs(), 0, 2, sign, "%")
}

fun BigDecimal.diffSign(): String {
    return when {
        this == BigDecimal.ZERO -> ""
        this >= BigDecimal.ZERO -> "+"
        else -> "-"
    }
}
