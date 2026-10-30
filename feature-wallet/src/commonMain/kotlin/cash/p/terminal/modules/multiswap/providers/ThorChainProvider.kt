package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.thorchain

object ThorChainProvider : BaseThorChainProvider(
    baseUrl = "https://gateway.liquify.com/chain/thorchain_api/thorchain/",
    affiliate = "piratecash",
    affiliateBps = 50,
) {
    override val id = "thorchain"
    override val title = "THORChain"
    override val icon = Res.drawable.thorchain
}
