package cash.p.terminal.modules.coin.overview.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.modules.coin.CoinLink
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_academy_20
import cash.p.terminal.resources.ic_globe_20
import cash.p.terminal.resources.ic_telegram_20
import cash.p.terminal.resources.ic_twitter_20
import cash.p.terminal.wallet.models.LinkType

@Preview
@Composable
private fun LinksPreview() {
    ComposeAppTheme {
        val links = listOf(
            CoinLink(
                "http://q.com",
                LinkType.Guide,
                "@twitter",
                Res.drawable.ic_academy_20
            ),
            CoinLink(
                "http://q.com",
                LinkType.Guide,
                "@twitter",
                Res.drawable.ic_globe_20
            ),
            CoinLink(
                "http://q.com",
                LinkType.Twitter,
                "@twitter",
                Res.drawable.ic_twitter_20
            ),
            CoinLink(
                "http://q.com",
                LinkType.Telegram,
                "Telegram",
                Res.drawable.ic_telegram_20
            ),
        )
        Links(links = links, onCoinLinkClick = {})
    }
}
