package cash.p.terminal.modules.coin

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import cash.p.terminal.R
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsScreen
import cash.p.terminal.modules.coin.coinmarkets.CoinMarketsScreen
import cash.p.terminal.modules.coin.overview.ui.CoinOverviewScreen
import cash.p.terminal.modules.subscription.SubscriptionInfoPage
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.ListEmptyView
import cash.p.terminal.ui_compose.CoinFragmentInput
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.TabItem
import cash.p.terminal.ui_compose.components.Tabs
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui_compose.components.HudHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CoinPage(val input: CoinFragmentInput) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val coinUid = input.coinUid

        CoinScreen(
            coinUid,
            coinViewModel(coinUid),
            navigation,
        )
    }

    // The factory throws for an unknown coin.
    @Composable
    private fun coinViewModel(coinUid: String): CoinViewModel? {
        val owner = checkNotNull(LocalViewModelStoreOwner.current)
        return tryOrNull { ViewModelProvider(owner, CoinModule.Factory(coinUid))[CoinViewModel::class.java] }
    }
}

@Composable
fun CoinScreen(
    coinUid: String,
    coinViewModel: CoinViewModel?,
    navigation: HSNavigation,
) {
    if (coinViewModel != null) {
        CoinTabs(coinViewModel, navigation)
    } else {
        CoinNotFound(coinUid, navigation)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CoinTabs(
    viewModel: CoinViewModel,
    navigation: HSNavigation,
) {
    val tabs = viewModel.tabs
    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = viewModel.fullCoin.coin.code,
                navigationIcon = {
                    HsBackButton(onClick = { navigation.navigateUpSafely() })
                },
                menuItems = buildList {
                    if (viewModel.isWatchlistEnabled) {
                        if (viewModel.isFavorite) {
                            add(
                                MenuItem(
                                    title = TranslatableString.ResString(R.string.CoinPage_Unfavorite),
                                    icon = R.drawable.ic_filled_star_24,
                                    tint = ComposeAppTheme.colors.jacob,
                                    onClick = {
                                        viewModel.onUnfavoriteClick()
                                    }
                                )
                            )
                        } else {
                            add(
                                MenuItem(
                                    title = TranslatableString.ResString(R.string.CoinPage_Favorite),
                                    icon = R.drawable.ic_star_24,
                                    onClick = {
                                        viewModel.onFavoriteClick()
                                    }
                                )
                            )
                        }
                    }
                }
            )
        }
    ) { innerPaddings ->
        Column(
            modifier = Modifier.padding(innerPaddings)
        ) {
            val selectedTab = tabs[pagerState.currentPage]
            val tabItems = tabs.map {
                TabItem(stringResource(id = it.titleResId), it == selectedTab, it)
            }
            Tabs(tabItems, onClick = { tab ->
                coroutineScope.launch {
                    pagerState.scrollToPage(tabs.indexOf(tab))

                    if (tab == CoinModule.Tab.Details && viewModel.shouldShowSubscriptionInfo()) {
                        viewModel.subscriptionInfoShown()

                        delay(1000)
                        navigation.slideFromBottom(SubscriptionInfoPage())
                    }
                }
            })

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false
            ) { page ->
                when (tabs[page]) {
                    CoinModule.Tab.Overview -> {
                        CoinOverviewScreen(
                            fullCoin = viewModel.fullCoin,
                            navigation = navigation
                        )
                    }

                    CoinModule.Tab.Market -> {
                        CoinMarketsScreen(fullCoin = viewModel.fullCoin)
                    }

                    CoinModule.Tab.Details -> {
                        CoinAnalyticsScreen(
                            fullCoin = viewModel.fullCoin,
                            navigation = navigation,
                        )
                    }
                }
            }

            viewModel.successMessage?.let {
                HudHelper.showSuccessMessage(view, it)

                viewModel.onSuccessMessageShown()
            }
        }
    }
}

@Composable
fun CoinNotFound(coinUid: String, navigation: HSNavigation) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = coinUid,
                navigationIcon = {
                    HsBackButton(onClick = { navigation.navigateUpSafely() })
                }
            )
        },
        content = {
            ListEmptyView(
                paddingValues = it,
                text = stringResource(R.string.CoinPage_CoinNotFound, coinUid),
                icon = R.drawable.ic_not_available
            )
        }
    )
}
