package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.quickswap
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.BlockchainType

object QuickSwapProvider : BaseUniswapProvider() {
    override val id = "quickswap"
    override val title = "QuickSwap"
    override val icon = Res.drawable.quickswap

    override suspend fun supports(token: Token): Boolean {
        return token.blockchainType == BlockchainType.Polygon
    }
}
