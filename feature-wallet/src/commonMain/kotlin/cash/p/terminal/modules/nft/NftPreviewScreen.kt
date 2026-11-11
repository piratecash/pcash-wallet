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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.resources.Contacts_ActionMore
import cash.p.terminal.resources.ic_arrow_up_right_24
import cash.p.terminal.resources.ic_info_20
import cash.p.terminal.resources.ic_more_24
import cash.p.terminal.resources.nft_not_spam
import cash.p.terminal.resources.nft_report_spam
import cash.p.terminal.resources.nft_unverified
import cash.p.terminal.resources.nft_unverified_nft
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.ButtonPrimaryRed
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.core.nft.NftDomainExpiry
import cash.p.terminal.core.nft.NftLinkButton
import cash.p.terminal.core.nft.owner
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.modules.manageaccount.ui.rememberCopyWithHud
import cash.p.terminal.modules.nft.ui.HIDDEN_COLLECTION
import cash.p.terminal.modules.nft.ui.NftArtwork
import cash.p.terminal.modules.nft.ui.displayName
import cash.p.terminal.modules.nft.renew.DomainExpiryRow
import cash.p.terminal.modules.nft.renew.rememberDaysLeft
import cash.p.terminal.modules.nft.ui.previewNftItem
import cash.p.terminal.resources.AddToken_AddressOrSymbol
import cash.p.terminal.resources.Balance_Network
import cash.p.terminal.resources.Button_Send
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_nft_verified_16
import cash.p.terminal.resources.nft_about_collection
import cash.p.terminal.resources.nft_details
import cash.p.terminal.resources.nft_domain_expires
import cash.p.terminal.resources.nft_domain_renew
import cash.p.terminal.resources.nft_open_in_explorer
import cash.p.terminal.resources.nft_owner
import cash.p.terminal.resources.nft_transfer_check_failed
import cash.p.terminal.resources.nft_transfer_not_owner
import cash.p.terminal.resources.nft_transfer_on_sale
import cash.p.terminal.resources.nft_transfer_watch_only
import cash.p.terminal.resources.nft_unnamed_collection
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsDivider
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import cash.p.terminal.ui_compose.components.SectionUniversalItem
import cash.p.terminal.ui_compose.components.TitleAndValueCell
import cash.p.terminal.ui_compose.components.headline1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.title3_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.WalletFactory
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val MaxContentWidth = 560.dp
private val SectionShape = RoundedCornerShape(12.dp)
private const val ADDRESS_EDGE_LENGTH = 4

internal class NftNetworkInfo(val title: String?, val explorerUrl: String?)

internal class NftPreviewActions(
    val onArtworkClick: () -> Unit,
    val onSend: (NftTransferAvailability.Available) -> Unit,
    val onRenew: (NftDomainExpiry) -> Unit,
    val onMenu: () -> Unit,
    val onReportSpam: () -> Unit,
    val onNotSpam: () -> Unit,
    val onLinkClick: (NftLinkButton) -> Unit,
    val onBack: () -> Unit,
)

@Composable
internal fun NftPreviewScreen(
    item: NftItemRecord,
    uiState: NftPreviewUiState,
    network: NftNetworkInfo,
    actions: NftPreviewActions,
) {
    val name = item.displayName(uiState.hidden)
    Scaffold(
        topBar = { PreviewAppBar(name, uiState.showSpamPlate, actions.onMenu, actions.onBack) },
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
                if (uiState.showSpamPlate) SpamPlate(actions.onReportSpam, actions.onNotSpam)
                Column(Modifier.nftSection().background(ComposeAppTheme.colors.surfacePrimary)) {
                    NftImage(item, uiState, actions.onArtworkClick)
                    Info(item, name, uiState.hidden, uiState.showSpamPlate)
                    if (!uiState.hidden) {
                        item.collectionDescription?.takeIf { it.isNotBlank() }?.let { AboutCollection(it) }
                    }
                }
                if (!uiState.hidden) {
                    DetailsHeader(network.explorerUrl)
                    Details(item, network.title, uiState.domainExpiry)
                    TransferActions(uiState, actions)
                }
            }
        }
    }
}

@Composable
private fun PreviewAppBar(name: String, unverified: Boolean, onMenu: () -> Unit, onBack: () -> Unit) {
    AppBar(
        title = {
            Column {
                title3_leah(text = name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (unverified) UnverifiedCaption()
            }
        },
        navigationIcon = { HsBackButton(onClick = onBack) },
        menuItems = listOf(
            MenuItem(
                title = TranslatableString.PlainString(stringResource(Res.string.Contacts_ActionMore)),
                icon = Res.drawable.ic_more_24,
                tint = ComposeAppTheme.colors.brandDefault,
                onClick = onMenu,
            )
        ),
    )
}

@Composable
private fun UnverifiedCaption() {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(Res.string.nft_unverified_nft),
            style = ComposeAppTheme.typography.caption.copy(lineHeight = 16.sp),
            color = ComposeAppTheme.colors.statusWarning,
        )
        Image(
            painter = painterResource(Res.drawable.ic_info_20),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            colorFilter = ColorFilter.tint(ComposeAppTheme.colors.statusWarning),
        )
    }
}

@Composable
private fun SpamPlate(onReportSpam: () -> Unit, onNotSpam: () -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ButtonPrimaryRed(
            modifier = Modifier.weight(1f),
            title = stringResource(Res.string.nft_report_spam),
            onClick = onReportSpam,
        )
        ButtonPrimaryTransparent(
            modifier = Modifier.weight(1f),
            title = stringResource(Res.string.nft_not_spam),
            onClick = onNotSpam,
        )
    }
}

@Composable
private fun Modifier.nftSection() = padding(horizontal = 16.dp).nftCard()

@Composable
internal fun Modifier.nftCard() = fillMaxWidth()
    .clip(SectionShape)
    .border(1.dp, ComposeAppTheme.colors.borderDefault, SectionShape)

internal fun String.shortNftAddress() = "${take(ADDRESS_EDGE_LENGTH)}…${takeLast(ADDRESS_EDGE_LENGTH)}"

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
private fun Info(item: NftItemRecord, name: String, hidden: Boolean, unverified: Boolean) {
    val collection = when {
        hidden -> HIDDEN_COLLECTION
        unverified -> stringResource(Res.string.nft_unverified)
        else -> item.collectionName.takeUnless { it.isNullOrBlank() }
            ?: stringResource(Res.string.nft_unnamed_collection)
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title3_leah(text = name)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (unverified && !hidden) {
                Text(
                    text = collection,
                    modifier = Modifier.weight(1f, fill = false),
                    style = ComposeAppTheme.typography.subhead2,
                    color = ComposeAppTheme.colors.statusWarning,
                )
            } else {
                subhead2_grey(text = collection, modifier = Modifier.weight(1f, fill = false))
            }
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
private fun LinkButtons(uiState: NftPreviewUiState, onClick: (NftLinkButton) -> Unit) {
    val state = uiState.linkButtonsState
    if (state == LinkButtonsState.Hidden) return
    val enabled = state == LinkButtonsState.Enabled
    val icon = painterResource(Res.drawable.ic_arrow_up_right_24)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        uiState.linkButtons.forEachIndexed { index, button ->
            val modifier = Modifier.fillMaxWidth()
            if (index == 0 && !uiState.isSuspicious) {
                ButtonPrimaryYellow(
                    modifier = modifier,
                    title = button.label,
                    onClick = { onClick(button) },
                    enabled = enabled,
                    trailingIcon = icon,
                )
            } else {
                ButtonPrimaryTransparent(
                    title = button.label,
                    onClick = { onClick(button) },
                    modifier = modifier,
                    enabled = enabled,
                    trailingIcon = icon,
                )
            }
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
private fun Details(item: NftItemRecord, networkTitle: String?, domainExpiry: NftDomainExpiry?) {
    val ownerTitle = stringResource(Res.string.nft_owner)
    val contractTitle = stringResource(Res.string.AddToken_AddressOrSymbol)
    val networkLabel = stringResource(Res.string.Balance_Network)

    val expiresTitle = stringResource(Res.string.nft_domain_expires)

    val rows = buildList<@Composable () -> Unit> {
        domainExpiry?.let { add { DomainExpiryRow(expiresTitle, it.rememberDaysLeft()) } }
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
    TitleAndValueCell(title, address.shortNftAddress(), Modifier.clickable { copyWithHud(address) })
}

@Composable
private fun TransferActions(uiState: NftPreviewUiState, actions: NftPreviewActions) {
    val availability = uiState.transferAvailability
    if (availability is NftTransferAvailability.Hidden) return

    Column(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.Button_Send),
                enabled = availability is NftTransferAvailability.Available,
                onClick = { (availability as? NftTransferAvailability.Available)?.let(actions.onSend) },
            )
            LinkButtons(uiState, actions.onLinkClick)
            if (uiState.renewButton != RenewButtonState.Hidden) {
                ButtonPrimaryTransparent(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(Res.string.nft_domain_renew),
                    enabled = uiState.renewButton == RenewButtonState.Enabled,
                    onClick = { uiState.domainExpiry?.let(actions.onRenew) },
                )
            }
        }
        if (availability is NftTransferAvailability.Unavailable) {
            subhead2_grey(text = stringResource(availability.reason.message))
        }
    }
}

private val NftTransferUnavailableReason.message: StringResource
    get() = when (this) {
        NftTransferUnavailableReason.OnSale -> Res.string.nft_transfer_on_sale
        NftTransferUnavailableReason.WatchOnly -> Res.string.nft_transfer_watch_only
        NftTransferUnavailableReason.NotOwner -> Res.string.nft_transfer_not_owner
        NftTransferUnavailableReason.CheckFailed -> Res.string.nft_transfer_check_failed
    }

private val previewItem = previewNftItem("EQBx").copy(
    description = "A rare cat that lives on the TON blockchain.",
    realOwnerAddress = "UQCOwnerAddress0000000000000000000000000000000000",
    collectionDescription = "10,000 unique cats with different traits.",
    verified = true,
)

@Composable
private fun PreviewContent(
    hidden: Boolean,
    availability: NftTransferAvailability = NftTransferAvailability.Loading,
    domainExpiry: NftDomainExpiry? = null,
    trust: NftTrust = NftTrust.Whitelist,
    linkButtons: List<NftLinkButton> = emptyList(),
) {
    ComposeAppTheme {
        NftPreviewScreen(
            item = previewItem,
            uiState = NftPreviewUiState(
                hidden = hidden,
                canReveal = hidden,
                transferAvailability = availability,
                domainExpiry = domainExpiry,
                trust = trust,
                linkButtons = linkButtons,
            ),
            network = NftNetworkInfo(title = "TON", explorerUrl = "https://tonviewer.com/nft/EQBx"),
            actions = NftPreviewActions(
                onArtworkClick = {},
                onSend = {},
                onRenew = {},
                onMenu = {},
                onReportSpam = {},
                onNotSpam = {},
                onLinkClick = {},
                onBack = {},
            ),
        )
    }
}

@Preview
@Composable
private fun NftPreviewScreenPreview() = PreviewContent(hidden = false)

@Preview
@Composable
private fun NftPreviewScreenHiddenPreview() = PreviewContent(hidden = true)

@Preview
@Composable
private fun NftPreviewScreenOnSalePreview() = PreviewContent(
    hidden = false,
    availability = NftTransferAvailability.Unavailable(NftTransferUnavailableReason.OnSale),
)

private fun previewExpiry(daysLeft: Long) = NftDomainExpiry(
    owner = previewNftItem("EQBx").owner,
    nftAddress = "EQBx",
    name = "piratecash.ton",
    expiresAt = System.currentTimeMillis() / 1000 + daysLeft * 86_400 + 3_600,
    isOnSale = false,
)

@Preview
@Composable
private fun NftPreviewScreenRenewNormalPreview() = PreviewContent(
    hidden = false,
    availability = NftTransferAvailability.Loading,
    domainExpiry = previewExpiry(203),
)

@Preview
@Composable
private fun NftPreviewScreenRenewSoonPreview() = PreviewContent(
    hidden = false,
    availability = NftTransferAvailability.Loading,
    domainExpiry = previewExpiry(12),
)

@Preview
@Composable
private fun NftPreviewScreenRenewUrgentOnSalePreview() = PreviewContent(
    hidden = false,
    availability = NftTransferAvailability.Unavailable(NftTransferUnavailableReason.OnSale),
    domainExpiry = previewExpiry(5),
)

@Preview
@Composable
private fun NftPreviewScreenUnverifiedPreview() = PreviewContent(
    hidden = false,
    availability = NftTransferAvailability.Loading,
    trust = NftTrust.None,
)

private val previewLinkButtons = listOf(
    NftLinkButton("Claim reward", "https://example.com/claim"),
    NftLinkButton("Open in Getgems", "https://getgems.io"),
    NftLinkButton("Pay 1 TON", "ton://transfer/EQBx?amount=1000000000"),
)

@Preview
@Composable
private fun NftPreviewScreenLinkButtonsPreview() = PreviewContent(
    hidden = false,
    availability = NftTransferAvailability.Available(WalletFactory.previewWallet(), null),
    linkButtons = previewLinkButtons,
)

@Preview
@Composable
private fun NftPreviewScreenUnverifiedLinkButtonPreview() = PreviewContent(
    hidden = false,
    availability = NftTransferAvailability.Available(WalletFactory.previewWallet(), null),
    trust = NftTrust.None,
    linkButtons = previewLinkButtons.take(1),
)

@Preview
@Composable
private fun NftPreviewScreenLinkButtonsOnSalePreview() = PreviewContent(
    hidden = false,
    availability = NftTransferAvailability.Unavailable(NftTransferUnavailableReason.OnSale),
    linkButtons = previewLinkButtons,
)
