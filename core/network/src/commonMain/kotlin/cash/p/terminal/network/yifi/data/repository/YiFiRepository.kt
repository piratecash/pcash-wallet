package cash.p.terminal.network.yifi.data.repository

import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.SwapProviderStatusRequest
import cash.p.terminal.network.swaprepository.SwapProviderTransactionStatusRepository
import cash.p.terminal.network.swaprepository.SwapProviderTransactionStatusResult
import cash.p.terminal.network.swaprepository.parseIsoTimestamp
import cash.p.terminal.network.yifi.api.YiFiApi
import cash.p.terminal.network.yifi.data.entity.request.CreateSwapYiFiRequest
import cash.p.terminal.network.yifi.data.mapper.YiFiMapper
import cash.p.terminal.network.yifi.domain.entity.YiFiChain
import cash.p.terminal.network.yifi.domain.entity.YiFiOrder
import cash.p.terminal.network.yifi.domain.entity.YiFiPair
import cash.p.terminal.network.yifi.domain.entity.YiFiQuote
import cash.p.terminal.network.yifi.domain.entity.YiFiToken
import java.math.BigDecimal

class YiFiRepository internal constructor(
    private val yiFiApi: YiFiApi,
    private val yiFiMapper: YiFiMapper,
) : SwapProviderTransactionStatusRepository {

    suspend fun getChains(): List<YiFiChain> =
        yiFiApi.getChains().chains.map(yiFiMapper::mapChainDto)

    suspend fun searchTokens(network: String, search: String): List<YiFiToken> =
        yiFiApi.searchTokens(network, search).tokens.map(yiFiMapper::mapTokenDto)

    suspend fun getPair(
        fromToken: String,
        fromNetwork: String,
        toToken: String,
        toNetwork: String,
    ): YiFiPair? = yiFiApi.getPairs(fromToken, fromNetwork, toToken, toNetwork)
        .tokenPairs.firstOrNull()
        ?.let(yiFiMapper::mapPairDto)

    suspend fun getBestQuote(
        tickerFrom: String,
        networkFrom: String,
        tickerTo: String,
        networkTo: String,
        amount: BigDecimal,
        excludedProviders: Set<String> = emptySet(),
    ): YiFiQuote? = yiFiApi.getQuote(
        tickerFrom = tickerFrom,
        networkFrom = networkFrom,
        tickerTo = tickerTo,
        networkTo = networkTo,
        amount = amount,
        excludedProviders = excludedProviders,
    ).bestQuote.firstOrNull()?.let(yiFiMapper::mapQuoteDto)

    suspend fun createSwap(request: CreateSwapYiFiRequest): YiFiOrder =
        yiFiApi.createSwap(request).let(yiFiMapper::mapSwapDto)

    override suspend fun getTransactionStatus(
        request: SwapProviderStatusRequest,
    ): SwapProviderTransactionStatusResult {
        val transaction = yiFiApi.getTransaction(request.transactionId)
        val status = transaction.status.toTransactionStatus()
        val finishedAt = if (status == TransactionStatusEnum.FINISHED) {
            transaction.completionTime?.takeIf { it.isNotBlank() }?.parseIsoTimestamp()
        } else {
            null
        }

        return SwapProviderTransactionStatusResult(
            status = status,
            amountOutReal = transaction.receiveAmount?.toBigDecimalOrNull(),
            finishedAt = finishedAt,
        )
    }

    private fun String.toTransactionStatus(): TransactionStatusEnum = when (lowercase()) {
        "pending", "active" -> TransactionStatusEnum.WAITING
        "processing" -> TransactionStatusEnum.EXCHANGING
        "completed" -> TransactionStatusEnum.FINISHED
        "failed", "cancelled" -> TransactionStatusEnum.FAILED
        else -> TransactionStatusEnum.UNKNOWN
    }
}
