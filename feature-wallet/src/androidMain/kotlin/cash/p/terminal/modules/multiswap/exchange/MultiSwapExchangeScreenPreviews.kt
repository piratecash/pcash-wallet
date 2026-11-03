package cash.p.terminal.modules.multiswap.exchange

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.modules.multiswap.providers.ProviderRiskType
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun Leg1HeaderPreview() {
    ComposeAppTheme {
        Column {
            Leg1Header(
                providerName = "STON.fi",
                status = LegStatus.Executing,
                coinIconUrlIn = null,
                coinIconUrlOut = null,
                riskType = ProviderRiskType.Flexible,
            )
            Leg1Header(
                providerName = "STON.fi",
                status = LegStatus.Completed,
                coinIconUrlIn = null,
                coinIconUrlOut = null,
                riskType = ProviderRiskType.Auto,
            )
        }
    }
}

@Preview(name = "Route info")
@Composable
private fun MultiSwapRouteInfoPreview() {
    MultiSwapScreenPreviewContent(
        presentation = MultiSwapExchangePresentation.RouteInfo,
        leg1Status = LegStatus.Pending,
        buttonState = ButtonState.Enabled,
    )
}

@Preview(name = "Execution")
@Composable
private fun MultiSwapExchangeScreenPreview() {
    MultiSwapScreenPreviewContent(
        presentation = MultiSwapExchangePresentation.Execution,
        leg1Status = LegStatus.Executing,
        buttonState = ButtonState.Disabled,
        showContinueLater = true,
    )
}

@Preview(name = "Completed execution")
@Composable
private fun MultiSwapExchangeScreenCompletedPreview() {
    MultiSwapScreenPreviewContent(
        presentation = MultiSwapExchangePresentation.Execution,
        leg1Status = LegStatus.Completed,
        buttonState = ButtonState.Enabled,
        showContinueLater = true,
        timeRemainingProgress = 0.7f,
    )
}
