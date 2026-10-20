package cash.p.terminal.premium.domain

import cash.p.terminal.premium.data.dao.AccountPremiumCacheDao
import cash.p.terminal.premium.data.dao.DemoPremiumUserDao
import cash.p.terminal.premium.data.database.PremiumDatabase
import cash.p.terminal.premium.data.database.PremiumDatabaseProvider
import cash.p.terminal.premium.data.database.StaticPremiumDatabaseProvider
import io.mockk.every
import io.mockk.mockk

internal fun premiumDatabaseProviderOf(
    demoDao: DemoPremiumUserDao = mockk(),
    cacheDao: AccountPremiumCacheDao = mockk(),
): PremiumDatabaseProvider = StaticPremiumDatabaseProvider(
    mockk<PremiumDatabase> {
        every { demoPremiumUserDao() } returns demoDao
        every { accountPremiumCacheDao() } returns cacheDao
    }
)
