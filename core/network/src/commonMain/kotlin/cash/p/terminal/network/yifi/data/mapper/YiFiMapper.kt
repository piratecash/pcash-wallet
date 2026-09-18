package cash.p.terminal.network.yifi.data.mapper

import cash.p.terminal.network.yifi.data.entity.YiFiChainDto
import cash.p.terminal.network.yifi.data.entity.YiFiPairDto
import cash.p.terminal.network.yifi.data.entity.YiFiQuoteDto
import cash.p.terminal.network.yifi.data.entity.YiFiSwapDto
import cash.p.terminal.network.yifi.data.entity.YiFiTokenDto
import cash.p.terminal.network.yifi.domain.entity.YiFiChain
import cash.p.terminal.network.yifi.domain.entity.YiFiOrder
import cash.p.terminal.network.yifi.domain.entity.YiFiPair
import cash.p.terminal.network.yifi.domain.entity.YiFiQuote
import cash.p.terminal.network.yifi.domain.entity.YiFiToken

internal class YiFiMapper {
    fun mapChainDto(dto: YiFiChainDto) = YiFiChain(
        id = dto.id,
        chainId = dto.chainId,
        aliases = dto.aliases,
        nativeToken = dto.nativeToken.orEmpty(),
    )

    fun mapTokenDto(dto: YiFiTokenDto) = YiFiToken(
        network = dto.network,
        ticker = dto.ticker,
        contractAddress = dto.contractAddress,
    )

    fun mapPairDto(dto: YiFiPairDto) = YiFiPair(
        minAmount = dto.minAmount,
        maxAmount = dto.maxAmount,
    )

    fun mapQuoteDto(dto: YiFiQuoteDto) = YiFiQuote(
        rateId = dto.rateId,
        provider = dto.provider,
        exchangerName = dto.exchangerName,
        estimatedOutput = dto.estimatedOutput,
        estimatedTime = dto.estimatedTime,
    )

    fun mapSwapDto(dto: YiFiSwapDto) = YiFiOrder(
        transactionId = dto.transactionId,
        provider = dto.provider,
        depositAddress = dto.depositAddress,
        extraIdDeposit = dto.extraIdDeposit,
        receiveAmount = dto.receiveAmount,
    )
}
