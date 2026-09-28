package cash.p.terminal.modules.multiswap.providers.backendswap

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
import cash.p.terminal.modules.multiswap.providers.MEMO_CHAINS
import cash.p.terminal.modules.multiswap.providers.OffChainSwapProvider
import cash.p.terminal.modules.multiswap.providers.OffChainSwapProviderSupport
import cash.p.terminal.modules.multiswap.providers.ProviderRiskType
import cash.p.terminal.modules.multiswap.providers.SwapDepositMemoUnsupported
import cash.p.terminal.modules.multiswap.providers.isZcashNonTransparent
import cash.p.terminal.modules.multiswap.providers.normalizeSwapAddress
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionResult
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings
import cash.p.terminal.modules.multiswap.ui.DataFieldRecipientExtended
import cash.p.terminal.network.backendswap.data.entity.BackendSwapError
import cash.p.terminal.network.backendswap.data.repository.BackendSwapRepository
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapAsset
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapConfirmRequest
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapCreatedOrder
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapEstimateRequest
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapProviderInfo
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigDecimal
import java.util.UUID

/** One p.cash backend provider (e.g. Changelly); orders are signed with the account's EVM key. */
class BackendSwapProvider(
    val info: BackendSwapProviderInfo,
    override val walletUseCase: WalletUseCase,
    private val accountManager: IAccountManager,
    private val backendSwapRepository: BackendSwapRepository,
    private val assetResolver: BackendSwapAssetResolver,
    private val signer: BackendSwapSigner,
    private val providerSupport: OffChainSwapProviderSupport,
    private val dispatcherProvider: DispatcherProvider,
) : OffChainSwapProvider {
    override val id = "$ID_PREFIX${info.name}"
    override val title = info.displayName
    override val icon = BackendSwapBranding.icon(info.name)
    override val iconUrl = info.logoUrl

    // The provider may put a swap on AML/KYC hold.
    override val riskType = ProviderRiskType.Controlled
    override val mevProtectionAvailable = false

    private val swapType = if (info.supportsFloat) TYPE_FLOAT else TYPE_FIXED

    // SwapConfirmViewModel requests the final quote repeatedly; each miss creates a new order.
    private var placedOrder: PlacedOrder? by accountManager.accountScoped()
    private val placedOrderMutex = Mutex()

    private data class OrderKey(
        val tokenInId: String,
        val tokenOutId: String,
        val amountIn: BigDecimal,
        val receiveAddress: String,
        val refundAddress: String,
    )

    private class PlacedOrder(
        val key: OrderKey,
        val order: BackendSwapCreatedOrder,
        val depositAddress: String,
        val memo: String?,
        val assetIn: BackendSwapAsset,
        val walletAddress: String,
        val validUntilMillis: Long?,
        val cachedUntilMillis: Long,
    )

    override suspend fun supports(tokenFrom: Token, tokenTo: Token): Boolean =
        !tokenTo.isZcashNonTransparent && supports(tokenFrom) && supports(tokenTo)

    override suspend fun supports(token: Token): Boolean {
        val account = accountManager.activeAccount ?: return false
        return signer.walletAddress(account) != null && assetResolver.resolve(info.name, token) != null
    }

    override suspend fun fetchQuote(
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        settings: Map<String, Any?>,
    ): ISwapQuote = withContext(dispatcherProvider.io) {
        val request = BackendSwapEstimateRequest(
            provider = info.name,
            type = swapType,
            from = requireAsset(tokenIn),
            to = requireAsset(tokenOut),
            amount = amountIn,
        )
        val estimate = try {
            backendSwapRepository.estimate(request)
        } catch (e: BackendSwapError) {
            throw e.toQuoteFailure()
        }
        SwapQuoteOffChain(
            amountOut = estimate.amountToWithFee,
            priceImpact = null,
            fields = emptyList(),
            settings = emptyList(),
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = amountIn,
            actionRequired = getCreateTokenActionRequired(listOf(tokenIn, tokenOut)),
        )
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
        swapQuote: ISwapQuote,
    ): ISwapFinalQuote = withContext(dispatcherProvider.io) {
        placedOrderMutex.withLock {
            val key = OrderKey(
                tokenInId = tokenIn.tokenQuery.id,
                tokenOutId = tokenOut.tokenQuery.id,
                amountIn = amountIn,
                receiveAddress = tokenOut.normalizeSwapAddress(walletUseCase.getReceiveAddress(tokenOut).trim()),
                refundAddress = tokenIn.normalizeSwapAddress(providerSupport.getRefundAddress(tokenIn).trim()),
            )
            val placed = placedOrder?.takeIf { it.key == key && System.currentTimeMillis() < it.cachedUntilMillis }
                ?: placeOrder(tokenIn, tokenOut, key).also { placedOrder = it }
            buildFinalQuote(tokenIn, tokenOut, placed, swapQuote.amountOut)
        }
    }

    override fun onTransactionCompleted(transaction: SwapProviderTransaction, result: SendTransactionResult) {
        val placed = placedOrder?.takeIf { it.order.id == transaction.transactionId }
        // The deposit is funded now; a repeat swap must get a new order.
        placedOrder = null
        providerSupport.onTransactionCompleted(transaction, result)
        val txHash = result.getCanonicalTxHash() ?: return
        placed ?: return
        dispatcherProvider.applicationScope.launch(dispatcherProvider.io) {
            confirmDeposit(placed, txHash, transaction.amountIn)
        }
    }

    // Best effort: the backend also picks the deposit up by itself, only later.
    private suspend fun confirmDeposit(placed: PlacedOrder, txHash: String, amountIn: BigDecimal) {
        try {
            backendSwapRepository.confirm(
                id = placed.order.id,
                walletAddress = placed.walletAddress,
                request = BackendSwapConfirmRequest(
                    txHash = txHash,
                    amount = amountIn,
                    address = placed.depositAddress,
                    addressExtraId = placed.memo,
                    currency = placed.assetIn,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Backend swap confirm failed")
        }
    }

    private suspend fun placeOrder(tokenIn: Token, tokenOut: Token, key: OrderKey): PlacedOrder {
        val account = checkNotNull(accountManager.activeAccount) { "No active account" }
        val walletAddress = checkNotNull(signer.walletAddress(account)) { "No EVM key for the account" }
        val assetIn = requireAsset(tokenIn)
        val message = CreateSwapMessage(
            provider = info.name,
            type = swapType,
            from = assetIn,
            to = requireAsset(tokenOut),
            amount = key.amountIn,
            recipient = key.receiveAddress,
            clientRequestId = UUID.randomUUID().toString(),
            refundAddress = key.refundAddress,
        )
        val order = backendSwapRepository.create(message.toCreateRequest(signer.sign(account, message)), walletAddress)
        val depositAddress = order.payinAddress?.takeIf { it.isNotBlank() }
            ?: error("Backend swap order has no deposit address")
        val memo = order.payinExtraId?.takeIf { it.isNotBlank() }
        if (memo != null && tokenIn.blockchainType !in MEMO_CHAINS) throw SwapDepositMemoUnsupported()
        val validUntilMillis = order.payinExpiresAt?.minus(DEPOSIT_DEADLINE_MARGIN_MS)
        val cacheExpiry = System.currentTimeMillis() + CACHE_ORDER_DURATION_MS
        return PlacedOrder(
            key = key,
            order = order,
            depositAddress = depositAddress,
            memo = memo,
            assetIn = assetIn,
            walletAddress = walletAddress,
            validUntilMillis = validUntilMillis,
            cachedUntilMillis = validUntilMillis?.coerceAtMost(cacheExpiry) ?: cacheExpiry,
        )
    }

    private fun buildFinalQuote(
        tokenIn: Token,
        tokenOut: Token,
        placed: PlacedOrder,
        quotedAmountOut: BigDecimal,
    ): SwapFinalQuoteEvm {
        val amountIn = placed.key.amountIn
        val amountOut = placed.order.amountToExpected ?: quotedAmountOut
        return SwapFinalQuoteEvm(
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = amountIn,
            amountOut = amountOut,
            amountOutMin = null, // floating rate
            sendTransactionData = providerSupport.buildTransactionData(
                tokenIn = tokenIn,
                amountIn = amountIn,
                depositAddress = placed.depositAddress,
                memo = placed.memo,
            ),
            priceImpact = null,
            fields = listOf(DataFieldRecipientExtended(Address(placed.depositAddress), tokenIn.blockchainType)),
            swapProviderTransaction = providerSupport.buildSwapProviderTransaction(
                provider = SwapProvider.PCASH_BACKEND,
                transactionId = placed.order.id,
                tokenIn = tokenIn,
                tokenOut = tokenOut,
                amountIn = amountIn,
                amountOut = amountOut,
                subProviderId = info.name,
                externalId = placed.order.externalId,
                walletAddress = placed.walletAddress,
            ),
            validUntilMillis = placed.validUntilMillis,
        )
    }

    private suspend fun requireAsset(token: Token): BackendSwapAsset =
        assetResolver.resolve(info.name, token) ?: throw SwapRouteNotFound()

    private fun BackendSwapError.toQuoteFailure(): Throwable = when {
        code == AMOUNT_LIMITS_ERROR && limitType == LIMIT_MINIMUM ->
            minAmount?.toBigDecimalOrNull()?.let(::SwapDepositTooSmall) ?: SwapAmountOutOfRange()
        code == AMOUNT_LIMITS_ERROR || code == AMOUNT_OUT_OF_RANGE -> SwapAmountOutOfRange()
        statusCode in NO_ROUTE_STATUSES -> SwapRouteNotFound()
        else -> this
    }

    companion object {
        private const val ID_PREFIX = "p_"
        private const val TYPE_FLOAT = "float"
        private const val TYPE_FIXED = "fixed"
        private const val AMOUNT_LIMITS_ERROR = "AMOUNT_LIMITS_ERROR"
        private const val AMOUNT_OUT_OF_RANGE = "AMOUNT_OUT_OF_RANGE"
        private const val LIMIT_MINIMUM = "minimum"
        private val NO_ROUTE_STATUSES = 400..422
        private const val CACHE_ORDER_DURATION_MS = 5 * 60 * 1000L

        // Leaves time to broadcast and confirm the deposit before the provider closes the payin window.
        private const val DEPOSIT_DEADLINE_MARGIN_MS = 10 * 60 * 1000L
    }
}
