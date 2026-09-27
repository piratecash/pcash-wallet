package cash.p.terminal.network.pirate.data.repository

import cash.p.terminal.network.pirate.api.CoinsListApi
import cash.p.terminal.network.pirate.data.mapper.CoinsListMapper
import cash.p.terminal.network.pirate.domain.model.CoinsList
import cash.p.terminal.network.pirate.domain.repository.CoinsListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class CoinsListRepositoryImpl(
    private val coinsListApi: CoinsListApi,
    private val coinsListMapper: CoinsListMapper,
) : CoinsListRepository {
    override suspend fun coinsList(): CoinsList = withContext(Dispatchers.IO) {
        coinsListApi.coinsList().let(coinsListMapper::map)
    }

    override suspend fun coinRanks(): Map<String, Int> = withContext(Dispatchers.IO) {
        coinsListApi.coinRanks()
    }

    override suspend fun coinsUpdatedAt(): Long = coinsListApi.coinsUpdates().updatedAt
}
