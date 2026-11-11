package cash.p.terminal.core.storage

import cash.p.terminal.entities.PendingMultiSwap
import cash.p.terminal.wallet.ActiveAccountState
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class PendingMultiSwapStorage(
    private val router: StorageRouter,
    private val dispatcherProvider: DispatcherProvider
) {
    private companion object {
        val MAX_AGE_MS = TimeUnit.DAYS.toMillis(30)
    }

    suspend fun cleanup() = withContext(dispatcherProvider.io) {
        router.forEachAccount { pendingMultiSwapDao().deleteOlderThan(System.currentTimeMillis() - MAX_AGE_MS) }
    }

    fun getByAccountId(accountId: String): Flow<List<PendingMultiSwap>> =
        router.account(accountId)?.pendingMultiSwapDao()?.getByAccountId(accountId) ?: flowOf(emptyList())

    fun observeForActiveAccount(
        activeAccountStateFlow: Flow<ActiveAccountState>
    ): Flow<List<PendingMultiSwap>> =
        activeAccountStateFlow.flatMapLatest { state ->
            val accountId = (state as? ActiveAccountState.ActiveAccount)?.account?.id
            if (accountId != null) getByAccountId(accountId) else flowOf(emptyList())
        }

    suspend fun getAllOnceByAccountId(accountId: String): List<PendingMultiSwap> =
        withContext(dispatcherProvider.io) {
            router.account(accountId)?.pendingMultiSwapDao()?.getAllOnceByAccountId(accountId).orEmpty()
        }

    suspend fun getById(id: String): PendingMultiSwap? = withContext(dispatcherProvider.io) {
        router.firstInAccounts { pendingMultiSwapDao().getById(id) }
    }

    suspend fun insert(swap: PendingMultiSwap) = withContext(dispatcherProvider.io) {
        router.account(swap.accountId)?.pendingMultiSwapDao()?.insert(swap)
    }

    suspend fun updateLeg1(
        id: String,
        status: String,
        amountOut: BigDecimal?,
        transactionId: String?
    ) = withContext(dispatcherProvider.io) {
        router.forEachAccount { pendingMultiSwapDao().updateLeg1(id, status, amountOut, transactionId) }
    }

    suspend fun updateLeg2(
        id: String,
        status: String,
        amountOut: BigDecimal?,
        transactionId: String?
    ) = withContext(dispatcherProvider.io) {
        router.forEachAccount { pendingMultiSwapDao().updateLeg2(id, status, amountOut, transactionId) }
    }

    suspend fun setLeg1ProviderTransactionId(id: String, providerTransactionId: String) =
        withContext(dispatcherProvider.io) {
            router.forEachAccount { pendingMultiSwapDao().setLeg1ProviderTransactionId(id, providerTransactionId) }
        }

    suspend fun setLeg1InfoRecordUid(id: String, recordUid: String) =
        withContext(dispatcherProvider.io) {
            router.forEachAccount { pendingMultiSwapDao().setLeg1InfoRecordUid(id, recordUid) }
        }

    suspend fun moveLeg1RecordUid(fromUids: List<String>, toUid: String) =
        withContext(dispatcherProvider.io) {
            router.forEachAccount { pendingMultiSwapDao().moveLeg1RecordUid(fromUids, toUid) }
        }

    suspend fun delete(id: String) = withContext(dispatcherProvider.io) {
        router.forEachAccount { pendingMultiSwapDao().delete(id) }
    }

    fun count(): Flow<Int> =
        router.observeAcrossAccounts { pendingMultiSwapDao().count().map(::listOf) }.map { it.sum() }
}
