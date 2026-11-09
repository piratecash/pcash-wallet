package cash.p.terminal.modules.nft

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.modules.manageaccount.ui.rememberCopyWithHud
import cash.p.terminal.modules.nft.ui.HIDDEN_COLLECTION
import cash.p.terminal.modules.nft.ui.NftArtwork
import cash.p.terminal.modules.nft.ui.displayName
import cash.p.terminal.modules.nft.ui.previewNftItem
import cash.p.terminal.resources.AddToken_AddressOrSymbol
import cash.p.terminal.resources.Balance_Network
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_nft_verified_16
import cash.p.terminal.resources.nft_about_collection
import cash.p.terminal.resources.nft_details
import cash.p.terminal.resources.nft_open_in_explorer
import cash.p.terminal.resources.nft_owner
import cash.p.terminal.resources.nft_unnamed_collection
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsDivider
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import cash.p.terminal.ui_compose.components.SectionUniversalItem
import cash.p.terminal.ui_compose.components.TitleAndValueCell
import cash.p.terminal.ui_compose.components.headline1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.title3_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val MaxContentWidth = 560.dp
private val SectionShape = RoundedCornerShape(12.dp)
private const val ADDRESS_EDGE_LENGTH = 4

@Composable
internal fun NftPreviewScreen(
    item: NftItemRecord,
    uiState: NftPreviewUiState,
    networkTitle: String?,
    explorerUrl: String?,
    onArtworkClick: () -> Unit,
    onBack: () -> Unit,
) {
    val name = item.displayName(uiState.hidden)
    Scaffold(
        topBar = { AppBar(title = name, navigationIcon = { HsBackButton(onClick = onBack) }) },
        containerColor = ComposeAppTheme.colors.backgroundBase,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.nftSection().background(ComposeAppTheme.colors.surfacePrimary)) {
                    NftImage(item, uiState, onArtworkClick)
                    Info(item, name, uiState.hidden)
                    if (!uiState.hidden) {
                        item.collectionDescription?.takeIf { it.isNotBlank() }?.let { AboutCollection(it) }
                    }
                }
                if (!uiState.hidden) {
                    DetailsHeader(explorerUrl)
                    Details(item, networkTitle)
                }
            }
        }
    }
}

@Composable
private fun Modifier.nftSection() = padding(horizontal = 16.dp)
    .fillMaxWidth()
    .clip(SectionShape)
    .border(1.dp, ComposeAppTheme.colors.borderDefault, SectionShape)

@Composable
private fun NftImage(item: NftItemRecord, uiState: NftPreviewUiState, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(ComposeAppTheme.colors.surfacePlaceholder)
            .clickable(enabled = uiState.canReveal, onClick = onClick)
    ) {
        NftArtwork(
            url = item.largeImageUrl ?: item.imageUrl,
            hidden = uiState.hidden,
            alternativeUrl = item.imageUrl,
        )
    }
}

@Composable
private fun Info(item: NftItemRecord, name: String, hidden: Boolean) {
    val collection = if (hidden) HIDDEN_COLLECTION else item.collectionName.takeUnless { it.isNullOrBlank() }
        ?: stringResource(Res.string.nft_unnamed_collection)

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title3_leah(text = name)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            subhead2_grey(text = collection, modifier = Modifier.weight(1f, fill = false))
            if (item.verified && !hidden) {
                Image(
                    painter = painterResource(Res.drawable.ic_nft_verified_16),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (!hidden) {
            item.description?.takeIf { it.isNotBlank() }?.let { subhead2_grey(text = it) }
        }
    }
}

@Composable
private fun AboutCollection(description: String) {
    HsDivider(thickness = 1.dp)
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(Res.string.nft_about_collection),
            style = ComposeAppTheme.typography.subhead1.copy(lineHeight = 20.sp),
            color = ComposeAppTheme.colors.textPrimary,
        )
        subhead2_grey(text = description)
    }
}

@Composable
private fun DetailsHeader(explorerUrl: String?) {
    val urlOpener = LocalUrlOpener.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 4.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        headline1_leah(text = stringResource(Res.string.nft_details))
        explorerUrl?.let { url ->
            Text(
                text = stringResource(Res.string.nft_open_in_explorer),
                modifier = Modifier
                    .clickable { urlOpener.open(url) }
                    .padding(vertical = 4.dp),
                style = ComposeAppTheme.typography.subhead1,
                color = ComposeAppTheme.colors.brandDefault,
            )
        }
    }
}

@Composable
private fun Details(item: NftItemRecord, networkTitle: String?) {
    val ownerTitle = stringResource(Res.string.nft_owner)
    val contractTitle = stringResource(Res.string.AddToken_AddressOrSymbol)
    val networkLabel = stringResource(Res.string.Balance_Network)

    val rows = buildList<@Composable () -> Unit> {
        item.realOwnerAddress?.let { add { AddressRow(ownerTitle, it) } }
        add { AddressRow(contractTitle, item.address) }
        networkTitle?.let { add { TitleAndValueCell(title = networkLabel, value = it) } }
    }

    Column(Modifier.nftSection()) {
        rows.forEachIndexed { index, row -> SectionUniversalItem(borderTop = index != 0, content = row) }
    }
}

@Composable
private fun AddressRow(title: String, address: String) {
    val copyWithHud = rememberCopyWithHud()
    val shortAddress = "${address.take(ADDRESS_EDGE_LENGTH)}…${address.takeLast(ADDRESS_EDGE_LENGTH)}"
    TitleAndValueCell(title, shortAddress, Modifier.clickable { copyWithHud(address) })
}

private val previewItem = previewNftItem("EQBx").copy(
    description = "A rare cat that lives on the TON blockchain.",
    realOwnerAddress = "UQCOwnerAddress0000000000000000000000000000000000",
    collectionDescription = "10,000 unique cats with different traits.",
    verified = true,
)

@Composable
private fun PreviewContent(hidden: Boolean) {
    ComposeAppTheme {
        NftPreviewScreen(
            item = previewItem,
            uiState = NftPreviewUiState(hidden = hidden, canReveal = hidden),
            networkTitle = "TON",
            explorerUrl = "https://tonviewer.com/nft/EQBx",
            onArtworkClick = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun NftPreviewScreenPreview() = PreviewContent(hidden = false)

@Preview
@Composable
private fun NftPreviewScreenHiddenPreview() = PreviewContent(hidden = true)
