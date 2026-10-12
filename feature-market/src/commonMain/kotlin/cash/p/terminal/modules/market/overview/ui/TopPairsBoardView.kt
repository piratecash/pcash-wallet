package cash.p.terminal.modules.market.overview.ui

import cash.p.terminal.resources.Market_Overview_TopPairs
import cash.p.terminal.resources.ic_pairs_24
import cash.p.terminal.resources.Res
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.market.overview.TopPairViewItem
import cash.p.terminal.modules.market.toppairs.TopPairItem
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun TopPairsBoardView(
    topMarketPairs: List<TopPairViewItem>,
    onItemClick: (TopPairViewItem) -> Unit,
    onClickSeeAll: () -> Unit
) {
    MarketsSectionHeader(
        title = Res.string.Market_Overview_TopPairs,
        icon = painterResource(Res.drawable.ic_pairs_24),
        onClick = onClickSeeAll
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeAppTheme.colors.surfacePrimary)
    ) {
        topMarketPairs.forEach {
            TopPairItem(item = it, borderBottom = true, onItemClick = onItemClick)
        }

        SeeAllButton(onClickSeeAll)
    }

    Spacer(modifier = Modifier.height(24.dp))
}

