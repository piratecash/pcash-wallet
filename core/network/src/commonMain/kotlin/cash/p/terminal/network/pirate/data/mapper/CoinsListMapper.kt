package cash.p.terminal.network.pirate.data.mapper

import cash.p.terminal.network.pirate.data.entity.BlockchainDto
import cash.p.terminal.network.pirate.data.entity.CoinDto
import cash.p.terminal.network.pirate.data.entity.CoinsListDto
import cash.p.terminal.network.pirate.data.entity.TokenDto
import cash.p.terminal.network.pirate.domain.model.CoinsList
import cash.p.terminal.network.pirate.domain.model.RemoteBlockchain
import cash.p.terminal.network.pirate.domain.model.RemoteCoin
import cash.p.terminal.network.pirate.domain.model.RemoteToken

internal class CoinsListMapper {
    fun map(dto: CoinsListDto) = CoinsList(
        blockchains = dto.blockchains.map(::mapBlockchain),
        coins = dto.coins.map(::mapCoin),
    )

    private fun mapBlockchain(dto: BlockchainDto) = RemoteBlockchain(
        uid = dto.uid,
        name = dto.name,
        url = dto.url,
    )

    private fun mapCoin(dto: CoinDto) = RemoteCoin(
        coingeckoId = dto.coingeckoId,
        name = dto.name,
        code = dto.code,
        priority = dto.priority,
        tokens = dto.tokens.map(::mapToken),
    )

    private fun mapToken(dto: TokenDto) = RemoteToken(
        type = dto.type,
        blockchainUid = dto.blockchainUid,
        address = dto.address,
        decimals = dto.decimals,
    )
}
