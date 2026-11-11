package cash.p.terminal.premium.data.repository

import cash.p.terminal.premium.data.database.PremiumDatabaseRouter
import cash.p.terminal.premium.data.database.sessionDatabase
import cash.p.terminal.premium.data.model.PremiumUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class PremiumUserRepository(
    private val premiumDatabaseRouter: PremiumDatabaseRouter
) {
    private val sessionDao get() = premiumDatabaseRouter.sessionDatabase.premiumUserDao()

    suspend fun getByLevel(level: Int) = withContext(Dispatchers.IO) {
        premiumDatabaseRouter.forLevel(level).premiumUserDao().getByLevel(level)
    }

    suspend fun getByLevels(levels: List<Int>) = withContext(Dispatchers.IO) {
        levels.groupBy(premiumDatabaseRouter::forLevel)
            .flatMap { (database, databaseLevels) -> database.premiumUserDao().getByLevels(databaseLevels) }
    }

    suspend fun deleteByAccount(accountId: String) = withContext(Dispatchers.IO) {
        sessionDao.deleteByAccount(accountId)
    }

    suspend fun getByAccountId(accountId: String) = withContext(Dispatchers.IO) {
        sessionDao.getByAccountId(accountId)
    }

    suspend fun insert(premiumUser: PremiumUser) = withContext(Dispatchers.IO) {
        premiumDatabaseRouter.forLevel(premiumUser.level).premiumUserDao().insert(premiumUser)
    }
} 
