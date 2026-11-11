package cash.p.terminal.premium.domain

import cash.p.terminal.premium.data.dao.AccountPremiumCacheDao
import cash.p.terminal.premium.data.dao.DemoPremiumUserDao
import cash.p.terminal.premium.data.database.PremiumDatabase
import cash.p.terminal.premium.data.database.PremiumDatabaseRouter
import cash.p.terminal.premium.data.database.SinglePremiumDatabaseRouter
import cash.p.terminal.premium.data.database.StaticPremiumDatabaseProvider
import io.mockk.every
import io.mockk.mockk

internal fun premiumDatabaseRouterOf(
    demoDao: DemoPremiumUserDao = mockk(),
    cacheDao: AccountPremiumCacheDao = mockk(),
): PremiumDatabaseRouter = SinglePremiumDatabaseRouter(
    StaticPremiumDatabaseProvider(
        mockk<PremiumDatabase> {
            every { demoPremiumUserDao() } returns demoDao
            every { accountPremiumCacheDao() } returns cacheDao
        }
    )
)
