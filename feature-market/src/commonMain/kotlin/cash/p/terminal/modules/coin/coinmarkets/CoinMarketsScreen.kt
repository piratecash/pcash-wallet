package cash.p.terminal.modules.coin.coinmarkets

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import cash.p.terminal.resources.CoinPage_NoDataAvailable
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SyncError
import cash.p.terminal.resources.ic_no_data
import cash.p.terminal.resources.ic_platform_placeholder_24
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.coin.MarketTickerViewItem
import cash.p.terminal.modules.coin.overview.ui.Loading
import cash.p.terminal.modules.market.MarketDataValue
import cash.p.terminal.ui_compose.Select
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.ButtonSecondaryToggle
import cash.p.terminal.ui_compose.components.HeaderSorting
import cash.p.terminal.ui.compose.components.ListEmptyView
import cash.p.terminal.ui.compose.components.ListErrorView
import cash.p.terminal.ui.compose.components.MarketCoinFirstRow
import cash.p.terminal.ui.compose.components.MarketCoinSecondRow
import cash.p.terminal.ui_compose.components.SectionItemBorderedRowUniversalClear
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.entities.FullCoin
import io.horizontalsystems.chartview.rememberAsyncImagePainterWithFallback
import kotlinx.coroutines.launch

@Composable
fun CoinMarketsScreen(
    fullCoin: FullCoin
) {
    val viewModel = koinViewModel<CoinMarketsViewModel> { parametersOf(fullCoin) }

    var scrollToTopAfterUpdate by rememberSaveable { mutableStateOf(false) }
    val viewItemState by viewModel.viewState.collectAsStateWithLifecycle()
    val viewItems by viewModel.viewItems.collectAsStateWithLifecycle()

    Surface(color = ComposeAppTheme.colors.backgroundBase) {
        Crossfade(viewItemState, label = "") { viewItemState ->
            when (viewItemState) {
                ViewState.Loading -> {
                    Loading()
                }
                is ViewState.Error -> {
                    ListErrorView(stringResource(Res.string.SyncError), viewModel::onErrorClick)
                }
                ViewState.Success -> {
                    viewItems?.let { items ->
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (items.isEmpty()) {
                                ListEmptyView(
                                    text = stringResource(Res.string.CoinPage_NoDataAvailable),
                                    icon = Res.drawable.ic_no_data
                                )
                            } else {
                                CoinMarketsMenu(
                                    viewModel.verifiedMenu,
                                ) {
                                    viewModel.toggleVerifiedType(it)
                                    scrollToTopAfterUpdate = true
                                }
                                CoinMarketList(items, scrollToTopAfterUpdate)
                                if (scrollToTopAfterUpdate) {
                                    scrollToTopAfterUpdate = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CoinMarketsMenu(
    menuVerified: Select<VerifiedType>,
    onToggleVerified: (VerifiedType) -> Unit,
) {

    var verifiedType by remember { mutableStateOf(menuVerified) }

    HeaderSorting(borderTop = true, borderBottom = true) {
        Spacer(Modifier.weight(1f))
        ButtonSecondaryToggle(
            modifier = Modifier.padding(end = 16.dp),
            select = verifiedType,
            onSelect = {
                onToggleVerified.invoke(it)
                verifiedType = Select(it, verifiedType.options)
            }
        )
    }
}

@Composable
fun CoinMarketList(
    items: List<MarketTickerViewItem>,
    scrollToTop: Boolean,
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LazyColumn(state = listState) {
        items(items) { item ->
            CoinMarketCell(
                name = item.market,
                subtitle = item.pair,
                iconUrl = item.marketImageUrl ?: "",
                volumeToken = item.volumeToken,
                marketDataValue = MarketDataValue.Volume(item.volumeFiat),
                tradeUrl = item.tradeUrl,
                badge = item.badge
            )
        }
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
        if (scrollToTop) {
            coroutineScope.launch {
                listState.scrollToItem(0)
            }
        }
    }
}

@Composable
fun CoinMarketCell(
    name: String,
    subtitle: String,
    iconUrl: String,
    volumeToken: String,
    marketDataValue: MarketDataValue,
    tradeUrl: String?,
    badge: TranslatableString?
) {
    val urlOpener = LocalUrlOpener.current
    SectionItemBorderedRowUniversalClear(
        onClick = tradeUrl?.let {
            { urlOpener.open(it) }
        },
        borderBottom = true
    ) {
        if(iconUrl.isNotEmpty()) {
            Image(
                painter = rememberAsyncImagePainterWithFallback(
                    model = iconUrl,
                    error = painterResource(Res.drawable.ic_platform_placeholder_24)
                ),
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            MarketCoinFirstRow(name, volumeToken, badge = badge?.getString())
            Spacer(modifier = Modifier.height(3.dp))
            MarketCoinSecondRow(subtitle, marketDataValue, null)
        }
    }
}
