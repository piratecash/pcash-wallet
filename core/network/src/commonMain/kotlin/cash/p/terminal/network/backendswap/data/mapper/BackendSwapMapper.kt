package cash.p.terminal.network.backendswap.data.mapper

import cash.p.terminal.network.backendswap.data.entity.AssetDto
import cash.p.terminal.network.backendswap.data.entity.ConfirmRequestDto
import cash.p.terminal.network.backendswap.data.entity.CreateRequestDto
import cash.p.terminal.network.backendswap.data.entity.CreatedSwapDto
import cash.p.terminal.network.backendswap.data.entity.CurrencyDto
import cash.p.terminal.network.backendswap.data.entity.EstimateDto
import cash.p.terminal.network.backendswap.data.entity.EstimateRequestDto
import cash.p.terminal.network.backendswap.data.entity.ProviderInfoDto
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapAsset
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapConfirmRequest
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapCreateRequest
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapCreatedOrder
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapCurrency
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapEstimate
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapEstimateRequest
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapProviderInfo
import java.math.BigDecimal

internal class BackendSwapMapper {
    fun mapProviderDto(dto: ProviderInfoDto) = BackendSwapProviderInfo(
        name = dto.name,
        displayName = dto.displayName,
        logoUrl = dto.logo?.url,
        active = dto.active,
        supportsFixed = dto.exchangeTypes?.fixed ?: false,
        supportsFloat = dto.exchangeTypes?.float ?: false,
    )

    fun mapCurrencyDto(dto: CurrencyDto) = BackendSwapCurrency(
        ticker = dto.ticker,
        coinId = dto.coinId,
        blockchain = dto.blockchain,
        contractAddress = dto.contractAddress,
    )

    fun mapEstimateDto(dto: EstimateDto) = BackendSwapEstimate(
        amountFrom = BigDecimal(dto.amountFrom),
        amountToWithFee = BigDecimal(dto.amountToWithFee),
        minAmount = dto.limits?.min?.toBigDecimalOrNull(),
        maxAmount = dto.limits?.max?.toBigDecimalOrNull(),
    )

    fun mapCreatedSwapDto(dto: CreatedSwapDto) = BackendSwapCreatedOrder(
        id = dto.id,
        externalId = dto.externalId,
        payinAddress = dto.payinAddress,
        payinExtraId = dto.payinExtraId,
        amountToExpected = dto.amountToExpected?.toBigDecimalOrNull(),
        payinExpiresAt = dto.payinExpiresAt,
        status = dto.status,
    )

    fun toDto(asset: BackendSwapAsset) = AssetDto(coinId = asset.coinId, blockchain = asset.blockchain)

    fun toDto(request: BackendSwapEstimateRequest) = EstimateRequestDto(
        provider = request.provider,
        type = request.type,
        from = toDto(request.from),
        to = toDto(request.to),
        amount = request.amount.toPlainString(),
    )

    fun toDto(request: BackendSwapCreateRequest) = CreateRequestDto(
        provider = request.provider,
        type = request.type,
        from = toDto(request.from),
        to = toDto(request.to),
        amount = request.amount.toPlainString(),
        recipient = request.recipient,
        recipientExtraId = request.recipientExtraId,
        refundAddress = request.refundAddress,
        refundExtraId = request.refundExtraId,
        fromAddress = request.fromAddress,
        fromExtraId = request.fromExtraId,
        clientRequestId = request.clientRequestId,
        signature = request.signature,
    )

    fun toDto(request: BackendSwapConfirmRequest) = ConfirmRequestDto(
        txHash = request.txHash,
        amount = request.amount.toPlainString(),
        address = request.address,
        addressExtraId = request.addressExtraId,
        currency = toDto(request.currency),
    )
}
