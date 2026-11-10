package cash.p.terminal.modules.nft

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.modules.nft.ui.HIDDEN_COLLECTION
import cash.p.terminal.modules.nft.ui.NftCard
import cash.p.terminal.modules.nft.ui.NftHiddenEntryCell
import cash.p.terminal.modules.nft.ui.previewNftItems
import cash.p.terminal.resources.Balance_Network
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.hide_balance
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.resources.ic_eye_20
import cash.p.terminal.resources.ic_eye_off
import cash.p.terminal.resources.ic_manage_2_24
import cash.p.terminal.resources.icon_24_nft_placeholder
import cash.p.terminal.resources.nft_collections
import cash.p.terminal.resources.nft_empty_title
import cash.p.terminal.resources.nft_hidden_collections_count
import cash.p.terminal.resources.nft_manage
import cash.p.terminal.resources.nft_manage_title
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.ListEmptyView
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HSCircularProgressIndicator
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.headline1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.title3_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val NftCardMinWidth = 100.dp
private val TitlePressBandPadding = 12.dp

@Composable
internal fun NftCollectionScreen(
    uiState: NftCollectionsUiState,
    onEyeClick: () -> Unit,
    onItemClick: (NftItemRecord) -> Unit,
    onManage: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            AppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        title3_leah(text = stringResource(Res.string.nft_collections))
                        NftCountBadge(uiState.totalCount, uiState.hidden, Modifier.padding(start = 8.dp))
                    }
                },
                navigationIcon = { HsBackButton(onClick = onBack) },
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.nft_manage_title),
                        icon = Res.drawable.ic_manage_2_24,
                        tint = ComposeAppTheme.colors.brandDefault,
                        onClick = onManage,
                    )
                ),
            )
        },
        containerColor = ComposeAppTheme.colors.backgroundBase,
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            HideBalanceRow(uiState, onEyeClick)
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    HSCircularProgressIndicator()
                }

                uiState.isEmpty -> ListEmptyView(
                    text = stringResource(Res.string.nft_empty_title),
                    icon = Res.drawable.icon_24_nft_placeholder,
                )

                else -> NftGrid(uiState, onItemClick, onManage)
            }
        }
    }
}

@Composable
private fun HideBalanceRow(uiState: NftCollectionsUiState, onEyeClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onEyeClick
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            subhead2_grey(text = stringResource(Res.string.hide_balance))
            HSpacer(8.dp)
            Icon(
                painter = painterResource(if (uiState.hidden) Res.drawable.ic_eye_off else Res.drawable.ic_eye_20),
                contentDescription = null,
                tint = ComposeAppTheme.colors.iconSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        uiState.networkTitle?.let {
            Box(Modifier.weight(1f), Alignment.CenterEnd) {
                subhead2_grey(text = "${stringResource(Res.string.Balance_Network)}: $it")
            }
        }
    }
}

@Composable
private fun NftGrid(
    uiState: NftCollectionsUiState,
    onItemClick: (NftItemRecord) -> Unit,
    onManage: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(NftCardMinWidth),
        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(uiState.items, key = { it.address }) { item ->
            NftCard(item = item, hidden = uiState.hidden, onClick = { onItemClick(item) })
        }
        if (uiState.hiddenCollectionsCount > 0) {
            item(key = "hidden_collections", span = { GridItemSpan(maxLineSpan) }) {
                HiddenCollectionsRow(uiState.hiddenCollectionsCount, uiState.hidden, onManage)
            }
        }
    }
}

@Composable
private fun HiddenCollectionsRow(count: Int, hidden: Boolean, onManage: () -> Unit) {
    NftHiddenEntryCell(
        label = stringResource(
            Res.string.nft_hidden_collections_count,
            if (hidden) HIDDEN_COLLECTION else count.toString(),
        ),
        actionTitle = stringResource(Res.string.nft_manage),
        onClick = onManage,
        // The grid already spaces items by 8; the design puts this entry 12 below the cards.
        modifier = Modifier.padding(top = 4.dp).height(48.dp),
        showChevron = true,
    )
}

/** Omitted while hidden, so the counter cannot reveal how many NFTs there are. */
@Composable
internal fun NftCountBadge(count: Int, hidden: Boolean, modifier: Modifier = Modifier) {
    if (hidden) return
    Text(
        text = count.toString(),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ComposeAppTheme.colors.badgeBackground)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        style = ComposeAppTheme.typography.captionSB,
        color = ComposeAppTheme.colors.textSecondary,
    )
}

@Composable
internal fun NftSectionTitle(
    title: String,
    hidden: Boolean,
    onClick: () -> Unit,
    gapAbove: Dp = 0.dp,
    badgeCount: Int? = null,
    trailingCount: Int? = null,
) {
    Row(
        modifier = Modifier
            .centerPressBandOnTitle(gapAbove)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = TitlePressBandPadding),
        horizontalArrangement = Arrangement.spacedBy(if (badgeCount != null) 6.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        headline1_leah(text = title)
        badgeCount?.let { NftCountBadge(it, hidden) }
        Icon(
            painter = painterResource(Res.drawable.ic_arrow_right),
            contentDescription = null,
            tint = ComposeAppTheme.colors.brandDefault,
            modifier = Modifier.size(16.dp),
        )
        if (trailingCount != null && !hidden) {
            Spacer(Modifier.weight(1f))
            subhead2_grey(text = trailingCount.toString())
        }
    }
}

/** The band may reach into [gapAbove] or past it, the title itself stays [gapAbove] below the previous block. */
private fun Modifier.centerPressBandOnTitle(gapAbove: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = (gapAbove - TitlePressBandPadding).roundToPx()
    layout(placeable.width, placeable.height + shift) { placeable.place(0, shift) }
}

@Composable
private fun ScreenPreview(uiState: NftCollectionsUiState) {
    ComposeAppTheme {
        NftCollectionScreen(uiState = uiState, onEyeClick = {}, onItemClick = {}, onManage = {}, onBack = {})
    }
}

private fun previewState(
    count: Int = 12,
    hidden: Boolean = false,
    isLoading: Boolean = false,
    hiddenCollectionsCount: Int = 0,
    hasNfts: Boolean = count > 0,
) = NftCollectionsUiState(
    items = previewNftItems(count),
    isLoading = isLoading,
    hidden = hidden,
    networkTitle = "TON",
    hiddenCollectionsCount = hiddenCollectionsCount,
    hasNfts = hasNfts,
)

@Preview
@Composable
private fun NftCollectionScreenPreview() = ScreenPreview(previewState())

@Preview
@Composable
private fun NftCollectionScreenHiddenPreview() = ScreenPreview(previewState(hidden = true))

@Preview
@Composable
private fun NftCollectionScreenLoadingPreview() = ScreenPreview(previewState(count = 0, isLoading = true))

@Preview
@Composable
private fun NftCollectionScreenEmptyPreview() = ScreenPreview(previewState(count = 0))

@Preview
@Composable
private fun NftCollectionScreenWithHiddenPreview() = ScreenPreview(previewState(hiddenCollectionsCount = 3))

@Preview
@Composable
private fun NftCollectionScreenWithHiddenMaskedPreview() =
    ScreenPreview(previewState(hidden = true, hiddenCollectionsCount = 3))

@Preview
@Composable
private fun NftCollectionScreenAllHiddenPreview() =
    ScreenPreview(previewState(count = 0, hiddenCollectionsCount = 3, hasNfts = true))
