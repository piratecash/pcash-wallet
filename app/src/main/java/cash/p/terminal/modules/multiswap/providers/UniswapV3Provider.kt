package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.uniswap_v3
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.uniswapkit.models.DexType

object UniswapV3Provider : BaseUniswapV3Provider(DexType.Uniswap) {
    override val id = "uniswap_v3"
    override val title = "Uniswap V3"
    override val icon = Res.drawable.uniswap_v3

    override suspend fun supports(token: Token) = when (token.blockchainType) {
        BlockchainType.Ethereum,
        BlockchainType.ArbitrumOne,
//            BlockchainType.Optimism,
        BlockchainType.Polygon,
        BlockchainType.BinanceSmartChain,
        BlockchainType.Base,
        BlockchainType.ZkSync,
        BlockchainType.RobinhoodChain,
        -> true
        else -> false
    }
}
