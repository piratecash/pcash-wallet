package cash.p.terminal.modules.multiswap

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.core.HSCaution
import cash.p.terminal.modules.multiswap.action.ISwapProviderAction
import cash.p.terminal.modules.multiswap.providers.IMultiSwapProvider
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings
import cash.p.terminal.modules.multiswap.settings.ISwapSetting
import cash.p.terminal.modules.multiswap.ui.DataField
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.uniswap_v3
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Token
import java.math.BigDecimal

@Preview
@Composable
private fun SwapSelectProviderScreenPreview() {
    fun previewProvider(providerId: String, providerTitle: String) = object : IMultiSwapProvider {
        override val id = providerId
        override val title = providerTitle
        override val icon = Res.drawable.uniswap_v3

        override val walletUseCase get() = throw NotImplementedError()
        override val mevProtectionAvailable = false
        override suspend fun supports(token: Token) = true
        override suspend fun fetchQuote(
            tokenIn: Token,
            tokenOut: Token,
            amountIn: BigDecimal,
            settings: Map<String, Any?>
        ): ISwapQuote = throw NotImplementedError()

        override suspend fun fetchFinalQuote(
            tokenIn: Token,
            tokenOut: Token,
            amountIn: BigDecimal,
            swapSettings: Map<String, Any?>,
            sendTransactionSettings: SendTransactionSettings?,
            swapQuote: ISwapQuote
        ): ISwapFinalQuote = throw NotImplementedError()
    }

    val previewQuote = object : ISwapQuote {
        override val amountOut = BigDecimal("456.78")
        override val priceImpact: BigDecimal? = null
        override val fields = emptyList<DataField>()
        override val settings = emptyList<ISwapSetting>()
        override val tokenIn: Token get() = throw NotImplementedError()
        override val tokenOut: Token get() = throw NotImplementedError()
        override val amountIn = BigDecimal("0.1234")
        override val actionRequired: ISwapProviderAction? =
            null
        override val cautions = emptyList<HSCaution>()
        override val estimationTime: Long? = null
    }

    val quote1 = SwapProviderQuote(
        provider = previewProvider("preview-uniswap", "Uniswap V3"),
        swapQuote = previewQuote
    )
    val quote2 = SwapProviderQuote(
        provider = previewProvider("preview-pancake", "PancakeSwap"),
        swapQuote = previewQuote
    )

    ComposeAppTheme(darkTheme = false) {
        SwapSelectProviderScreen(
            onClickClose = {},
            onClickSettings = {},
            mandatoryProviderIds = emptySet(),
            disabledProviderIds = emptySet(),
            sortType = ProviderSortType.BestPrice,
            onSortTypeChange = {},
            onToggleProvider = { _, _ -> },
            quotes = listOf(
                QuoteViewItem(
                    quote = quote1,
                    tokenAmount = "456.78 DAI",
                    fiatAmount = "$456.78",
                    diffWithFirst = null,
                    rateFrom = "1 ETH",
                    rateTo = "100 PIRATE",
                    estimationTime = 793
                ),
                QuoteViewItem(
                    quote = quote2,
                    tokenAmount = "455.12 DAI",
                    fiatAmount = "$455.12",
                    diffWithFirst = BigDecimal("-1.66"),
                    rateFrom = "1 ETH",
                    rateTo = "99 PIRATE",
                    estimationTime = null
                ),
            ),
            currentQuote = quote1,
            onSelectQuote = {},
            swapRates = {}
        )
    }
}
