package cash.p.terminal.ui.compose.components

import cash.p.terminal.ui_compose.components.plateOutline
import cash.p.terminal.ui_compose.components.plateBackground
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import cash.p.terminal.wallet.alternativeImageUrl
import cash.p.terminal.core.iconPlaceholder
import cash.p.terminal.core.imageUrl
import cash.p.terminal.wallet.imageUrl
import cash.p.terminal.ui_compose.components.ImageSource
import cash.p.terminal.modules.market.MarketViewItem
import cash.p.terminal.modules.market.search.MarketSearchModule.DiscoveryItem
import cash.p.terminal.ui_compose.components.DraggableCardSimple
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.ButtonSecondaryTransparent
import cash.p.terminal.ui_compose.components.caption_grey
import cash.p.terminal.ui_compose.components.diffColor
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.resources.Button_Retry
import cash.p.terminal.resources.CoinPage_Favorite
import cash.p.terminal.resources.CoinPage_Unfavorite
import cash.p.terminal.resources.Market_Category_TopCoins
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.resources.ic_down_arrow_20
import cash.p.terminal.resources.ic_star_24
import cash.p.terminal.resources.ic_star_off_24
import cash.p.terminal.resources.ic_sync_error
import cash.p.terminal.resources.ic_top_coins
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun CoinList(
    listState: LazyListState = rememberLazyListState(),
    items: List<MarketViewItem>,
    scrollToTop: Boolean,
    onAddFavorite: (String) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onCoinClick: (String) -> Unit,
    userScrollEnabled: Boolean = true,
    preItems: LazyListScope.() -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var revealedCardId by remember { mutableStateOf<String?>(null) }

    LazyColumn(state = listState, userScrollEnabled = userScrollEnabled) {
        preItems.invoke(this)
        itemsIndexed(items, key = { _, item -> item.coinUid }) { _, item ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(
                            if (item.favorited) {
                                ComposeAppTheme.colors.statusError
                            } else {
                                ComposeAppTheme.colors.brandDefault
                            }
                        )
                        .align(Alignment.CenterEnd)
                        .width(100.dp)
                        .clickable {
                            if (item.favorited) {
                                onRemoveFavorite(item.coinUid)
                            } else {
                                onAddFavorite(item.coinUid)
                            }
                            coroutineScope.launch {
                                delay(200)
                                revealedCardId = null
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            if (item.favorited) Res.drawable.ic_star_off_24 else Res.drawable.ic_star_24
                        ),
                        tint = ComposeAppTheme.colors.contentInverse,
                        contentDescription = stringResource(
                            if (item.favorited) Res.string.CoinPage_Unfavorite else Res.string.CoinPage_Favorite
                        ),
                    )
                }
                DraggableCardSimple(
                    key = item.coinUid,
                    isRevealed = revealedCardId == item.coinUid,
                    cardOffset = 100f,
                    onReveal = {
                        if (revealedCardId != item.coinUid) {
                            revealedCardId = item.coinUid
                        }
                    },
                    onConceal = {
                        revealedCardId = null
                    },
                    content = {
                        MarketCoin(
                            title = item.fullCoin.coin.code,
                            subtitle = item.subtitle,
                            coinIconUrl = item.fullCoin.coin.imageUrl,
                            alternativeCoinIconUrl = item.fullCoin.coin.alternativeImageUrl,
                            coinIconPlaceholder = item.fullCoin.iconPlaceholder,
                            value = item.value,
                            marketDataValue = item.marketDataValue,
                            label = item.rank,
                            advice = item.signal,
                            onClick = { onCoinClick.invoke(item.fullCoin.coin.uid) }
                        )
                    }
                )
                HorizontalDivider(
                    thickness = 1.dp,
                    color = ComposeAppTheme.colors.borderDivider,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
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
fun ListErrorView(
    errorText: String,
    onClick: () -> Unit
) {
    ListErrorView(
        errorText = errorText,
        icon = Res.drawable.ic_sync_error,
        onClick = onClick,
    )
}

@Composable
fun ListErrorView(
    errorText: String,
    icon: DrawableResource,
    onClick: () -> Unit
) {
    ScreenMessageWithAction(
        text = errorText,
        icon = icon,
    ) {
        ButtonPrimaryYellow(
            modifier = Modifier
                .padding(horizontal = 48.dp)
                .fillMaxWidth(),
            title = stringResource(Res.string.Button_Retry),
            onClick = onClick
        )
    }
}

@Composable
fun SortMenu(title: TranslatableString, onClick: () -> Unit) {
    ButtonSecondaryTransparent(
        title = title.getString(),
        iconRight = Res.drawable.ic_down_arrow_20,
        onClick = onClick
    )
}

@Composable
fun SortMenu(titleRes: StringResource, onClick: () -> Unit) {
    SortMenu(TranslatableString.ResString(titleRes), onClick)
}

@Composable
fun TopCloseButton(
    onCloseButtonClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier.clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onCloseButtonClick.invoke()
            }
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_close_24),
                contentDescription = "close icon",
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .size(24.dp),
                tint = ComposeAppTheme.colors.brandDefault
            )
        }
    }
}

@Composable
fun DescriptionCard(title: String, description: String, image: ImageSource) {
    Column {
        Row(
            modifier = Modifier
                .height(108.dp)
                .background(ComposeAppTheme.colors.backgroundBase)
        ) {
            Column(
                modifier = Modifier
                    .padding(start = 16.dp, top = 12.dp, end = 8.dp)
                    .weight(1f)
            ) {
                Text(
                    text = title,
                    style = ComposeAppTheme.typography.headline1,
                    color = ComposeAppTheme.colors.textPrimary,
                )
                subhead2_grey(
                    text = description,
                    modifier = Modifier.padding(top = 6.dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Image(
                painter = image.painter(),
                contentDescription = "category image",
                modifier = Modifier
                    .fillMaxHeight()
                    .width(76.dp),
            )
        }
    }
}

@Composable
fun CategoryCard(
    type: DiscoveryItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.height(128.dp).plateOutline(RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = plateBackground()),
        onClick = onClick
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (type) {
                is DiscoveryItem.TopCoins -> {
                    Image(
                        painter = painterResource(Res.drawable.ic_top_coins),
                        contentDescription = "category image",
                        modifier = Modifier
                            .height(108.dp)
                            .width(76.dp)
                            .align(Alignment.TopEnd),
                    )
                    Column(
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        subhead1_leah(
                            text = stringResource(Res.string.Market_Category_TopCoins),
                            maxLines = 1
                        )
                    }
                }

                is DiscoveryItem.Category -> {
                    Crossfade(
                        targetState = type.coinCategory.imageUrl,
                        animationSpec = tween(500),
                        modifier = Modifier
                            .height(108.dp)
                            .width(76.dp)
                            .align(Alignment.TopEnd)
                    ) { imageRes ->
                        Image(
                            painter = rememberAsyncImagePainter(imageRes),
                            contentDescription = "category image",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Column(
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        subhead1_leah(
                            text = type.coinCategory.name,
                            maxLines = 1
                        )
                        AnimatedVisibility(
                            visible = type.marketData != null,
                        ) {
                            type.marketData?.let { marketData ->
                                Row(modifier = Modifier.padding(top = 8.dp)) {
                                    caption_grey(
                                        text = marketData.marketCap ?: "",
                                        maxLines = 1
                                    )
                                    AnimatedVisibility(
                                        visible = marketData.diff != null,
                                        enter = fadeIn() + expandHorizontally(),
                                        exit = fadeOut() + shrinkHorizontally()
                                    ) {
                                        marketData.diff?.let { diff ->
                                            Text(
                                                text = diffText(diff),
                                                color = diffColor(diff),
                                                style = ComposeAppTheme.typography.caption,
                                                maxLines = 1,
                                                modifier = Modifier.padding(start = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
