package cash.p.terminal.modules.multiswap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.modules.paycore.PayCoreAssets
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun CoinBalanceRowPreview() {
    val rubItem = CoinBalanceItem(
        token = PayCoreAssets.rubToken,
        balance = null,
        fiatBalanceValue = null
    )
    val usdtItem = CoinBalanceItem(
        token = Token(
            coin = Coin(
                uid = "tether",
                name = "Tether",
                code = "USDT",
                marketCapRank = null,
                image = null
            ),
            blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
            type = TokenType.Eip20("0xdac17f958d2ee523a2206206994597c13d831ec7"),
            decimals = 6
        ),
        balance = null,
        fiatBalanceValue = null
    )
    ComposeAppTheme {
        Column(modifier = Modifier.background(ComposeAppTheme.colors.backgroundBase)) {
            CoinBalanceRow(rubItem) {}
            CoinBalanceRow(usdtItem) {}
        }
    }
}
