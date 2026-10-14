package cash.p.terminal.modules.coin.overview.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.modules.coin.ContractInfo
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.imageUrl

@Preview
@Composable
private fun ContractsPreview() {
    ComposeAppTheme(darkTheme = true) {
        val contracts = listOf(
            ContractInfo(
                "0xda123as34290098asd0098asdasd9098asd90123asd",
                BlockchainType.Ethereum.imageUrl,
                "https://etherscan.io/token/0xda123as34290098asd0098asdasd9098asd90123asd"
            ),
            ContractInfo(
                "BNB",
                BlockchainType.BinanceSmartChain.imageUrl,
                "https://explorer.binance.org/asset/BNB"
            ),
        )
        Contracts(contracts = contracts, {}, {})
    }
}
