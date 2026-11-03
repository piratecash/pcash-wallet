package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.pancake
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.BlockchainType

object PancakeSwapProvider : BaseUniswapProvider() {
    override val id = "pancake"
    override val title = "PancakeSwap"
    override val icon = Res.drawable.pancake

    override suspend fun supports(token: Token): Boolean {
        return token.blockchainType == BlockchainType.BinanceSmartChain
    }
}
