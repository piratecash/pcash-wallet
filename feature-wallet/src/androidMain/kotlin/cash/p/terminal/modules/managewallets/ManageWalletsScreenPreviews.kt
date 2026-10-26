package cash.p.terminal.modules.managewallets

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.modules.enablecoin.restoresettings.IRestoreSettingsUi
import cash.p.terminal.modules.enablecoin.restoresettings.TokenConfig
import cash.p.terminal.modules.restoreaccount.restoreblockchains.CoinViewItem
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_placeholder
import cash.p.terminal.ui_compose.components.ImageSource
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.flow.MutableStateFlow

@Preview(showBackground = true)
@Composable
private fun ManageWalletsScreenPreview() {
    ComposeAppTheme(darkTheme = true) {
        val items = listOf(
            CoinViewItem(
                item = Token(
                    coin = Coin("Bitcoin", "Bitcoin", "BTC"),
                    blockchain = Blockchain(BlockchainType.Bitcoin, "Bitcoin", null),
                    type = TokenType.Native,
                    decimals = 8
                ),
                imageSource = ImageSource.Local(Res.drawable.ic_placeholder),
                title = "BTC",
                subtitle = "Bitcoin",
                enabled = true
            ),
            CoinViewItem(
                item = Token(
                    coin = Coin("Ethereum", "Ethereum", "ETH"),
                    blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
                    type = TokenType.Native,
                    decimals = 18
                ),
                imageSource = ImageSource.Local(Res.drawable.ic_placeholder),
                title = "ETH",
                subtitle = "Ethereum",
                enabled = false,
                label = "ERC20",
                hasInfo = true
            ),
            CoinViewItem(
                item = Token(
                    coin = Coin("Ethereum", "Ethereum", "ETH"),
                    blockchain = Blockchain(BlockchainType.Base, "Base", null),
                    type = TokenType.Native,
                    decimals = 18
                ),
                imageSource = ImageSource.Local(Res.drawable.ic_placeholder),
                title = "ETH",
                subtitle = "Ethereum",
                enabled = true,
                label = "BASE",
                hasInfo = true
            ),
            CoinViewItem(
                item = Token(
                    coin = Coin("Zcash", "Zcash", "ZEC"),
                    blockchain = Blockchain(BlockchainType.Zcash, "Zcash", null),
                    type = TokenType.Native,
                    decimals = 8
                ),
                imageSource = ImageSource.Local(Res.drawable.ic_placeholder),
                title = "ZEC",
                subtitle = "Zcash",
                enabled = false,
                available = false
            )
        )

        val groups = listOf(
            CoinGroup(
                coinName = "Bitcoin",
                coinUid = "bitcoin",
                items = listOf(items[0]),
                isExpanded = false
            ),
            CoinGroup(
                coinName = "Zcash",
                coinUid = "zcash",
                items = listOf(items[3]),
                isExpanded = false
            ),
            CoinGroup(
                coinName = "Ethereum",
                coinUid = "ethereum",
                items = listOf(items[1], items[2]),
                isExpanded = true
            )
        )

        ManageWalletsScreen(
            navigation = HSNavigation(NavBackStack()),
            manageWalletsCallback = object : ManageWalletsCallback {
                override val groupsList = MutableStateFlow(groups)
                override val addTokenEnabled = true
                override val showScanToAddButton = false
                override val hardwareActionButtonText = ""
                override val errorMsg: String? = null
                override val offlineNoticeMsg: String? = null
                override val closeScreen: Boolean = false
                override fun updateFilter(text: String) = Unit
                override fun onOfflineNoticeShown() = Unit
                override fun enable(token: Token) = Unit
                override fun disable(token: Token) = Unit
                override fun toggleGroupExpansion(coinUid: String) = Unit
            },
            onBackPressed = {},
            requestScan = {},
            restoreSettingsViewModel = object : IRestoreSettingsUi {
                override val openTokenConfigure: Token? = null
                override fun tokenConfigureOpened() = Unit
                override fun consumeInitialConfig(): TokenConfig? = null
                override fun onEnter(tokenConfig: TokenConfig) = Unit
                override fun onCancelEnterBirthdayHeight() = Unit
            }
        )
    }
}
