package cash.p.terminal.modules.nft.renew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.nft.NftDomainExpiry
import cash.p.terminal.core.nft.NftOwner
import cash.p.terminal.core.nft.domainExpiryLevel
import cash.p.terminal.modules.nft.nftCard
import cash.p.terminal.modules.nft.ui.HIDDEN_NAME
import cash.p.terminal.modules.nft.ui.NftArtwork
import cash.p.terminal.resources.FeeSettings_NetworkFee
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.nft_domain_expires_row
import cash.p.terminal.resources.nft_domain_on_sale_badge
import cash.p.terminal.resources.nft_domain_on_sale_hint
import cash.p.terminal.resources.nft_domain_renew_info
import cash.p.terminal.resources.nft_domain_renew_page_title
import cash.p.terminal.resources.nft_domain_renew_select
import cash.p.terminal.resources.nft_domain_renew_selected
import cash.p.terminal.resources.nft_domain_renew_total
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.BadgeText
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsCheckbox
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SectionUniversalItem
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.BlockchainType
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NftDomainRenewScreen(
    state: NftDomainRenewalState,
    totals: NftDomainRenewUiState?,
    renewUntil: String,
    onToggle: (NftDomainExpiry) -> Unit,
    onRenew: () -> Unit,
    onBack: () -> Unit,
) {
    val selectedCount = state.selected.size
    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(Res.string.nft_domain_renew_page_title),
                navigationIcon = { HsBackButton(onClick = onBack) },
            )
        },
        containerColor = ComposeAppTheme.colors.backgroundBase,
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                subhead2_grey(text = stringResource(Res.string.nft_domain_renew_info))
                Column(Modifier.nftCard()) {
                    state.domains.forEachIndexed { index, domain ->
                        SectionUniversalItem(borderTop = index != 0) {
                            DomainRow(
                                domain = domain,
                                hidden = state.hidden,
                                checked = domain.nftAddress !in state.deselected,
                                onToggle = { onToggle(domain) },
                            )
                        }
                    }
                }
                totals?.let { Totals(selectedCount, it) }
            }
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp),
                title = if (selectedCount > 0) {
                    pluralStringResource(
                        Res.plurals.nft_domain_renew_selected,
                        selectedCount,
                        selectedCount,
                        renewUntil,
                    )
                } else {
                    stringResource(Res.string.nft_domain_renew_select)
                },
                enabled = selectedCount > 0,
                onClick = onRenew,
            )
        }
    }
}

@Composable
private fun DomainRow(domain: NftDomainExpiry, hidden: Boolean, checked: Boolean, onToggle: () -> Unit) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalPadding = 10.dp,
        minHeight = 64.dp,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ComposeAppTheme.colors.surfacePlaceholder),
        ) {
            NftArtwork(url = domain.imageUrl, hidden = hidden)
        }
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            body_leah(text = if (hidden) HIDDEN_NAME else domain.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ExpiryLine(domain)
        }
        if (domain.isOnSale) {
            BadgeText(
                text = stringResource(Res.string.nft_domain_on_sale_badge),
                background = ComposeAppTheme.colors.statusWarning20,
                textColor = ComposeAppTheme.colors.statusWarning,
            )
        } else {
            HsCheckbox(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun ExpiryLine(domain: NftDomainExpiry) {
    if (domain.isOnSale) {
        subhead2_grey(text = stringResource(Res.string.nft_domain_on_sale_hint))
        return
    }
    val daysLeft = domain.rememberDaysLeft()
    Text(
        text = pluralStringResource(Res.plurals.nft_domain_expires_row, daysLeft, daysLeft),
        style = ComposeAppTheme.typography.subhead2,
        color = domainExpiryLevel(daysLeft).color(),
    )
}

@Composable
private fun Totals(selectedCount: Int, totals: NftDomainRenewUiState) {
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TotalRow(
            caption = pluralStringResource(Res.plurals.nft_domain_renew_total, selectedCount, selectedCount),
            value = totals.cost,
        )
        TotalRow(caption = stringResource(Res.string.FeeSettings_NetworkFee), value = totals.fee)
    }
}

@Composable
private fun TotalRow(caption: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        subhead2_grey(text = caption)
        subhead2_leah(text = value)
    }
}

private val previewOwner = NftOwner("account", BlockchainType.Ton, "owner")

private fun previewDomain(name: String, daysLeft: Long, isOnSale: Boolean = false) = NftDomainExpiry(
    owner = previewOwner,
    nftAddress = name,
    name = name,
    expiresAt = System.currentTimeMillis() / 1000 + daysLeft * 86_400 + 3_600,
    isOnSale = isOnSale,
)

private val previewDomains = listOf(
    previewDomain("piratecash.ton", daysLeft = 12),
    previewDomain("cosanta.ton", daysLeft = 5),
    previewDomain("tokenstorage.ton", daysLeft = 41, isOnSale = true),
    previewDomain("piratecashbot.ton", daysLeft = 203),
)

private val previewTotals = NftDomainRenewUiState(
    domainNames = emptyList(),
    renewUntil = "14 March 2027",
    cost = "0.04 TON",
    fee = "≈ 0.06 TON · $0.30",
    insufficientBalance = null,
    canRenew = true,
    outcome = RenewOutcome.Idle,
)

@Composable
private fun PreviewContent(state: NftDomainRenewalState, totals: NftDomainRenewUiState?) {
    ComposeAppTheme {
        NftDomainRenewScreen(
            state = state,
            totals = totals,
            renewUntil = "14 March 2027",
            onToggle = {},
            onRenew = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun NftDomainRenewScreenMixedPreview() = PreviewContent(
    state = NftDomainRenewalState(domains = previewDomains, deselected = setOf("piratecashbot.ton")),
    totals = previewTotals,
)

@Preview
@Composable
private fun NftDomainRenewScreenBalanceHiddenPreview() = PreviewContent(
    state = NftDomainRenewalState(domains = previewDomains, hidden = true),
    totals = previewTotals.copy(cost = "*****", fee = "*****"),
)

@Preview
@Composable
private fun NftDomainRenewScreenNoneSelectedPreview() = PreviewContent(
    state = NftDomainRenewalState(domains = previewDomains, deselected = previewDomains.map { it.nftAddress }.toSet()),
    totals = null,
)
