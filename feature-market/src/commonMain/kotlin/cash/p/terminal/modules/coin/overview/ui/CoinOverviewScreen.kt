package cash.p.terminal.modules.coin.overview.ui

import cash.p.terminal.resources.BalanceSyncError_Title
import cash.p.terminal.resources.Button_Hide
import cash.p.terminal.resources.Button_Show
import cash.p.terminal.resources.CoinPage_Indicators
import cash.p.terminal.resources.Hud_Text_Copied
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.footer_text
import cash.p.terminal.resources.ic_setting_20
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.core.iconPlaceholder
import cash.p.terminal.modules.coin.CoinLink
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.modules.coin.overview.CoinOverviewChartService
import cash.p.terminal.modules.coin.overview.CoinOverviewViewModel
import cash.p.terminal.modules.coin.overview.HudMessageType
import cash.p.terminal.modules.coin.overview.TokenWalletActions
import cash.p.terminal.modules.coin.ui.CoinScreenTitle
import cash.p.terminal.modules.market.MarketPages
import cash.p.terminal.modules.coin.indicators.IndicatorsPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.ui_compose.components.ButtonSecondaryDefault
import cash.p.terminal.ui.compose.components.ListErrorView
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
import cash.p.terminal.ui_compose.components.CellFooter
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HSSwipeRefresh
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.LocalHudMessenger
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import cash.p.terminal.ui_compose.components.UrlOpener
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.alternativeImageUrl
import cash.p.terminal.wallet.entities.FullCoin
import cash.p.terminal.wallet.imageUrl
import cash.p.terminal.wallet.models.LinkType
import io.horizontalsystems.chartview.chart.ChartCurrencyValueFormatterSignificant
import io.horizontalsystems.chartview.chart.ChartViewModel
import io.horizontalsystems.chartview.ui.Chart
import cash.p.terminal.ui_compose.currentYear
import io.horizontalsystems.core.IAppNumberFormatter
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.runtime.getValue

@Composable
fun CoinOverviewScreen(
    fullCoin: FullCoin,
    navigation: HSNavigation
) {
    val koin = getKoin()
    val numberFormatter = koinInject<IAppNumberFormatter>()
    val viewModel = koinViewModel<CoinOverviewViewModel> { parametersOf(fullCoin) }
    val chartViewModel = koinViewModel<ChartViewModel> {
        parametersOf(
            koin.get<CoinOverviewChartService> { parametersOf(fullCoin.coin.uid) },
            ChartCurrencyValueFormatterSignificant(numberFormatter),
        )
    }
    val tokenWalletActions = koinInject<TokenWalletActions>()
    val marketPages = koinInject<MarketPages>()
    val clipboardManager = koinInject<IClipboardManager>()

    val refreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val chartIndicatorsState = viewModel.chartIndicatorsState

    val hudMessenger = LocalHudMessenger.current
    val urlOpener = LocalUrlOpener.current
    val copiedText = stringResource(Res.string.Hud_Text_Copied)

    viewModel.showHudMessage?.let {
        val text = stringResource(it.text)
        when (it.type) {
            HudMessageType.Error -> hudMessenger.showError(text)
            HudMessageType.Success -> hudMessenger.showSuccess(text)
        }

        viewModel.onHudMessageShown()
    }

    if (viewModel.tokenVariants?.items?.any { it.canAddToWallet } == true) {
        tokenWalletActions.Dialogs(navigation)
    }

    HSSwipeRefresh(
        refreshing = refreshing,
        onRefresh = {
            viewModel.refresh()
            chartViewModel.refresh()
        },
        content = {
            Crossfade(viewState, label = "") { viewState ->
                when (viewState) {
                    ViewState.Loading -> {
                        Loading()
                    }

                    ViewState.Success -> {
                        overview?.let { overview ->
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                CoinScreenTitle(
                                    fullCoin.coin.name,
                                    overview.marketCapRank,
                                    fullCoin.coin.imageUrl,
                                    fullCoin.coin.alternativeImageUrl,
                                    fullCoin.iconPlaceholder
                                )

                                Chart(
                                    uiState = chartViewModel.uiState,
                                    getSelectedPointCallback = chartViewModel::getSelectedPoint,
                                    onSelectChartInterval = chartViewModel::onSelectChartInterval
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                CellUniversalLawrenceSection {
                                    RowUniversal(
                                        modifier = Modifier
                                            .height(52.dp)
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                    ) {
                                        subhead2_grey(text = stringResource(Res.string.CoinPage_Indicators))
                                        Spacer(modifier = Modifier.weight(1f))

                                        if (chartIndicatorsState.hasActiveSubscription) {
                                            if (chartIndicatorsState.enabled) {
                                                ButtonSecondaryDefault(
                                                    title = stringResource(Res.string.Button_Hide),
                                                    onClick = {
                                                        viewModel.disableChartIndicators()
                                                    }
                                                )
                                            } else {
                                                ButtonSecondaryDefault(
                                                    title = stringResource(Res.string.Button_Show),
                                                    onClick = {
                                                        viewModel.enableChartIndicators()
                                                    }
                                                )
                                            }
                                            HSpacer(width = 8.dp)
                                            ButtonSecondaryCircle(
                                                icon = Res.drawable.ic_setting_20,
                                                onClick = { navigation.slideFromRight(IndicatorsPage()) }
                                            )
                                        }
                                    }
                                }

                                if (overview.marketData.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    MarketData(overview.marketData)
                                }

                                if (overview.roi.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Roi(overview.roi)
                                }

                                viewModel.tokenVariants?.let { tokenVariants ->
                                    Spacer(modifier = Modifier.height(24.dp))
                                    TokenVariants(
                                        tokenVariants = tokenVariants,
                                        onClickAddToWallet = tokenWalletActions::enable,
                                        onClickRemoveWallet = tokenWalletActions::disable,
                                        onClickCopy = {
                                            clipboardManager.copyText(it)
                                            hudMessenger.showSuccess(copiedText)
                                        },
                                        onClickExplorer = urlOpener::open,
                                    )
                                }

                                if (overview.about.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    About(overview.about)
                                }

                                if (overview.links.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Links(overview.links) { onClick(it, urlOpener, marketPages, navigation) }
                                }

                                Spacer(modifier = Modifier.height(32.dp))
                                CellFooter(text = stringResource(Res.string.footer_text, currentYear()))
                            }
                        }

                    }

                    is ViewState.Error -> {
                        ListErrorView(stringResource(Res.string.BalanceSyncError_Title)) {
                            viewModel.retry()
                            chartViewModel.refresh()
                        }
                    }

                }
            }
        },
    )
}

private fun onClick(
    coinLink: CoinLink,
    urlOpener: UrlOpener,
    marketPages: MarketPages,
    navigation: HSNavigation,
) {
    val absoluteUrl = getAbsoluteUrl(coinLink)

    when (coinLink.linkType) {
        LinkType.Guide -> marketPages.markdown(absoluteUrl)?.let { navigation.slideFromRight(it) }
        else -> urlOpener.open(absoluteUrl)
    }
}

private fun getAbsoluteUrl(coinLink: CoinLink) = when (coinLink.linkType) {
    LinkType.Twitter -> "https://twitter.com/${coinLink.url}"
    LinkType.Telegram -> "https://t.me/${coinLink.url}"
    else -> coinLink.url
}

@Composable
fun Error(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        subhead2_grey(text = message)
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = ComposeAppTheme.colors.textSecondary,
            strokeWidth = 2.dp
        )
    }
}
