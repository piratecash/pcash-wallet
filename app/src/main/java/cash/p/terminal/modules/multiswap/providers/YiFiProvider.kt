package cash.p.terminal.modules.multiswap.providers

import androidx.collection.LruCache
import cash.p.terminal.R
import cash.p.terminal.core.cache.accountScoped
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.modules.multiswap.ISwapFinalQuote
import cash.p.terminal.modules.multiswap.ISwapQuote
import cash.p.terminal.modules.multiswap.SwapAmountOutOfRange
import cash.p.terminal.modules.multiswap.SwapDepositTooSmall
import cash.p.terminal.modules.multiswap.SwapFinalQuoteEvm
import cash.p.terminal.modules.multiswap.SwapQuoteOffChain
import cash.p.terminal.modules.multiswap.SwapRouteNotFound
import cash.p.terminal.modules.multiswap.action.ActionCreate
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionResult
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings
import cash.p.terminal.modules.multiswap.ui.DataFieldRecipientExtended
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.network.yifi.data.entity.BackendYiFiResponseError
import cash.p.terminal.network.yifi.data.entity.request.CreateSwapYiFiRequest
import cash.p.terminal.network.yifi.data.repository.YiFiRepository
import cash.p.terminal.network.yifi.domain.entity.YiFiOrder
import cash.p.terminal.network.yifi.domain.entity.YiFiPair
import cash.p.terminal.network.yifi.domain.entity.YiFiQuote
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.math.BigDecimal

/** The order needs a deposit memo that the input network cannot carry. */
class YiFiDepositMemoUnsupported : Throwable()

class YiFiProvider(
    override val walletUseCase: WalletUseCase,
    private val yiFiRepository: YiFiRepository,
    private val tokenResolver: YiFiTokenResolver,
    accountManager: IAccountManager,
    private val dispatcherProvider: DispatcherProvider,
    private val providerSupport: OffChainSwapProviderSupport,
) : OffChainSwapProvider {
    override val id = "yifi"
    override val title = "YiFi"
    override val icon = R.drawable.ic_yifi
    override val riskType = ProviderRiskType.Controlled

    override val mevProtectionAvailable: Boolean = false

    private val pairCache = LruCache<String, CachedPair>(10)

    // SwapConfirmViewModel calls final quote too many times, so cache results
    private var finalQuote: CachedFinalQuote? by accountManager.accountScoped()
    private val finalQuoteMutex = Mutex()

    private class CachedPair(val pair: YiFiPair?, val timestamp: Long)

    private data class FinalQuoteKey(
        val tokenInId: String,
        val tokenOutId: String,
        val amountIn: BigDecimal,
        val receiveAddress: String,
        val refundAddress: String,
    )

    private data class CachedFinalQuote(
        val key: FinalQuoteKey,
        val order: YiFiOrder,
        val exchangerName: String,
        val timestamp: Long,
    )

    private data class PlacedOrder(val order: YiFiOrder, val exchangerName: String)

    private sealed interface OrderFailure {
        data object Memo : OrderFailure
        class Rate(val error: BackendYiFiResponseError) : OrderFailure
    }

    private companion object {
        const val CACHE_PAIR_DURATION = 1000L * 60
        const val CACHE_FINAL_QUOTE_DURATION = 1000L * 60 * 5
        const val MAX_ORDER_ATTEMPTS = 4
        val MEMO_CHAINS = setOf(
            BlockchainType.Stellar,
            BlockchainType.Ton,
            BlockchainType.Thorchain,
            BlockchainType.Mayachain,
        )
        val RATE_ERRORS = setOf(BackendYiFiResponseError.RATE_EXPIRED, BackendYiFiResponseError.RATE_MISMATCH)
        val NO_ROUTE_ERRORS = setOf(
            BackendYiFiResponseError.NO_QUOTES_AVAILABLE,
            BackendYiFiResponseError.COIN_NOT_FOUND,
            BackendYiFiResponseError.NO_SOURCES_AVAILABLE,
        )
        val BCH_PREFIX = Regex("^bitcoincash:", RegexOption.IGNORE_CASE)
    }

    // No network call here: SwapQuoteService starts providers sequentially.
    override suspend fun start() = tokenResolver.clear()

    override suspend fun supports(tokenFrom: Token, tokenTo: Token): Boolean {
        if (tokenTo.isZcashNonTransparent) return false
        return coroutineScope {
            val from = async { supports(tokenFrom) }
            val to = async { supports(tokenTo) }
            from.await() && to.await()
        }
    }

    override suspend fun supports(token: Token): Boolean =
        !token.isZcashShielded && tokenResolver.resolveAsset(token) != null

    override suspend fun fetchQuote(
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        settings: Map<String, Any?>
    ): ISwapQuote = withContext(dispatcherProvider.io) {
        val assetIn = requireAsset(tokenIn)
        val assetOut = requireAsset(tokenOut)
        coroutineScope {
            val pairDeferred = async { getPair(assetIn, assetOut) }
            val quoteDeferred = async { getBestQuote(assetIn, assetOut, amountIn) }

            val pair = pairDeferred.await() ?: throw SwapRouteNotFound()
            if (amountIn < pair.minAmount) throw SwapDepositTooSmall(pair.minAmount)
            if (pair.maxAmount.signum() > 0 && amountIn > pair.maxAmount) throw SwapAmountOutOfRange()
            val best = quoteDeferred.await() ?: throw SwapRouteNotFound()

            SwapQuoteOffChain(
                amountOut = best.estimatedOutput,
                priceImpact = null,
                fields = emptyList(),
                settings = emptyList(),
                tokenIn = tokenIn,
                tokenOut = tokenOut,
                amountIn = amountIn,
                actionRequired = getCreateTokenActionRequired(listOf(tokenIn, tokenOut)),
                estimationTime = parseYiFiEtaSeconds(best.estimatedTime),
            )
        }
    }

    override fun getCreateTokenActionRequired(tokens: List<Token>): ActionCreate? =
        providerSupport.getCreateTokenActionRequired(tokens)

    override suspend fun getWarningMessage(tokenIn: Token, tokenOut: Token): TranslatableString? =
        withContext(dispatcherProvider.io) {
            providerSupport.getWarningMessage(tokenIn)
        }

    override suspend fun fetchFinalQuote(
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        swapSettings: Map<String, Any?>,
        sendTransactionSettings: SendTransactionSettings?,
        swapQuote: ISwapQuote
    ): ISwapFinalQuote = withContext(dispatcherProvider.io) {
        finalQuoteMutex.withLock {
            val receiveAddress = normalizeAddress(tokenOut, walletUseCase.getReceiveAddress(tokenOut))
            val refundAddress = normalizeAddress(tokenIn, providerSupport.getRefundAddress(tokenIn))
            val key = FinalQuoteKey(
                tokenInId = tokenIn.tokenQuery.id,
                tokenOutId = tokenOut.tokenQuery.id,
                amountIn = amountIn,
                receiveAddress = receiveAddress,
                refundAddress = refundAddress,
            )
            val cached = finalQuote?.takeIf {
                it.key == key && System.currentTimeMillis() - it.timestamp < CACHE_FINAL_QUOTE_DURATION
            }
            val placed = if (cached != null) {
                PlacedOrder(cached.order, cached.exchangerName)
            } else {
                createOrder(tokenIn, tokenOut, key).also {
                    finalQuote = CachedFinalQuote(key, it.order, it.exchangerName, System.currentTimeMillis())
                }
            }
            buildFinalQuote(tokenIn, tokenOut, amountIn, placed)
        }
    }

    override fun onTransactionCompleted(
        transaction: SwapProviderTransaction,
        result: SendTransactionResult,
    ) {
        // The deposit is funded now; a repeat swap must get a new order.
        finalQuote = null
        providerSupport.onTransactionCompleted(transaction, result)
    }

    private suspend fun createOrder(tokenIn: Token, tokenOut: Token, key: FinalQuoteKey): PlacedOrder {
        val assetIn = requireAsset(tokenIn)
        val assetOut = requireAsset(tokenOut)
        val excluded = mutableSetOf<String>()
        var lastFailure: OrderFailure? = null
        repeat(MAX_ORDER_ATTEMPTS) {
            val best = getBestQuote(assetIn, assetOut, key.amountIn, excluded) ?: failWith(lastFailure)
            val order = try {
                yiFiRepository.createSwap(buildSwapRequest(best, assetIn, assetOut, key))
            } catch (e: BackendYiFiResponseError) {
                if (e.code !in RATE_ERRORS) throw e
                lastFailure = OrderFailure.Rate(e)
                return@repeat
            }
            if (!order.extraIdDeposit.isNullOrBlank() && tokenIn.blockchainType !in MEMO_CHAINS) {
                lastFailure = OrderFailure.Memo
                excluded += best.provider
                return@repeat
            }
            return PlacedOrder(order, best.exchangerName)
        }
        failWith(lastFailure)
    }

    private fun failWith(failure: OrderFailure?): Nothing = throw when (failure) {
        OrderFailure.Memo -> YiFiDepositMemoUnsupported()
        is OrderFailure.Rate -> failure.error
        null -> SwapRouteNotFound()
    }

    private fun buildSwapRequest(
        quote: YiFiQuote,
        assetIn: YiFiAsset,
        assetOut: YiFiAsset,
        key: FinalQuoteKey,
    ) = CreateSwapYiFiRequest(
        provider = quote.provider,
        fromToken = assetIn.ticker,
        fromNetwork = assetIn.network,
        toToken = assetOut.ticker,
        toNetwork = assetOut.network,
        amount = key.amountIn,
        receiveAddress = key.receiveAddress,
        rateId = quote.rateId,
        refundAddress = key.refundAddress,
    )

    private fun buildFinalQuote(
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        placed: PlacedOrder,
    ): SwapFinalQuoteEvm {
        val order = placed.order
        return SwapFinalQuoteEvm(
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = amountIn,
            amountOut = order.receiveAmount,
            amountOutMin = null, // floating rate
            sendTransactionData = providerSupport.buildTransactionData(
                tokenIn = tokenIn,
                amountIn = amountIn,
                depositAddress = order.depositAddress,
                memo = order.extraIdDeposit,
            ),
            priceImpact = null,
            fields = listOf(DataFieldRecipientExtended(Address(order.depositAddress), tokenIn.blockchainType)),
            swapProviderTransaction = providerSupport.buildSwapProviderTransaction(
                provider = SwapProvider.YIFI,
                transactionId = order.transactionId,
                tokenIn = tokenIn,
                tokenOut = tokenOut,
                amountIn = amountIn,
                amountOut = order.receiveAmount,
                subProviderId = placed.exchangerName,
            ),
        )
    }

    private suspend fun getPair(assetIn: YiFiAsset, assetOut: YiFiAsset): YiFiPair? {
        val key = "${assetIn.ticker}-${assetIn.network}-${assetOut.ticker}-${assetOut.network}"
        pairCache[key]?.takeIf { System.currentTimeMillis() - it.timestamp < CACHE_PAIR_DURATION }
            ?.let { return it.pair }
        return yiFiRepository.getPair(assetIn.ticker, assetIn.network, assetOut.ticker, assetOut.network)
            .also { pairCache.put(key, CachedPair(it, System.currentTimeMillis())) }
    }

    private suspend fun getBestQuote(
        assetIn: YiFiAsset,
        assetOut: YiFiAsset,
        amountIn: BigDecimal,
        excludedProviders: Set<String> = emptySet(),
    ): YiFiQuote? = try {
        yiFiRepository.getBestQuote(
            tickerFrom = assetIn.ticker,
            networkFrom = assetIn.network,
            tickerTo = assetOut.ticker,
            networkTo = assetOut.network,
            amount = amountIn,
            excludedProviders = excludedProviders,
        )
    } catch (e: BackendYiFiResponseError) {
        if (e.statusCode == 404 && e.code in NO_ROUTE_ERRORS) null else throw e
    }

    private suspend fun requireAsset(token: Token): YiFiAsset =
        tokenResolver.resolveAsset(token) ?: throw SwapRouteNotFound()

    // YiFi's BCH address regex rejects the CashAddr prefix that bitcoin-kit includes.
    private fun normalizeAddress(token: Token, address: String): String =
        if (token.blockchainType == BlockchainType.BitcoinCash) address.replaceFirst(BCH_PREFIX, "") else address
}

/** Parses YiFi `estimatedTime` ("~30s", "~194m", "10-60" minutes) into seconds. */
internal fun parseYiFiEtaSeconds(text: String?): Long? {
    val match = text?.let { Regex("""~(\d+)([sm])""").matchEntire(it.trim()) }
        ?: return parseMinutesRangeToSeconds(text)
    val value = match.groupValues[1].toLong()
    return if (match.groupValues[2] == "s") value else value * 60
}
