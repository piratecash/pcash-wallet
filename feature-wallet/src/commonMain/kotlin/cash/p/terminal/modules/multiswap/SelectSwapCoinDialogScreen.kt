package cash.p.terminal.modules.multiswap

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.iconPlaceholder
import cash.p.terminal.modules.balance.ui.HideBalanceToggle
import cash.p.terminal.ui.compose.components.Badge
import cash.p.terminal.ui.compose.components.MultitextM1
import cash.p.terminal.ui.compose.components.SearchBar
import cash.p.terminal.ui_compose.components.B2
import cash.p.terminal.ui_compose.components.D1
import cash.p.terminal.ui_compose.components.HsImage
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SectionUniversalItem
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui.compose.components.coinIconPlate
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.alternativeImageUrl
import cash.p.terminal.wallet.badge
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.imageUrl
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ManageCoins_Search
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import java.math.BigDecimal

private const val HIDDEN_BALANCE = "*****"

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SelectSwapCoinDialogScreen(
    title: String,
    uiState: SwapSelectCoinUiState,
    onSearchTextChanged: (String) -> Unit,
    onClose: () -> Unit,
    onClickItem: (CoinBalanceItem) -> Unit,
    onToggleBalanceVisibility: () -> Unit,
    onBalanceClick: (CoinBalanceItem) -> Unit,
) {
    Column(modifier = Modifier.background(color = ComposeAppTheme.colors.backgroundBase)) {
        SearchBar(
            title = title,
            searchHintText = stringResource(Res.string.ManageCoins_Search),
            onClose = onClose,
            onSearchTextChanged = onSearchTextChanged
        )

        HideBalanceToggle(
            hidden = uiState.balanceHidden,
            onToggle = onToggleBalanceVisibility,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (uiState.loading && uiState.coinBalanceItems.isEmpty()) {
            Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .padding(top = 4.dp, end = 8.dp),
                    color = ComposeAppTheme.colors.textSecondary,
                    strokeWidth = 4.dp
                )
            }
        }

        LazyColumn {
            items(uiState.coinBalanceItems) { coinItem ->
                CoinBalanceRow(coinItem, onClickItem, onBalanceClick)
            }
            item {
                VSpacer(height = 32.dp)
            }
        }
    }
}

@Composable
internal fun CoinBalanceRow(
    coinItem: CoinBalanceItem,
    onClickItem: (CoinBalanceItem) -> Unit,
    onBalanceClick: (CoinBalanceItem) -> Unit,
) {
    SectionUniversalItem(borderTop = true) {
        RowUniversal(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            onClick = {
                onClickItem.invoke(coinItem)
            }
        ) {
            HsImage(
                url = coinItem.token.coin.imageUrl,
                alternativeUrl = coinItem.token.coin.alternativeImageUrl,
                placeholder = coinItem.token.iconPlaceholder,
                modifier = Modifier.size(32.dp).coinIconPlate()
            )
            Spacer(modifier = Modifier.size(16.dp))
            MultitextM1(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        B2(text = coinItem.token.coin.code)
                        coinItem.token.badge?.let {
                            Badge(
                                modifier = Modifier.padding(start = 6.dp),
                                text = it
                            )
                        }
                    }
                },
                subtitle = {
                    D1(
                        text = coinItem.token.coin.name,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                },
                modifier = Modifier.weight(1f)
            )
            CoinBalanceValues(coinItem, onBalanceClick)
        }
    }
}

@Composable
private fun CoinBalanceValues(
    coinItem: CoinBalanceItem,
    onBalanceClick: (CoinBalanceItem) -> Unit,
) {
    // A hidden row always shows two lines, even when the coin has no fiat rate.
    val amount = if (coinItem.balanceHidden) HIDDEN_BALANCE else coinItem.balanceText
    val fiat = if (coinItem.balanceHidden) HIDDEN_BALANCE else coinItem.fiatBalanceText
    val clickModifier = if (coinItem.balance != null) {
        Modifier.clickable(interactionSource = null, indication = null) { onBalanceClick(coinItem) }
    } else {
        Modifier
    }
    MultitextM1(
        title = {
            amount?.let { B2(text = it) }
        },
        subtitle = {
            fiat?.let {
                D1(
                    modifier = Modifier.align(Alignment.End),
                    text = it
                )
            }
        },
        modifier = clickModifier
    )
}

@Preview
@Composable
private fun SelectSwapCoinDialogScreenHiddenPreview() {
    SelectSwapCoinDialogScreenPreview(
        balanceHidden = true,
        items = listOf(
            previewItem("USDT", balanceHidden = true),
            previewItem("TRX", balanceHidden = false, balanceClickToggles = true),
            previewItem("ETH", balanceHidden = true, fiat = false),
            previewItem("BNB", balance = null),
        ),
    )
}

@Preview
@Composable
private fun SelectSwapCoinDialogScreenVisiblePreview() {
    SelectSwapCoinDialogScreenPreview(
        balanceHidden = false,
        items = listOf(
            previewItem("USDT"),
            previewItem("ETH", fiat = false),
            previewItem("BNB", balance = null),
        ),
    )
}

@Composable
private fun SelectSwapCoinDialogScreenPreview(balanceHidden: Boolean, items: List<CoinBalanceItem>) {
    ComposeAppTheme {
        SelectSwapCoinDialogScreen(
            title = "You Pay",
            uiState = SwapSelectCoinUiState(
                coinBalanceItems = items,
                balanceHidden = balanceHidden,
                loading = false,
            ),
            onSearchTextChanged = {},
            onClose = {},
            onClickItem = {},
            onToggleBalanceVisibility = {},
            onBalanceClick = {},
        )
    }
}

private fun previewItem(
    code: String,
    balanceHidden: Boolean = false,
    balanceClickToggles: Boolean = balanceHidden,
    fiat: Boolean = true,
    balance: BigDecimal? = BigDecimal("12.5"),
) = CoinBalanceItem(
    token = Token(
        coin = Coin(uid = code.lowercase(), name = code, code = code),
        blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
        type = TokenType.Native,
        decimals = 18
    ),
    balance = balance,
    fiatBalanceValue = null,
    balanceText = balance?.let { "$it $code" },
    fiatBalanceText = if (balance != null && fiat) "\$$balance" else null,
    balanceHidden = balanceHidden,
    balanceClickToggles = balanceClickToggles,
)
