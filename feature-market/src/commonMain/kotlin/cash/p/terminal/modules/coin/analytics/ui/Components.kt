package cash.p.terminal.modules.coin.analytics.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.resources.ic_info_20
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.BoxItem
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.OverallScore
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.chartview.ChartBars
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui.compose.components.StackedBarChart
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.headline1_bran
import cash.p.terminal.ui_compose.components.subhead1_grey
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import io.horizontalsystems.chartview.ChartMinimal

@Composable
fun AnalyticsBlockHeader(
    title: String,
    onInfoClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        subhead1_grey(text = title)
        onInfoClick?.let {
            HsIconButton(
                modifier = Modifier.size(20.dp),
                onClick = it
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_info_20),
                    contentDescription = "info button",
                    tint = ComposeAppTheme.colors.iconSecondary
                )
            }
        }
    }
}

@Composable
fun AnalyticsContentNumber(
    number: String,
    period: String? = null
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        headline1_bran(text = number)
        period?.let {
            HSpacer(8.dp)
            subhead1_grey(
                text = it,
                modifier = Modifier.padding(bottom = 1.dp),
            )
        }
    }
    VSpacer(12.dp)
}

@Composable
fun AnalyticsFooterCell(
    title: BoxItem,
    value: BoxItem?,
    showRightArrow: Boolean,
    showTopDivider: Boolean = true,
    cellAction: CoinAnalyticsModule.ActionType?,
    onActionClick: (CoinAnalyticsModule.ActionType) -> Unit
) {
    if (showTopDivider) {
        HorizontalDivider(
            thickness = 1.dp,
            color = ComposeAppTheme.colors.borderDivider,
            modifier = Modifier.fillMaxWidth()
        )
    }
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = if (cellAction != null) {
            { onActionClick.invoke(cellAction) }
        } else {
            null
        }
    ) {
        BoxItemCell(
            modifier = Modifier.weight(1f),
            boxItem = title,
            onActionClick = onActionClick
        )
        value?.let {
            BoxItemCell(
                boxItem = it,
                onActionClick = onActionClick
            )
        }

        if (showRightArrow) {
            HSpacer(8.dp)
            Image(
                painter = painterResource(Res.drawable.ic_arrow_right),
                contentDescription = "arrow icon"
            )
        }
    }
}

@Composable
private fun BoxItemCell(
    modifier: Modifier = Modifier,
    boxItem: BoxItem,
    onActionClick: (CoinAnalyticsModule.ActionType) -> Unit
) {
    when (boxItem) {
        is BoxItem.IconTitle -> {
            Image(
                painter = boxItem.image.painter(),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
            HSpacer(16.dp)
            subhead2_grey(
                text = boxItem.text.getString(),
                modifier = modifier.padding(end = 8.dp)
            )
        }

        is BoxItem.OverallScoreValue -> {
            RatingCell(boxItem.score)
        }

        is BoxItem.Title -> {
            subhead2_grey(
                text = boxItem.text.getString(),
                modifier = modifier.padding(end = 8.dp)
            )
        }

        is BoxItem.TitleWithInfo -> {
            subhead2_grey(
                text = boxItem.text.getString(),
            )
            HSpacer(8.dp)
            HsIconButton(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(20.dp),
                onClick = {
                    onActionClick.invoke(boxItem.action)
                }
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_info_20),
                    contentDescription = "info button",
                    tint = ComposeAppTheme.colors.iconSecondary,
                )
            }
            Spacer(modifier)
        }

        is BoxItem.Value -> {
            subhead1_leah(text = boxItem.text)
        }
    }
}

@Composable
private fun RatingCell(rating: OverallScore) {
    val color = when (rating) {
        OverallScore.Excellent -> ComposeAppTheme.colors.statusSuccess
        OverallScore.Good -> ComposeAppTheme.colors.statusWarning
        OverallScore.Fair -> Color(0xFFFF7A00)
        OverallScore.Poor -> Color(0xFFFF3D00)
    }
    Text(
        text = stringResource(rating.title).uppercase(),
        style = ComposeAppTheme.typography.subhead1,
        color = color,
    )
    HSpacer(8.dp)
    Image(
        painter = painterResource(rating.icon),
        contentDescription = null
    )
}

@Composable
fun AnalyticsContainer(
    showFooterDivider: Boolean = true,
    sectionTitle: @Composable (RowScope.() -> Unit)? = null,
    titleRow: @Composable (() -> Unit)? = null,
    sectionDescription: @Composable (() -> Unit)? = null,
    bottomRows: @Composable ColumnScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    VSpacer(12.dp)
    sectionTitle?.let {
        HorizontalDivider(
            thickness = 1.dp,
            color = ComposeAppTheme.colors.borderDivider,
            modifier = Modifier.fillMaxWidth()
        )
        RowUniversal(content = it)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.surfacePrimary)
    ) {
        titleRow?.invoke()
        content.invoke()
        if (showFooterDivider) {
            HorizontalDivider(
                thickness = 1.dp,
                color = ComposeAppTheme.colors.borderDivider,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Column(
            content = bottomRows
        )
    }
    sectionDescription?.invoke()
}

@Composable
fun AnalyticsChart(
    analyticChart: CoinAnalyticsModule.AnalyticChart,
) {
    when (analyticChart) {
        is CoinAnalyticsModule.AnalyticChart.StackedBars -> {
            StackedBarChart(analyticChart.data, modifier = Modifier.padding(horizontal = 16.dp))
            VSpacer(12.dp)
        }

        is CoinAnalyticsModule.AnalyticChart.Bars -> {
            ChartBars(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(60.dp),
                chartData = analyticChart.data
            )
            VSpacer(12.dp)
        }

        is CoinAnalyticsModule.AnalyticChart.Line -> {
            ChartMinimal(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(60.dp),
                data = analyticChart.data
            )
            VSpacer(12.dp)
        }

        is CoinAnalyticsModule.AnalyticChart.TechAdvice -> {
            TechnicalAdviceBlock(
                adviceTitle = analyticChart.data.adviceTitle,
                detailText = analyticChart.data.detailText,
                sliderPosition = analyticChart.data.sliderPosition
                )
        }
    }
}
