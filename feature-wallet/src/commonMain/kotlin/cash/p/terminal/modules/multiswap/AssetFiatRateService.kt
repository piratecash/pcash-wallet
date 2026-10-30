package cash.p.terminal.modules.multiswap

import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.Currency
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.rx2.asFlow

class AssetFiatRateService(
    private val marketKit: MarketKitWrapper,
) {
    fun rate(token: Token, currency: Currency): BigDecimal? =
        marketKit.coinPrice(token.coin.uid, currency.code)?.value

    fun rateFlow(tag: String, token: Token, currency: Currency): Flow<BigDecimal?> =
        marketKit.coinPriceObservable(tag, token.coin.uid, currency.code)
            .asFlow()
            .map { it.value }
            .onStart {
                rate(token, currency)?.let { emit(it) }
            }
}
