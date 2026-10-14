package cash.p.terminal.modules.coin.overview.ui

import cash.p.terminal.resources.CoinPage_Links
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_arrow_right
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.coin.CoinLink
import cash.p.terminal.ui_compose.components.CellSingleLineClear
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.body_leah

@Composable
fun Links(links: List<CoinLink>, onCoinLinkClick: (CoinLink) -> Unit) {
    Column {
        CellSingleLineClear(borderTop = true) {
            body_leah(text = stringResource(Res.string.CoinPage_Links))
        }

        CellUniversalLawrenceSection(links) { link ->
            RowUniversal(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                onClick = { onCoinLinkClick(link) }
            ) {
                Image(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(link.icon),
                    contentDescription = "link",
                )
                body_leah(
                    text = link.title,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                )
                Image(
                    painter = painterResource(Res.drawable.ic_arrow_right),
                    contentDescription = ""
                )
            }
        }
    }
}
