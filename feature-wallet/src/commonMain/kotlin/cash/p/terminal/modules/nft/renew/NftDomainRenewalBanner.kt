package cash.p.terminal.modules.nft.renew

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.nft.nftCard
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.resources.ic_globe
import cash.p.terminal.resources.nft_domains_expiring_banner
import cash.p.terminal.resources.nft_domains_expiring_banner_hidden
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NftDomainRenewalBanner(
    count: Int,
    renewUntil: String,
    hidden: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RowUniversal(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .nftCard()
            .background(ComposeAppTheme.colors.surfacePrimary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalPadding = 12.dp,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(ComposeAppTheme.colors.statusWarning20),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_globe),
                contentDescription = null,
                tint = ComposeAppTheme.colors.statusWarning,
                modifier = Modifier.size(22.dp),
            )
        }
        subhead2_leah(
            text = if (hidden) {
                stringResource(Res.string.nft_domains_expiring_banner_hidden, renewUntil)
            } else {
                pluralStringResource(Res.plurals.nft_domains_expiring_banner, count, count, renewUntil)
            },
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        Icon(
            painter = painterResource(Res.drawable.ic_arrow_right),
            contentDescription = null,
            tint = ComposeAppTheme.colors.brandDefault,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Preview
@Composable
private fun NftDomainRenewalBannerOneDomainPreview() {
    ComposeAppTheme {
        NftDomainRenewalBanner(count = 1, renewUntil = "14 March 2027", hidden = false, onClick = {})
    }
}

@Preview
@Composable
private fun NftDomainRenewalBannerManyDomainsPreview() {
    ComposeAppTheme {
        NftDomainRenewalBanner(count = 2, renewUntil = "14 March 2027", hidden = false, onClick = {})
    }
}

@Preview
@Composable
private fun NftDomainRenewalBannerHiddenPreview() {
    ComposeAppTheme {
        NftDomainRenewalBanner(count = 2, renewUntil = "14 March 2027", hidden = true, onClick = {})
    }
}
