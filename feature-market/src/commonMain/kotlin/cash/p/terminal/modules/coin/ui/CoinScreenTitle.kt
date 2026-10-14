package cash.p.terminal.modules.coin.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.components.HsImage
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.body_grey
import cash.p.terminal.ui_compose.components.subhead1_grey
import org.jetbrains.compose.resources.DrawableResource

@Composable
fun CoinScreenTitle(
    coinName: String,
    marketCapRank: Int?,
    coinIconUrl: String,
    alternativeCoinIconUrl: String?,
    iconPlaceholder: DrawableResource?
) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        HsImage(
            url = coinIconUrl,
            alternativeUrl = alternativeCoinIconUrl,
            placeholder = iconPlaceholder,
            modifier = Modifier.size(32.dp).clip(CircleShape)
        )

        body_grey(
            text = coinName,
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        marketCapRank?.let { marketCapRank ->
            subhead1_grey(
                text = "#$marketCapRank",
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}
