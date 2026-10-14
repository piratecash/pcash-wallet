package cash.p.terminal.modules.coin.analytics.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.BoxItem
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.OverallScore
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.ScoreCategory
import cash.p.terminal.resources.CoinAnalytics_ThreeDots
import cash.p.terminal.resources.Res
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.StackBarSlice
import cash.p.terminal.ui.compose.components.StackedBarChart
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource

@Preview
@Composable
private fun HoldersBlockLockedPreview() {
    val slices = listOf(
        StackBarSlice(value = 50.34f, color = Color(0xBF808085)),
        StackBarSlice(value = 37.75f, color = Color(0x80808085)),
        StackBarSlice(value = 11.9f, color = Color(0x40808085)),
    )
    ComposeAppTheme {
        AnalyticsContainer(
            titleRow = {
                AnalyticsBlockHeader(
                    title = "Holders",
                    onInfoClick = {}
                )
            },
            bottomRows = {
                AnalyticsFooterCell(
                    title = BoxItem.Title(TranslatableString.PlainString("Blockchain 1")),
                    value = BoxItem.Value(stringResource(Res.string.CoinAnalytics_ThreeDots)),
                    showRightArrow = true,
                    cellAction = CoinAnalyticsModule.ActionType.Preview,
                    onActionClick = {}
                )
                AnalyticsFooterCell(
                    title = BoxItem.Title(TranslatableString.PlainString("Blockchain 2")),
                    value = BoxItem.Value(stringResource(Res.string.CoinAnalytics_ThreeDots)),
                    showRightArrow = true,
                    cellAction = CoinAnalyticsModule.ActionType.Preview,
                    onActionClick = {}
                )
                AnalyticsFooterCell(
                    title = BoxItem.Title(TranslatableString.PlainString("Blockchain 3")),
                    value = BoxItem.Value(stringResource(Res.string.CoinAnalytics_ThreeDots)),
                    showRightArrow = true,
                    cellAction = CoinAnalyticsModule.ActionType.Preview,
                    onActionClick = {}
                )
            }
        ) {
            AnalyticsContentNumber(
                number = "•••",
            )
            VSpacer(12.dp)
            StackedBarChart(slices, modifier = Modifier.padding(horizontal = 16.dp))
            VSpacer(16.dp)
        }
    }
}
@Preview
@Composable
private fun AnalyticsBarChartDisabledPreview() {
    ComposeAppTheme {
        AnalyticsContainer(
            titleRow = {
                AnalyticsBlockHeader(
                    title = "Dex Volume",
                    onInfoClick = {}
                )
            },
            bottomRows = {
                AnalyticsFooterCell(
                    title = BoxItem.Title(TranslatableString.PlainString("30-Day Rank")),
                    value = BoxItem.Value("•••"),
                    showRightArrow = true,
                    cellAction = CoinAnalyticsModule.ActionType.Preview,
                    onActionClick = {}
                )
            }
        ) {
            AnalyticsContentNumber(
                number = "•••",
            )
            AnalyticsChart(
                CoinAnalyticsModule.zigzagPlaceholderAnalyticChart(false),
            )
            VSpacer(12.dp)
        }
    }
}
@Preview
@Composable
private fun AnalyticsLineChartDisabledPreview() {
    ComposeAppTheme {
        AnalyticsContainer(
            titleRow = {
                AnalyticsBlockHeader(
                    title = "Dex Volume",
                    onInfoClick = {}
                )
            },
            bottomRows = {
                AnalyticsFooterCell(
                    title = BoxItem.Title(TranslatableString.PlainString("30-Day Rank")),
                    value = BoxItem.Value("#19"),
                    showRightArrow = true,
                    cellAction = CoinAnalyticsModule.ActionType.Preview,
                    onActionClick = {}
                )
            }
        ) {
            AnalyticsContentNumber(
                number = "•••",
            )
            AnalyticsChart(
                CoinAnalyticsModule.zigzagPlaceholderAnalyticChart(true),
            )
            VSpacer(12.dp)
        }
    }
}
@Preview
@Composable
private fun HoldersBlockPreview() {
    val slices = listOf(
        StackBarSlice(value = 60f, color = Color(0xFF6B7196)),
        StackBarSlice(value = 31f, color = Color(0xFFF3BA2F)),
        StackBarSlice(value = 8f, color = Color(0xFF8247E5)),
        StackBarSlice(value = 1f, color = Color(0xFFD74F49))
    )
    ComposeAppTheme {
        AnalyticsContainer(
            titleRow = {
                AnalyticsBlockHeader(
                    title = "Defi Cap",
                    onInfoClick = {}
                )
            },
            bottomRows = {
                AnalyticsFooterCell(
                    title = BoxItem.Title(TranslatableString.PlainString("Chain 1")),
                    value = BoxItem.Value("•••"),
                    showRightArrow = true,
                    cellAction = CoinAnalyticsModule.ActionType.Preview,
                    onActionClick = {}
                )
                AnalyticsFooterCell(
                    title = BoxItem.Title(TranslatableString.PlainString("Chain 2")),
                    value = BoxItem.Value("•••"),
                    showRightArrow = true,
                    cellAction = CoinAnalyticsModule.ActionType.Preview,
                    onActionClick = {}
                )
            }
        ) {
            AnalyticsContentNumber(
                number = "\$2.46B",
                period = "last 30d"
            )
            VSpacer(12.dp)
            StackedBarChart(slices, modifier = Modifier.padding(horizontal = 16.dp))
            VSpacer(16.dp)
        }
    }
}
@Preview
@Composable
private fun AnalyticsRatingScalePreview() {
    ComposeAppTheme {
        AnalyticsContainer(
            titleRow = {
                AnalyticsBlockHeader(
                    title = "Dex Volume",
                    onInfoClick = {}
                )
            },
            bottomRows = {
                AnalyticsFooterCell(
                    title = BoxItem.TitleWithInfo(
                        TranslatableString.PlainString("Rating Scale"),
                        CoinAnalyticsModule.ActionType.OpenOverallScoreInfo(ScoreCategory.CexScoreCategory)
                    ),
                    value = BoxItem.OverallScoreValue(OverallScore.Fair),
                    showRightArrow = false,
                    cellAction = null,
                    onActionClick = {}
                )
            }
        ) {
            AnalyticsContentNumber(
                number = "•••",
            )
            AnalyticsChart(
                CoinAnalyticsModule.zigzagPlaceholderAnalyticChart(true),
            )
            VSpacer(12.dp)
        }
    }
}
