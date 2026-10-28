package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.pancake_v3
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.uniswapkit.models.DexType

object PancakeSwapV3Provider : BaseUniswapV3Provider(DexType.PancakeSwap) {
    override val id = "pancake_v3"
    override val title = "PancakeSwap V3"
    override val icon = Res.drawable.pancake_v3

    override suspend fun supports(token: Token) = when (token.blockchainType) {
        BlockchainType.BinanceSmartChain,
        BlockchainType.Ethereum
        -> true
        else -> false
    }
}
