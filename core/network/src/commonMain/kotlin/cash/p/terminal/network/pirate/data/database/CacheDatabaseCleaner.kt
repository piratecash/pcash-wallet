package cash.p.terminal.network.pirate.data.database

class CacheDatabaseCleaner internal constructor(
    private val dao: CacheChangeNowCoinAssociationDao
) {
    suspend fun clear() = dao.deleteAll()
}
