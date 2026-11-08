package cash.p.terminal.core.storage

import cash.p.terminal.core.utils.SwapTransactionMatcher
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class SwapProviderTransactionsStorage(
    private val router: StorageRouter,
    private val dispatcherProvider: DispatcherProvider
) {
    private fun dao(accountId: String) = router.account(accountId)?.swapProviderTransactionsDao()

    private fun List<SwapProviderTransaction>.newestFirst(limit: Int) =
        sortedByDescending { it.date }.take(limit)

    private companion object Companion {
        const val THRESHOLD_MSEC = 40_000
        const val AMOUNT_TOLERANCE = 0.005 // 0.5%
        const val FINISHED_AT_WINDOW_MS = 1_800_000L // ±30 minutes
        const val LEGACY_WINDOW_BEFORE_MS = 3_600_000L  // 1 hour before
        const val LEGACY_WINDOW_AFTER_MS = 10_800_000L  // 3 hours after
        const val OBSERVE_ALL_LIMIT = 100
    }

    fun save(
        swapProviderTransaction: SwapProviderTransaction
    ) {
        dao(swapProviderTransaction.accountId)?.insert(swapProviderTransaction)
    }

    suspend fun saveAsync(
        swapProviderTransaction: SwapProviderTransaction
    ) {
        withContext(dispatcherProvider.io) {
            dao(swapProviderTransaction.accountId)?.insert(swapProviderTransaction)
        }
    }

    fun getAll(
        token: Token,
        address: String,
        statusesExcluded: List<String>,
        providersExcluded: List<SwapProvider>,
        limit: Int
    ) = router.acrossAccounts {
        swapProviderTransactionsDao().getAll(
            tokenQueryId = token.tokenQuery.id,
            coinUid = token.coin.uid,
            blockchainType = token.blockchainType.uid,
            address = address,
            statusesExcluded = statusesExcluded,
            providersExcluded = providersExcluded,
            limit = limit
        )
    }.newestFirst(limit)

    fun getAllUnfinishedByAccount(
        accountId: String,
        statusesExcluded: List<String>,
        providersExcluded: List<SwapProvider>,
        limit: Int
    ) = dao(accountId)?.getAllUnfinishedByAccount(
        accountId = accountId,
        statusesExcluded = statusesExcluded,
        providersExcluded = providersExcluded,
        limit = limit
    ).orEmpty()

    fun observeByToken(
        token: Token,
        address: String,
        limit: Int = 50
    ): Flow<List<SwapProviderTransaction>> = router.observeAcrossAccounts {
        swapProviderTransactionsDao().observeByToken(
            tokenQueryId = token.tokenQuery.id,
            coinUid = token.coin.uid,
            blockchainType = token.blockchainType.uid,
            address = address,
            limit = limit
        )
    }.map { it.newestFirst(limit) }

    fun observeAll(): Flow<List<SwapProviderTransaction>> =
        router.observeAcrossAccounts { swapProviderTransactionsDao().observeAll() }
            .map { it.newestFirst(OBSERVE_ALL_LIMIT) }

    fun observeAllByAccount(
        accountId: String,
        limit: Int = 100
    ): Flow<List<SwapProviderTransaction>> =
        dao(accountId)?.observeAllByAccount(accountId, limit) ?: flowOf(emptyList())

    fun observeForActiveAccount(
        activeAccountStateFlow: Flow<ActiveAccountState>
    ): Flow<List<SwapProviderTransaction>> =
        activeAccountStateFlow.flatMapLatest { state ->
            val accountId = (state as? ActiveAccountState.ActiveAccount)?.account?.id
            if (accountId != null) observeAllByAccount(accountId) else flowOf(emptyList())
        }

    fun observeByDate(date: Long): Flow<SwapProviderTransaction?> =
        router.observeAcrossAccounts { swapProviderTransactionsDao().observeByDate(date).map(::listOfNotNull) }
            .map { it.firstOrNull() }

    suspend fun getTransaction(transactionId: String) =
        withContext(dispatcherProvider.io) {
            router.firstInAccounts { swapProviderTransactionsDao().getTransaction(transactionId) }
        }

    suspend fun getByDate(date: Long): SwapProviderTransaction? =
        withContext(dispatcherProvider.io) {
            router.firstInAccounts { swapProviderTransactionsDao().getByDate(date) }
        }

    fun getByTokenIn(
        token: Token,
        amountIn: BigDecimal?,
        timestamp: Long
    ) = router.accounts.mapNotNull {
        it.swapProviderTransactionsDao().getByTokenIn(
            tokenQueryId = token.tokenQuery.id,
            coinUid = token.coin.uid,
            blockchainType = token.blockchainType.uid,
            amountIn = amountIn,
            dateFrom = timestamp - THRESHOLD_MSEC,
            dateTo = timestamp + THRESHOLD_MSEC
        )
    }.maxByOrNull { it.date }

    fun getByOutgoingRecordUid(
        outgoingRecordUid: String
    ) = router.firstInAccounts { swapProviderTransactionsDao().getByOutgoingRecordUid(outgoingRecordUid) }

    fun getByIncomingRecordUid(
        incomingRecordUid: String
    ) = router.firstInAccounts { swapProviderTransactionsDao().getByIncomingRecordUid(incomingRecordUid) }

    fun getByTokenOut(
        token: Token,
        timestamp: Long,
        accountId: String
    ) = dao(accountId)?.getByTokenOut(
        tokenQueryId = token.tokenQuery.id,
        coinUid = token.coin.uid,
        blockchainType = token.blockchainType.uid,
        accountId = accountId,
        dateFrom = timestamp - THRESHOLD_MSEC,
        dateTo = timestamp + THRESHOLD_MSEC
    )

    fun getByAddressAndAmount(
        address: String,
        token: Token,
        accountId: String,
        amount: BigDecimal,
        timestamp: Long
    ): SwapProviderTransaction? = dao(accountId)?.getByAddressAndAmount(
        address = address,
        tokenQueryId = token.tokenQuery.id,
        coinUid = token.coin.uid,
        blockchainType = token.blockchainType.uid,
        accountId = accountId,
        amount = amount.toDouble(),
        tolerance = AMOUNT_TOLERANCE,
        timestamp = timestamp,
        timeWindowMs = FINISHED_AT_WINDOW_MS,
        dateFrom = timestamp - SwapTransactionMatcher.TIME_WINDOW_MS,
        dateTo = timestamp + SwapTransactionMatcher.TIME_WINDOW_MS
    )

    fun setIncomingRecordUid(date: Long, incomingRecordUid: String, amountOutReal: BigDecimal) =
        router.forEachAccount {
            swapProviderTransactionsDao().setIncomingRecordUid(date, incomingRecordUid, amountOutReal)
        }

    fun syncReceivedAt(date: Long, incomingRecordUid: String, receivedAt: Long) =
        router.forEachAccount { swapProviderTransactionsDao().syncReceivedAt(date, incomingRecordUid, receivedAt) }

    fun setOutgoingRecordUid(date: Long, outgoingRecordUid: String) =
        router.forEachAccount { swapProviderTransactionsDao().setOutgoingRecordUid(date, outgoingRecordUid) }

    fun updateStatusFields(
        date: Long,
        status: String,
        amountOutReal: BigDecimal?,
        finishedAt: Long?
    ) = router.forEachAccount {
        swapProviderTransactionsDao().updateStatusFields(date, status, amountOutReal, finishedAt)
    }

    fun updateTransactionId(date: Long, newTransactionId: String) =
        router.forEachAccount { swapProviderTransactionsDao().updateTransactionId(date, newTransactionId) }

    fun getByProviderAndTokenOut(
        provider: SwapProvider,
        tokenOut: Token,
        accountId: String,
        addressOut: String,
        expectedAmount: BigDecimal,
        legStartTime: Long,
    ): SwapProviderTransaction? = dao(accountId)?.getByProviderAndTokenOut(
        provider = provider.name,
        tokenQueryId = tokenOut.tokenQuery.id,
        coinUid = tokenOut.coin.uid,
        blockchainType = tokenOut.blockchainType.uid,
        accountId = accountId,
        addressOut = addressOut,
        expectedAmount = expectedAmount.toDouble(),
        tolerance = AMOUNT_TOLERANCE,
        dateFrom = legStartTime - LEGACY_WINDOW_BEFORE_MS,
        dateTo = legStartTime + LEGACY_WINDOW_AFTER_MS,
    )

    fun getUnmatchedSwapsByTokenOut(
        token: Token,
        fromTimestamp: Long,
        toTimestamp: Long,
        amount: BigDecimal,
        tolerance: Double,
        accountId: String,
        limit: Int = 100
    ): List<SwapProviderTransaction> = dao(accountId)?.getUnmatchedSwapsByTokenOut(
        tokenQueryId = token.tokenQuery.id,
        coinUid = token.coin.uid,
        blockchainType = token.blockchainType.uid,
        accountId = accountId,
        dateFrom = fromTimestamp,
        dateTo = toTimestamp,
        amount = amount.toDouble(),
        tolerance = tolerance,
        limit = limit
    ).orEmpty()
}

fun List<SwapProviderTransaction>.toRecordUidMap(): Map<String, SwapProviderTransaction> {
    return flatMap { swap ->
        listOfNotNull(
            swap.incomingRecordUid?.let { it to swap },
            swap.outgoingRecordUid?.let { it to swap }
        )
    }.toMap()
}
