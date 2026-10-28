package cash.p.terminal.modules.paycore.payment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.modules.multiswap.TokenRowPure
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Send_Confirmation_YouSend
import cash.p.terminal.resources.Swap_ToAmountTitle
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.alternativeImageUrl
import cash.p.terminal.wallet.badge
import cash.p.terminal.wallet.imageUrl
import io.horizontalsystems.core.entities.Currency
import java.math.BigDecimal
import org.jetbrains.compose.resources.stringResource

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun PayCorePaymentScreenPreview() {
    // PriceField uses getKoinInstance<IAppNumberFormatter>() (Koin) which is unavailable in Preview.
    // Preview only the static parts: token rows, service info, button.
    ComposeAppTheme {
        Column(modifier = Modifier.background(ComposeAppTheme.colors.backgroundBase)) {
            TokenRowPure(
                fiatAmount = null,
                currency = Currency("USD", "$", 2),
                title = stringResource(Res.string.Send_Confirmation_YouSend),
                amountColor = ComposeAppTheme.colors.statusError,
                imageUrl = "https://p.cash/storage/coins/rub/image.png",
                alternativeImageUrl = null,
                imagePlaceholder = null,
                badge = null,
                amountFormatted = "1000 RUB"
            )
            TokenRowPure(
                fiatAmount = null,
                borderTop = true,
                currency = Currency("USD", "$", 2),
                title = stringResource(Res.string.Swap_ToAmountTitle),
                amountColor = ComposeAppTheme.colors.statusSuccess,
                imageUrl = null,
                alternativeImageUrl = null,
                imagePlaceholder = null,
                badge = "TRC20",
                amountFormatted = "10.5 USDT"
            )
            ServiceRow()
            ServiceFeeRow(serviceFee = BigDecimal("2"))
        }
    }
}
