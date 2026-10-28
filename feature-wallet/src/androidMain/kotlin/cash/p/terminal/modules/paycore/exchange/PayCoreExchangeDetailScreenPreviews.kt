package cash.p.terminal.modules.paycore.exchange

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.core.providers.SharedAppConfig
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun PayCoreExchangeDetailScreenPreview() {
    ComposeAppTheme {
        PayCoreExchangeDetailScreen(
            uiState = PayCoreExchangeDetailUiState(
                amountSentFormatted = "10,000 RUB",
                amountGotFormatted = "105.5 USDT",
                coinImageUrlIn = "https://p.cash/storage/coins/rub/image.png",
                coinImageUrlOut = null,
                priceFormatted = "1 RUB = 0.01055 USDT",
                priceInvFormatted = "1 USDT = 94.78 RUB",
                dateFormatted = "Mar 30, 2026, 14:30",
                durationFormatted = "5m 12s",
                status = TransactionStatusEnum.EXCHANGING,
                transactionId = "abc-123-def-456",
                supportUrl = SharedAppConfig.payCoreSupportUrl,
            ),
            onBack = {},
        )
    }
}
