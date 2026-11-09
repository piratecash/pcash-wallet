package cash.p.terminal.modules.nft.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.modules.nft.NftSectionTitle
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.nft_collections
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun NftCollectionsSection(
    title: @Composable () -> Unit,
    items: List<NftItemRecord>,
    hidden: Boolean,
    onItemClick: (NftItemRecord) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    Column(modifier = modifier) {
        title()
        NftCardsRow(
            items = items,
            hidden = hidden,
            onItemClick = onItemClick,
            onShowAll = onShowAll
        )
    }
}

@Composable
private fun SectionPreview(items: List<NftItemRecord>, hidden: Boolean = false) {
    ComposeAppTheme {
        NftCollectionsSection(
            title = {
                NftSectionTitle(
                    title = stringResource(Res.string.nft_collections),
                    hidden = hidden,
                    onClick = {},
                    trailingCount = items.size,
                )
            },
            items = items,
            hidden = hidden,
            onItemClick = {},
            onShowAll = {}
        )
    }
}

@Preview
@Composable
private fun NftCollectionsSectionPreview() = SectionPreview(previewNftItems(5))

@Preview
@Composable
private fun NftCollectionsSectionHiddenPreview() = SectionPreview(previewNftItems(5), hidden = true)

@Preview
@Composable
private fun NftCollectionsSectionOverLimitPreview() = SectionPreview(previewNftItems(12))
