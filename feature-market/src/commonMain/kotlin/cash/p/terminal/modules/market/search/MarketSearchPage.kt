package cash.p.terminal.modules.market.search

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.coin.CoinPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.CoinPage_Favorite
import cash.p.terminal.resources.CoinPage_Unfavorite
import cash.p.terminal.resources.EmptyResults
import cash.p.terminal.resources.Market_Search
import cash.p.terminal.resources.Market_Search_Sections_PopularTitle
import cash.p.terminal.resources.Market_Search_Sections_RecentTitle
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_not_found
import cash.p.terminal.resources.ic_star_24
import cash.p.terminal.resources.ic_star_off_24
import cash.p.terminal.wallet.alternativeImageUrl
import cash.p.terminal.core.iconPlaceholder
import cash.p.terminal.wallet.imageUrl

import cash.p.terminal.ui_compose.CoinFragmentInput
import cash.p.terminal.modules.market.search.MarketSearchModule.CoinItem
import cash.p.terminal.ui_compose.components.DraggableCardSimple
import cash.p.terminal.ui_compose.components.HeaderStick
import cash.p.terminal.ui_compose.components.HsImage
import cash.p.terminal.ui.compose.components.ListEmptyView
import cash.p.terminal.ui.compose.components.SearchBar
import cash.p.terminal.ui_compose.components.SectionItemBorderedRowUniversalClear
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.entities.Coin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MarketSearchPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<MarketSearchViewModel>()
        MarketSearchScreen(viewModel, navigation)
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MarketSearchScreen(viewModel: MarketSearchViewModel, navigation: HSNavigation) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val uiState = viewModel.uiState

    Column {
        SearchBar(
            title = stringResource(Res.string.Market_Search),
            searchHintText = stringResource(Res.string.Market_Search),
            searchOnlyMode = true,
            searchModeInitial = true,
            focusRequester = focusRequester,
            onClose = { navigation.navigateUpSafely() },
            onSearchTextChanged = { query -> viewModel.searchByQuery(query) }
        )

        val itemSections = when (uiState.page) {
            is MarketSearchViewModel.Page.Discovery -> {
                mapOf(
                    MarketSearchSection.Recent to uiState.page.recent,
                    MarketSearchSection.Popular to uiState.page.popular,
                )
            }

            is MarketSearchViewModel.Page.SearchResults -> {
                mapOf(
                    MarketSearchSection.SearchResults to uiState.page.items
                )
            }
        }

        MarketSearchResults(
            uiState.listId,
            itemSections = itemSections,
            onCoinClick = { coin, section ->
                viewModel.onCoinOpened(coin)
                navigation.slideFromRight(CoinPage(CoinFragmentInput(coin.uid)))
            }
        ) { favorited, coinUid ->
            viewModel.onFavoriteClick(favorited, coinUid)
        }
    }
}

enum class MarketSearchSection(val title: StringResource?) {
    Recent(Res.string.Market_Search_Sections_RecentTitle),
    Popular(Res.string.Market_Search_Sections_PopularTitle),
    SearchResults(null)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarketSearchResults(
    vararg inputs: Any?,
    itemSections: Map<MarketSearchSection, List<CoinItem>>,
    onCoinClick: (Coin, MarketSearchSection) -> Unit,
    onFavoriteClick: (Boolean, String) -> Unit,
) {
    if (itemSections.all { (_, items) -> items.isEmpty() }) {
        ListEmptyView(
            text = stringResource(Res.string.EmptyResults),
            icon = Res.drawable.ic_not_found
        )
    } else {
        val coroutineScope = rememberCoroutineScope()
        var revealedCardId by remember(*inputs) { mutableStateOf<String?>(null) }

        LazyColumn(
            state = rememberSaveable(
                *inputs,
                saver = LazyListState.Saver
            ) {
                LazyListState()
            }
        ) {
            itemSections.forEach { (section, coinItems) ->
                section.title?.let { title ->
                    stickyHeader {
                        HeaderStick(
                            borderTop = true,
                            text = stringResource(title)
                        )
                    }
                }
                items(coinItems) { item ->
                    val coin = item.fullCoin.coin

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .background(
                                    if (item.favourited) {
                                        ComposeAppTheme.colors.statusError
                                    } else {
                                        ComposeAppTheme.colors.brandDefault
                                    }
                                )
                                .align(Alignment.CenterEnd)
                                .width(100.dp)
                                .clickable {
                                    onFavoriteClick(
                                        item.favourited,
                                        coin.uid
                                    )

                                    coroutineScope.launch {
                                        delay(200)
                                        revealedCardId = null
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (item.favourited) Res.drawable.ic_star_off_24 else Res.drawable.ic_star_24
                                ),
                                tint = ComposeAppTheme.colors.contentInverse,
                                contentDescription = stringResource(
                                    if (item.favourited) {
                                        Res.string.CoinPage_Unfavorite
                                    } else {
                                        Res.string.CoinPage_Favorite
                                    }
                                ),
                            )
                        }
                        val cardId = (section.title?.let { stringResource(it) }.orEmpty()) + coin.uid
                        DraggableCardSimple(
                            key = cardId,
                            isRevealed = revealedCardId == cardId,
                            cardOffset = 100f,
                            onReveal = {
                                if (revealedCardId != cardId) {
                                    revealedCardId = cardId
                                }
                            },
                            onConceal = {
                                revealedCardId = null
                            },
                            content = {
                                Box(modifier = Modifier.background(ComposeAppTheme.colors.backgroundBase)) {
                                    MarketCoin(
                                        coinCode = coin.code,
                                        coinName = coin.name,
                                        coinIconUrl = coin.imageUrl,
                                        alternativeCoinIconUrl = coin.alternativeImageUrl,
                                        coinIconPlaceholder = item.fullCoin.iconPlaceholder,
                                        onClick = { onCoinClick(coin, section) }
                                    )
                                }
                            }
                        )
                    }
                }
            }

            item {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = ComposeAppTheme.colors.borderDivider,
                )
            }
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
internal fun MarketCoin(
    coinCode: String,
    coinName: String,
    coinIconUrl: String,
    alternativeCoinIconUrl: String?,
    coinIconPlaceholder: DrawableResource,
    onClick: () -> Unit,
) {

    SectionItemBorderedRowUniversalClear(
        borderTop = true,
        onClick = onClick
    ) {
        HsImage(
            url = coinIconUrl,
            alternativeUrl = alternativeCoinIconUrl,
            placeholder = coinIconPlaceholder,
            modifier = Modifier
                .padding(end = 16.dp)
                .size(32.dp)
                .clip(CircleShape)
        )
        Column(
            modifier = Modifier.weight(1f)
        ) {
            body_leah(
                text = coinCode,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            subhead2_grey(
                text = coinName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
