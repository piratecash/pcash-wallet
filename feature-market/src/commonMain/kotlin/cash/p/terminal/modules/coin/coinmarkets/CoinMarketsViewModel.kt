package cash.p.terminal.modules.coin.coinmarkets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.resources.CoinPage_MarketsLabel_Verified
import cash.p.terminal.resources.Res
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.coin.MarketTickerViewItem
import cash.p.terminal.strings.helpers.TranslatableString
import io.horizontalsystems.core.IAppNumberFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow

class CoinMarketsViewModel(
    private val service: CoinMarketsService,
    private val numberFormatter: IAppNumberFormatter,
) : ViewModel() {
    val verifiedMenu by service::verifiedMenu

    private val _viewState = MutableStateFlow<ViewState>(ViewState.Loading)
    val viewState: StateFlow<ViewState> = _viewState

    private val _viewItems = MutableStateFlow<List<MarketTickerViewItem>?>(null)
    val viewItems: StateFlow<List<MarketTickerViewItem>?> = _viewItems

    init {
        viewModelScope.launch {
            service.stateObservable.asFlow().collect {
                syncState(it)
            }
        }

        service.start()
    }

    private fun syncState(state: DataState<List<MarketTickerItem>>) {
        state.viewState?.let { _viewState.value = it }

        state.dataOrNull?.let { data ->
            _viewItems.value = data.map { createViewItem(it) }
        }
    }

    private fun createViewItem(item: MarketTickerItem): MarketTickerViewItem {
        return MarketTickerViewItem(
            market = item.market,
            marketImageUrl = item.marketImageUrl,
            pair = "${item.baseCoinCode}/${item.targetCoinCode}",
            volumeFiat = numberFormatter.formatFiatShort(
                item.volumeFiat, service.currency.symbol, service.currency.decimal
            ),
            volumeToken = numberFormatter.formatCoinShort(item.volumeToken, item.baseCoinCode, 8),
            tradeUrl = item.tradeUrl,
            badge = if (item.verified) TranslatableString.ResString(Res.string.CoinPage_MarketsLabel_Verified) else null
        )
    }

    override fun onCleared() {
        service.stop()
    }

    fun onErrorClick() {
        service.start()
    }

    fun toggleVerifiedType(verifiedType: VerifiedType) {
        service.setVerifiedType(verifiedType)
    }

}
