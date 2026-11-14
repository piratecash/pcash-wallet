package cash.p.terminal.modules.nft.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cash.p.terminal.core.nft.NftCollectionState
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.modules.nft.managedTitle
import cash.p.terminal.modules.nft.ui.NftArtwork
import cash.p.terminal.modules.nft.ui.previewNftItem
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.nft_count
import cash.p.terminal.resources.nft_count_single
import cash.p.terminal.resources.nft_manage_collapse
import cash.p.terminal.resources.nft_manage_hidden
import cash.p.terminal.resources.nft_manage_intro
import cash.p.terminal.resources.nft_manage_show_all
import cash.p.terminal.resources.nft_manage_spam
import cash.p.terminal.resources.nft_manage_title
import cash.p.terminal.resources.nft_manage_visible
import cash.p.terminal.resources.nft_not_spam
import cash.p.terminal.resources.nft_spam_by_provider
import cash.p.terminal.resources.nft_spam_by_user
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HSCircularProgressIndicator
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsDivider
import cash.p.terminal.ui_compose.components.HsSwitch
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val CardShape = RoundedCornerShape(12.dp)

@Composable
internal fun NftManageScreen(
    uiState: NftManageUiState,
    onVisibleChange: (row: NftManageRow, visible: Boolean, name: String) -> Unit,
    onNotSpam: (row: NftManageRow, name: String) -> Unit,
    onToggleShowAll: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(Res.string.nft_manage_title),
                navigationIcon = { HsBackButton(onClick = onBack) },
            )
        },
        containerColor = ComposeAppTheme.colors.backgroundBase,
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) { HSCircularProgressIndicator() }
        } else {
            ManageList(uiState, onVisibleChange, onNotSpam, onToggleShowAll, Modifier.padding(paddingValues))
        }
    }
}

@Composable
private fun ManageList(
    uiState: NftManageUiState,
    onVisibleChange: (NftManageRow, Boolean, String) -> Unit,
    onNotSpam: (NftManageRow, String) -> Unit,
    onToggleShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = stringResource(Res.string.nft_manage_intro),
                style = ComposeAppTheme.typography.subhead2,
                color = ComposeAppTheme.colors.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        manageSection(uiState.visibleRows.size, Res.string.nft_manage_visible, uiState.shownVisibleRows) { rows ->
            ManageCard(rows, uiState.hidden, onVisibleChange, onNotSpam) {
                if (uiState.canCollapseVisible) ShowAllRow(uiState, onToggleShowAll)
            }
        }
        manageSection(uiState.hiddenRows.size, Res.string.nft_manage_hidden, uiState.hiddenRows) { rows ->
            ManageCard(rows, uiState.hidden, onVisibleChange, onNotSpam)
        }
        manageSection(uiState.spamRows.size, Res.string.nft_manage_spam, uiState.spamRows) { rows ->
            ManageCard(rows, uiState.hidden, onVisibleChange, onNotSpam)
        }
    }
}

private fun LazyListScope.manageSection(
    total: Int,
    title: StringResource,
    rows: List<NftManageRow>,
    card: @Composable (List<NftManageRow>) -> Unit,
) {
    if (total == 0) return
    item {
        Text(
            text = stringResource(title, total),
            style = ComposeAppTheme.typography.subhead1.copy(fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
            color = ComposeAppTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
    }
    item { card(rows) }
}

@Composable
private fun ManageCard(
    rows: List<NftManageRow>,
    balanceHidden: Boolean,
    onVisibleChange: (NftManageRow, Boolean, String) -> Unit,
    onNotSpam: (NftManageRow, String) -> Unit,
    footer: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .border(1.dp, ComposeAppTheme.colors.borderDefault, CardShape)
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) HsDivider()
            ManageRowItem(row, balanceHidden, onVisibleChange, onNotSpam)
        }
        footer()
    }
}

@Composable
private fun ManageRowItem(
    row: NftManageRow,
    balanceHidden: Boolean,
    onVisibleChange: (NftManageRow, Boolean, String) -> Unit,
    onNotSpam: (NftManageRow, String) -> Unit,
) {
    val name = row.title(balanceHidden)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RowIcon(row, balanceHidden)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name,
                style = ComposeAppTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                color = ComposeAppTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.subtitle(),
                style = ComposeAppTheme.typography.subhead2,
                color = if (row.isSpam) ComposeAppTheme.colors.statusWarning else ComposeAppTheme.colors.textSecondary,
            )
        }
        if (row.isSpam) {
            NotSpamButton(onClick = { onNotSpam(row, name) })
        } else {
            HsSwitch(
                checked = row.state == NftCollectionState.Visible,
                onCheckedChange = { onVisibleChange(row, it, name) },
                modifier = Modifier.size(width = 44.dp, height = 24.dp),
            )
        }
    }
}

/** Spam artwork may itself be the scam, so it is replaced by a warning glyph. */
@Composable
private fun RowIcon(row: NftManageRow, balanceHidden: Boolean) {
    val colors = ComposeAppTheme.colors
    val glyph = when {
        row.isSpam -> "!" to colors.statusWarning
        row.isUnnamed -> "?" to colors.textSecondary
        else -> null
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (glyph == null) colors.surfacePlaceholder else colors.surfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        if (glyph == null) {
            NftArtwork(url = row.item.imageUrl, hidden = balanceHidden)
        } else {
            Text(
                text = glyph.first,
                style = ComposeAppTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                color = glyph.second,
            )
        }
    }
}

@Composable
private fun NotSpamButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .background(ComposeAppTheme.colors.buttonSecondaryFilledBackground)
            .border(1.dp, ComposeAppTheme.colors.borderDefault, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.nft_not_spam),
            style = ComposeAppTheme.typography.subhead2.copy(fontWeight = FontWeight.Medium),
            color = ComposeAppTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun ShowAllRow(uiState: NftManageUiState, onToggleShowAll: () -> Unit) {
    HsDivider()
    Box(
        modifier = Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onToggleShowAll),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (uiState.showAllVisible) {
                stringResource(Res.string.nft_manage_collapse)
            } else {
                stringResource(Res.string.nft_manage_show_all, uiState.visibleRows.size)
            },
            style = ComposeAppTheme.typography.subhead2.copy(fontWeight = FontWeight.Medium),
            color = ComposeAppTheme.colors.brandDefault,
        )
    }
}

@Composable
private fun NftManageRow.title(balanceHidden: Boolean): String = item.managedTitle(balanceHidden)

@Composable
private fun NftManageRow.subtitle(): String = when {
    state == NftCollectionState.SpamByUser -> stringResource(Res.string.nft_spam_by_user)
    state == NftCollectionState.SpamByProvider -> stringResource(Res.string.nft_spam_by_provider)
    isSingle -> stringResource(Res.string.nft_count_single, nftCount)
    else -> stringResource(Res.string.nft_count, nftCount)
}

private fun previewRow(
    index: Int,
    state: NftCollectionState,
    collectionName: String? = "Collection $index",
    nftCount: Int = index + 1,
) = NftManageRow(
    item = previewNftItem("$index", collectionName = collectionName, trust = NftTrust.Whitelist)
        .copy(collectionAddress = collectionName?.let { "collection-$index" }),
    nftCount = nftCount,
    state = state,
)

private fun previewState(
    visible: Int = 2,
    hidden: Int = 1,
    spam: Int = 2,
    balanceHidden: Boolean = false,
    showAllVisible: Boolean = false,
) = NftManageUiState(
    rows = List(visible) { previewRow(it, NftCollectionState.Visible) } +
        List(hidden) { previewRow(10 + it, NftCollectionState.Hidden, collectionName = null, nftCount = 1) } +
        List(spam) {
            previewRow(20 + it, if (it == 0) NftCollectionState.SpamByUser else NftCollectionState.SpamByProvider)
        },
    isLoading = false,
    hidden = balanceHidden,
    showAllVisible = showAllVisible,
)

@Composable
private fun ScreenPreview(uiState: NftManageUiState) {
    ComposeAppTheme {
        NftManageScreen(
            uiState = uiState,
            onVisibleChange = { _, _, _ -> },
            onNotSpam = { _, _ -> },
            onToggleShowAll = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun NftManageScreenPreview() = ScreenPreview(previewState())

@Preview
@Composable
private fun NftManageScreenCollapsedPreview() = ScreenPreview(previewState(visible = 5))

@Preview
@Composable
private fun NftManageScreenExpandedPreview() = ScreenPreview(previewState(visible = 5, showAllVisible = true))

@Preview
@Composable
private fun NftManageScreenBalanceHiddenPreview() = ScreenPreview(previewState(balanceHidden = true))

@Preview
@Composable
private fun NftManageScreenLoadingPreview() = ScreenPreview(NftManageUiState())
