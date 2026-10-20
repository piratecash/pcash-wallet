package cash.p.terminal.premium.data.repository

import cash.p.terminal.premium.data.database.PremiumDatabaseProvider
import cash.p.terminal.premium.data.model.PremiumUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class PremiumUserRepository(
    private val premiumDatabaseProvider: PremiumDatabaseProvider
) {
    private val premiumUserDao get() = premiumDatabaseProvider.database.premiumUserDao()

    suspend fun getByLevel(level: Int) = withContext(Dispatchers.IO) {
        premiumUserDao.getByLevel(level)
    }

    suspend fun getByLevels(level: List<Int>) = withContext(Dispatchers.IO) {
        premiumUserDao.getByLevels(level)
    }

    suspend fun deleteByAccount(accountId: String) = withContext(Dispatchers.IO) {
        premiumUserDao.deleteByAccount(accountId)
    }

    suspend fun getByAccountId(accountId: String) = withContext(Dispatchers.IO) {
        premiumUserDao.getByAccountId(accountId)
    }

    suspend fun insert(premiumUser: PremiumUser) = withContext(Dispatchers.IO) {
        premiumUserDao.insert(premiumUser)
    }
} 
