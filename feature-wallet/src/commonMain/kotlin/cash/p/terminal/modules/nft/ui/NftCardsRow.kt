package cash.p.terminal.modules.nft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.resources.nft_show_all
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val MAX_CARDS = 10
private const val SHOW_ALL_KEY = "show_all"
private const val VISIBLE_CARDS = 3
private val ContentPadding = 16.dp
private val CardSpacing = 8.dp
private val MaxCardWidth = 160.dp

@Composable
fun NftCardsRow(
    items: List<NftItemRecord>,
    hidden: Boolean,
    onItemClick: (NftItemRecord) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Leaves a peek of the 4th card, which hints that the row scrolls.
        val cardWidth = minOf(
            (maxWidth - ContentPadding * 3) / VISIBLE_CARDS,
            MaxCardWidth
        )
        val listState = rememberLazyListState()
        SnapShowAllCell(listState)
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = ContentPadding),
            horizontalArrangement = Arrangement.spacedBy(CardSpacing)
        ) {
            items(items.take(MAX_CARDS), key = { it.address }) { item ->
                NftCard(
                    item = item,
                    hidden = hidden,
                    onClick = { onItemClick(item) },
                    modifier = Modifier.width(cardWidth)
                )
            }
            if (items.size > MAX_CARDS) {
                item(key = SHOW_ALL_KEY) {
                    ShowAllCell(width = cardWidth, onClick = onShowAll)
                }
            }
        }
    }
}

/** Once a scroll stops with the cell half shown, it is revealed completely or pushed out of the viewport. */
@Composable
private fun SnapShowAllCell(listState: LazyListState) {
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { !it }.collect {
            val layout = listState.layoutInfo
            val cell = layout.visibleItemsInfo.firstOrNull { it.key == SHOW_ALL_KEY } ?: return@collect
            val offset = showAllSnapOffset(cell.offset, cell.size, layout.viewportEndOffset, layout.afterContentPadding)
            // A drag cancels the snap; in a child coroutine that cancellation cannot end the collector.
            if (offset != 0f) launch { listState.animateScrollBy(offset) }
        }
    }
}

/** Scroll distance to snap the cell: positive reveals it, negative pushes it out, 0 leaves it. */
internal fun showAllSnapOffset(itemOffset: Int, itemSize: Int, viewportEnd: Int, endPadding: Int): Float {
    val itemEnd = itemOffset + itemSize
    val revealedEnd = viewportEnd - endPadding
    val visible = (minOf(itemEnd, viewportEnd) - itemOffset).coerceIn(0, itemSize)
    return when {
        visible == 0 || itemEnd <= revealedEnd -> 0f
        visible * 2 >= itemSize -> (itemEnd - revealedEnd).toFloat()
        else -> (itemOffset - viewportEnd).toFloat()
    }
}

@Composable
internal fun ShowAllCell(width: Dp, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(width)
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(ComposeAppTheme.colors.surfaceElevated, CircleShape)
                .border(1.dp, ComposeAppTheme.colors.borderDefault, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_right),
                contentDescription = null,
                tint = ComposeAppTheme.colors.iconPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = stringResource(Res.string.nft_show_all),
            style = ComposeAppTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
            color = ComposeAppTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

internal fun previewNftItems(count: Int) = List(count) { index ->
    previewNftItem(
        address = "$index",
        trust = if (index % 4 == 1) NftTrust.None else NftTrust.Whitelist
    )
}

@Preview
@Composable
private fun NftCardsRowPreview() {
    ComposeAppTheme {
        NftCardsRow(items = previewNftItems(5), hidden = false, onItemClick = {}, onShowAll = {})
    }
}

@Preview
@Composable
private fun NftCardsRowHiddenPreview() {
    ComposeAppTheme {
        NftCardsRow(items = previewNftItems(5), hidden = true, onItemClick = {}, onShowAll = {})
    }
}

@Preview
@Composable
private fun NftCardsRowOverLimitPreview() {
    ComposeAppTheme {
        NftCardsRow(items = previewNftItems(12), hidden = false, onItemClick = {}, onShowAll = {})
    }
}

@Preview
@Composable
private fun ShowAllCellPreview() {
    ComposeAppTheme {
        ShowAllCell(width = 110.dp, onClick = {})
    }
}
