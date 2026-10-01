package cash.p.terminal.network.backendswap.data.repository

import cash.p.terminal.network.backendswap.api.BackendSwapApi
import cash.p.terminal.network.backendswap.data.mapper.BackendSwapMapper
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapConfirmRequest
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapCreateRequest
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapCreatedOrder
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapCurrency
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapEstimate
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapEstimateRequest
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapProviderInfo
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.SwapProviderStatusRequest
import cash.p.terminal.network.swaprepository.SwapProviderTransactionStatusRepository
import cash.p.terminal.network.swaprepository.SwapProviderTransactionStatusResult

class BackendSwapRepository internal constructor(
    private val backendSwapApi: BackendSwapApi,
    private val mapper: BackendSwapMapper,
) : SwapProviderTransactionStatusRepository {

    suspend fun getProviders(): List<BackendSwapProviderInfo> =
        backendSwapApi.getProviders().map(mapper::mapProviderDto)

    suspend fun getCurrencies(provider: String): List<BackendSwapCurrency> =
        backendSwapApi.getCurrencies(provider).map(mapper::mapCurrencyDto)

    suspend fun estimate(request: BackendSwapEstimateRequest): BackendSwapEstimate =
        backendSwapApi.estimate(mapper.toDto(request)).let(mapper::mapEstimateDto)

    suspend fun create(request: BackendSwapCreateRequest, walletAddress: String): BackendSwapCreatedOrder =
        backendSwapApi.create(walletAddress, mapper.toDto(request)).let(mapper::mapCreatedSwapDto)

    suspend fun confirm(id: String, walletAddress: String, request: BackendSwapConfirmRequest) {
        backendSwapApi.confirm(id, walletAddress, mapper.toDto(request))
    }

    /** A `null` `walletAddress` means the row was never created server-side; there is nothing to poll. */
    override suspend fun getTransactionStatus(
        request: SwapProviderStatusRequest,
    ): SwapProviderTransactionStatusResult? {
        val walletAddress = request.walletAddress ?: return null
        val swap = backendSwapApi.getSwap(request.transactionId, walletAddress)
        return SwapProviderTransactionStatusResult(
            status = swap.status.toBackendSwapTransactionStatus(),
            amountOutReal = swap.amountToActual?.toBigDecimalOrNull(),
        )
    }

    private fun String.toBackendSwapTransactionStatus(): TransactionStatusEnum = when (lowercase()) {
        "waiting" -> TransactionStatusEnum.WAITING
        "exchanging" -> TransactionStatusEnum.EXCHANGING
        "hold" -> TransactionStatusEnum.VERIFYING
        "finished" -> TransactionStatusEnum.FINISHED
        "failed", "expired" -> TransactionStatusEnum.FAILED
        "refunded" -> TransactionStatusEnum.REFUNDED
        else -> TransactionStatusEnum.UNKNOWN
    }
}
