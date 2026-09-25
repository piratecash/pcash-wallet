package cash.p.terminal.modules.info

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.OverallScore
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.ScoreCategory
import cash.p.terminal.modules.info.ui.InfoHeader
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoTextBody
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.headline2_jacob
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import java.math.BigDecimal

class OverallScoreInfoPage(val input: ScoreCategory) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val categoryScores = getScores(input)
        InfoScreen(
            input.title,
            input.description,
            categoryScores,
            onBackPress = { navigation.navigateUpSafely() }
        )
    }
}

@Composable
private fun InfoScreen(
    categoryTitle: Int,
    description: Int,
    categoryScores: Map<OverallScore, String>,
    onBackPress: () -> Unit
) {
    Surface(color = ComposeAppTheme.colors.tyler) {
        Column {
            AppBar(
                navigationIcon = {
                    HsBackButton(onClick = onBackPress)
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                InfoHeader(R.string.Coin_Analytics_OverallScore)
                VSpacer(12.dp)
                headline2_jacob(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    text = stringResource(categoryTitle)
                )
                InfoTextBody(stringResource(description))
                VSpacer(12.dp)
                val items = buildList<@Composable () -> Unit> {
                    categoryScores.forEach { (score, value) ->
                        val color = when (score) {
                            OverallScore.Excellent -> Color(0xFF05C46B)
                            OverallScore.Good -> Color(0xFFFFA800)
                            OverallScore.Fair -> Color(0xFFFF7A00)
                            OverallScore.Poor -> Color(0xFFFF3D00)
                        }
                        add {
                            RowUniversal(
                                modifier = Modifier.padding(horizontal = 16.dp),
                            ) {
                                Image(
                                    painter = painterResource(score.icon),
                                    contentDescription = null
                                )
                                HSpacer(8.dp)
                                Text(
                                    text = stringResource(score.title).uppercase(),
                                    style = ComposeAppTheme.typography.subhead1,
                                    color = color,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = value,
                                    style = ComposeAppTheme.typography.subhead1,
                                    color = color,
                                )
                            }
                        }
                    }
                }
                CellUniversalLawrenceSection(items)
                VSpacer(24.dp)
            }
        }
    }
}

private fun formatFiat(number: Int): String {
    val currency = App.currencyManager.baseCurrency
    return App.numberFormatter.formatFiatShort(BigDecimal(number), currency.symbol, 0)
}

private fun formatNumber(number: Int): String {
    return App.numberFormatter.formatNumberShort(BigDecimal(number), 2)
}

private fun getScores(scoreCategory: ScoreCategory?): Map<OverallScore, String> {
    return when (scoreCategory) {
        ScoreCategory.CexScoreCategory -> {
            mapOf(
                OverallScore.Excellent to "> " + formatFiat(10_000_000),
                OverallScore.Good to "> " + formatFiat(5_000_000),
                OverallScore.Fair to "> " + formatFiat(1_000_000),
                OverallScore.Poor to "< " + formatFiat(1_000_000),
            )
        }

        ScoreCategory.DexVolumeScoreCategory -> {
            mapOf(
                OverallScore.Excellent to "> " + formatFiat(1_000_000),
                OverallScore.Good to "> " + formatFiat(500_000),
                OverallScore.Fair to "> " + formatFiat(100_000),
                OverallScore.Poor to "< " + formatFiat(100_000),
            )
        }

        ScoreCategory.DexLiquidityScoreCategory -> {
            mapOf(
                OverallScore.Excellent to "> " + formatFiat(1_000_000),
                OverallScore.Good to "> " + formatFiat(1_000_000),
                OverallScore.Fair to "> " + formatFiat(500_000),
                OverallScore.Poor to "< " + formatFiat(500_000),
            )
        }

        ScoreCategory.AddressesScoreCategory -> {
            mapOf(
                OverallScore.Excellent to "> 500",
                OverallScore.Good to "> 200",
                OverallScore.Fair to "> 100",
                OverallScore.Poor to "< 100",
            )
        }

        ScoreCategory.TransactionCountScoreCategory -> {
            mapOf(
                OverallScore.Excellent to "> " + formatNumber(10_000),
                OverallScore.Good to "> " + formatNumber(5_000),
                OverallScore.Fair to "> " + formatNumber(1_000),
                OverallScore.Poor to "< " + formatNumber(1_000),
            )
        }

        ScoreCategory.HoldersScoreCategory -> {
            mapOf(
                OverallScore.Excellent to "> " + formatNumber(100_000),
                OverallScore.Good to "> " + formatNumber(50_000),
                OverallScore.Fair to "> " + formatNumber(30_000),
                OverallScore.Poor to "< " + formatNumber(30_000),
            )
        }

        ScoreCategory.TvlScoreCategory -> {
            mapOf(
                OverallScore.Excellent to "> " + formatFiat(200_000_000),
                OverallScore.Good to "> " + formatFiat(100_000_000),
                OverallScore.Fair to "> " + formatFiat(50_000_000),
                OverallScore.Poor to "< " + formatFiat(50_000_000),
            )
        }

        null -> emptyMap()
    }
}
