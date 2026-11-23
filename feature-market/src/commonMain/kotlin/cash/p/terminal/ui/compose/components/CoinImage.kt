package cash.p.terminal.ui.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import cash.p.terminal.core.iconPlaceholder
import cash.p.terminal.ui_compose.components.HsImage
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.alternativeImageUrl
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.imagePlaceholder
import cash.p.terminal.wallet.imageUrl

@Composable
fun CoinImage(
    coin: Coin?,
    modifier: Modifier,
    colorFilter: ColorFilter? = null
) = HsImage(
    url = coin?.imageUrl,
    alternativeUrl = coin?.alternativeImageUrl,
    placeholder = coin?.imagePlaceholder,
    modifier = modifier.coinIconPlate(),
    colorFilter = colorFilter
)

@Composable
fun CoinImage(
    token: Token?,
    modifier: Modifier,
    colorFilter: ColorFilter? = null
) = HsImage(
    url = token?.coin?.imageUrl,
    alternativeUrl = token?.coin?.alternativeImageUrl,
    placeholder = token?.iconPlaceholder,
    modifier = modifier.coinIconPlate(),
    colorFilter = colorFilter
)
