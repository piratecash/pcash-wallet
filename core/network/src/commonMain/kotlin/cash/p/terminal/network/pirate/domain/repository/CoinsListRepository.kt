package cash.p.terminal.network.pirate.domain.repository

import cash.p.terminal.network.pirate.domain.model.CoinsList

interface CoinsListRepository {
    suspend fun coinsList(): CoinsList
    suspend fun coinRanks(): Map<String, Int>
    suspend fun coinsUpdatedAt(): Long
}
