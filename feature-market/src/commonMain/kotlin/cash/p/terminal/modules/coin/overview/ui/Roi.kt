package cash.p.terminal.modules.coin.overview.ui

import cash.p.terminal.resources.CoinPage_Performance_Month
import cash.p.terminal.resources.CoinPage_Performance_Week
import cash.p.terminal.resources.Res
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.coin.RoiViewItem
import cash.p.terminal.ui_compose.components.CellSingleLineLawrenceSection
import cash.p.terminal.ui_compose.components.caption_grey
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.models.HsTimePeriod
import org.jetbrains.compose.resources.StringResource
import org.koin.compose.koinInject
import java.math.BigDecimal

@Composable
fun Roi(roi: List<RoiViewItem>) {
    val numberFormatter = koinInject<IAppNumberFormatter>()
    CellSingleLineLawrenceSection(roi) { item ->
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (item) {
                is RoiViewItem.HeaderRowViewItem -> {
                    subhead1_leah(
                        modifier = Modifier.weight(1f),
                        text = item.title,
                        textAlign = TextAlign.Center
                    )
                    item.periods.forEach { period: HsTimePeriod ->
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(ComposeAppTheme.colors.borderDivider)
                        )
                        Text(
                            modifier = Modifier.weight(1f),
                            text = period.periodNameStringResId?.let { stringResource(it) }
                                ?: "",
                            style = ComposeAppTheme.typography.caption,
                            color = ComposeAppTheme.colors.textPrimary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is RoiViewItem.RowViewItem -> {
                    caption_grey(
                        modifier = Modifier.weight(1f),
                        text = item.title,
                        textAlign = TextAlign.Center
                    )
                    item.values.forEach { value ->
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(ComposeAppTheme.colors.borderDivider)
                        )
                        val text: String
                        val color: Color
                        if (value != null) {
                            val sign = if (value >= BigDecimal.ZERO) "+" else "-"
                            text = numberFormatter.format(value.abs(), 0, 2, sign, "%")
                            color = if (value >= BigDecimal.ZERO) {
                                ComposeAppTheme.colors.statusSuccess
                            } else {
                                ComposeAppTheme.colors.statusError
                            }

                        } else {
                            text = "---"
                            color = ComposeAppTheme.colors.textSecondary
                        }

                        Text(
                            modifier = Modifier.weight(1f),
                            text = text,
                            style = ComposeAppTheme.typography.caption,
                            color = color,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

val HsTimePeriod.periodNameStringResId: StringResource?
    get() = when (this) {
        HsTimePeriod.Week1 -> Res.string.CoinPage_Performance_Week
        HsTimePeriod.Month1 -> Res.string.CoinPage_Performance_Month
        else -> null
    }
