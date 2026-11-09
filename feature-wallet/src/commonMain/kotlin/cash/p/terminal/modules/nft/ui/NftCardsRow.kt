package cash.p.terminal.modules.nft.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import cash.p.terminal.resources.nft_show_all
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource

private const val MAX_CARDS = 10
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
        LazyRow(
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
                item(key = "show_all") {
                    ShowAllCell(width = cardWidth, onClick = onShowAll)
                }
            }
        }
    }
}

@Composable
private fun ShowAllCell(width: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(width)
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
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
