package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.uniswap
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.BlockchainType

object UniswapProvider : BaseUniswapProvider() {
    override val id = "uniswap"
    override val title = "Uniswap"
    override val icon = Res.drawable.uniswap

    override suspend fun supports(token: Token): Boolean {
        return token.blockchainType == BlockchainType.Ethereum
    }
}
