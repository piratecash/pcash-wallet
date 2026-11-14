package cash.p.terminal.modules.nft.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    allCollectionsHidden: Boolean,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty() && !allCollectionsHidden) return

    Column(modifier = modifier) {
        title()
        if (allCollectionsHidden) {
            NftAllHiddenCell(onManage = onManage, modifier = Modifier.padding(horizontal = 16.dp))
        } else {
            NftCardsRow(
                items = items,
                hidden = hidden,
                onItemClick = onItemClick,
                onShowAll = onShowAll
            )
        }
    }
}

@Composable
private fun SectionPreview(
    items: List<NftItemRecord>,
    hidden: Boolean = false,
    allCollectionsHidden: Boolean = false,
) {
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
            onShowAll = {},
            allCollectionsHidden = allCollectionsHidden,
            onManage = {},
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

@Preview
@Composable
private fun NftCollectionsSectionAllHiddenPreview() = SectionPreview(emptyList(), allCollectionsHidden = true)
