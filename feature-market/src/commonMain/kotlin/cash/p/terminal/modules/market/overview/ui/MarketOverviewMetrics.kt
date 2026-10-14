package cash.p.terminal.modules.market.overview.ui

import cash.p.terminal.resources.NotAvailable
import cash.p.terminal.resources.Res
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.market.metricspage.MetricsPage
import cash.p.terminal.modules.market.overview.MarketOverviewModule
import cash.p.terminal.modules.market.tvl.TvlPage
import cash.p.terminal.modules.metricchart.MetricsType
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.ui.extensions.MetricData
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.chartview.ChartMinimal
import io.horizontalsystems.core.IAppNumberFormatter
import java.math.BigDecimal
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MetricChartsView(marketMetrics: MarketOverviewModule.MarketMetrics, navigation: HSNavigation) {
    MarketsHorizontalCards(4) { page ->
        ChartView(marketMetrics[page], navigation)
    }
}

@Composable
private fun ChartView(metricsData: MetricData, navigation: HSNavigation) {
    val numberFormatter = koinInject<IAppNumberFormatter>()
    Card(
        modifier = Modifier
            .height(105.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                openMetricsPage(metricsData.type, navigation)
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ComposeAppTheme.colors.surfacePrimary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
//            caption_grey(text = stringResource(metricsData.type.title))
            Spacer(modifier = Modifier.height(10.dp))
            if (metricsData.value != null) {
                Text(
                    text = metricsData.value,
                    style = ComposeAppTheme.typography.headline1,
                    color = ComposeAppTheme.colors.textPrimary,
                )
            } else {
                Text(
                    text = stringResource(Res.string.NotAvailable),
                    style = ComposeAppTheme.typography.headline1,
                    color = ComposeAppTheme.colors.textDisabled,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                if (metricsData.diff != null) {
                    val sign = if (metricsData.diff >= BigDecimal.ZERO) "+" else "-"
                    Text(
                        text = numberFormatter.format(metricsData.diff.abs(), 0, 2, sign, "%"),
                        style = ComposeAppTheme.typography.subhead1,
                        color = if (metricsData.diff >= BigDecimal.ZERO) {
                            ComposeAppTheme.colors.statusSuccess
                        } else {
                            ComposeAppTheme.colors.statusError
                        },
                    )
                } else {
                    Text(
                        text = "----",
                        style = ComposeAppTheme.typography.subhead1,
                        color = ComposeAppTheme.colors.textDisabled,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                metricsData.chartData?.let { chartData ->
                    ChartMinimal(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 3.dp, bottom = 6.dp)
                            .height(24.dp),
                        data = chartData
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private fun openMetricsPage(metricsType: MetricsType, navigation: HSNavigation) {
    if (metricsType == MetricsType.TvlInDefi) {
        navigation.slideFromBottom(TvlPage())
    } else {
        navigation.slideFromBottom(MetricsPage(metricsType))
    }
}
