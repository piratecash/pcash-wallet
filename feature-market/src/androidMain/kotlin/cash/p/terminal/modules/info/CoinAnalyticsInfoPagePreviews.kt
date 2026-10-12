package cash.p.terminal.modules.info

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.AnalyticInfo
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun CoinAnalyticsInfoScreenPreview() {
    ComposeAppTheme {
        CoinAnalyticsInfoScreen(
            AnalyticInfo.CexVolumeInfo,
        ) {}
    }
}
