package cash.p.terminal.wallet.syncers

import cash.p.terminal.network.pirate.domain.repository.CoinsListRepository
import cash.p.terminal.wallet.SyncInfo
import cash.p.terminal.wallet.managers.VirtualCoinMapper
import cash.p.terminal.wallet.storage.CoinStorage
import cash.p.terminal.wallet.storage.SyncerStateDao
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

class CoinSyncer(
    private val repository: CoinsListRepository,
    private val storage: CoinStorage,
    private val syncerStateDao: SyncerStateDao,
    private val virtualCoinMapper: VirtualCoinMapper,
    private val mapper: CoinsListMapper,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val logger = Logger.withTag("CoinSyncer")

    private val keyListTimestamp = "coin-syncer-v2-list-timestamp"
    private val keyListDownloadedAt = "coin-syncer-v2-list-downloaded-at"
    private val keyRanksDownloadedAt = "coin-syncer-v2-ranks-downloaded-at"
    private val keyCoinsCount = "coin-syncer-v2-coins-count"
    private val keyBlockchainsCount = "coin-syncer-v2-blockchains-count"
    private val keyTokensCount = "coin-syncer-v2-tokens-count"
    private val keyServerAvailable = "coin-syncer-v2-server-available"

    suspend fun sync(force: Boolean) = withContext(dispatcherProvider.io) {
        // List and ranks are independent: a failure of one must not block the other.
        val listSynced = attempt("list") { syncListIfOutdated(force) }
        val ranksSynced = attempt("ranks") { syncRanksIfStale() }
        syncerStateDao.save(keyServerAvailable, (listSynced != null && ranksSynced != null).toString())
    }

    private inline fun <T : Any> attempt(step: String, block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.e(e) { "$step sync error" }
        null
    }

    private suspend fun syncListIfOutdated(force: Boolean) {
        val updatedAt = repository.coinsUpdatedAt()
        if (!force && syncerStateDao.get(keyListTimestamp)?.toLongOrNull() == updatedAt) return

        val data = mapper.map(repository.coinsList(), storage.ranks(), virtualCoinMapper)
        check(data.coins.isNotEmpty() && data.blockchains.isNotEmpty() && data.tokens.isNotEmpty()) {
            "Empty coins list response"
        }
        storage.replaceAll(data)
        updateCounts()
        syncerStateDao.save(keyListTimestamp, updatedAt.toString())
        syncerStateDao.save(keyListDownloadedAt, System.currentTimeMillis().toString())
    }

    private suspend fun syncRanksIfStale() {
        if (!ranksStale()) return

        val ranks = repository.coinRanks()
        check(ranks.isNotEmpty()) { "Empty coin ranks response" }
        storage.applyRanks(ranks)
        syncerStateDao.save(keyRanksDownloadedAt, System.currentTimeMillis().toString())
    }

    private fun ranksStale(): Boolean {
        val ranksDownloadedAt = syncerStateDao.get(keyRanksDownloadedAt)?.toLongOrNull() ?: return true
        // Ranks older than the current list miss its new coins, e.g. after a failed post-list refresh.
        val listDownloadedAt = syncerStateDao.get(keyListDownloadedAt)?.toLongOrNull() ?: 0L
        val elapsed = System.currentTimeMillis() - ranksDownloadedAt
        return elapsed < 0 || elapsed >= RANKS_MAX_AGE_MS || ranksDownloadedAt < listDownloadedAt
    }

    private fun updateCounts() {
        val coinDao = storage.marketDatabase.coinDao()
        syncerStateDao.save(keyCoinsCount, coinDao.getCoinsCount().toString())
        syncerStateDao.save(keyBlockchainsCount, coinDao.getBlockchainsCount().toString())
        syncerStateDao.save(keyTokensCount, coinDao.getTokensCount().toString())
    }

    fun syncInfo(): SyncInfo {
        if (syncerStateDao.get(keyCoinsCount) == null ||
            syncerStateDao.get(keyBlockchainsCount) == null ||
            syncerStateDao.get(keyTokensCount) == null
        ) {
            updateCounts()
        }

        return SyncInfo(
            listTimestamp = syncerStateDao.get(keyListTimestamp),
            listDownloadedAt = syncerStateDao.get(keyListDownloadedAt)?.toLongOrNull(),
            ranksDownloadedAt = syncerStateDao.get(keyRanksDownloadedAt)?.toLongOrNull(),
            coinsCount = syncerStateDao.get(keyCoinsCount)?.toIntOrNull(),
            blockchainsCount = syncerStateDao.get(keyBlockchainsCount)?.toIntOrNull(),
            tokensCount = syncerStateDao.get(keyTokensCount)?.toIntOrNull(),
            serverAvailable = syncerStateDao.get(keyServerAvailable)?.toBooleanStrictOrNull()
        )
    }

    private companion object {
        const val RANKS_MAX_AGE_MS = 60 * 60 * 1000L
    }
}
