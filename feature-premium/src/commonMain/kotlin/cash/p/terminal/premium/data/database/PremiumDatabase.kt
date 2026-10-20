package cash.p.terminal.premium.data.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import cash.p.terminal.premium.data.dao.AccountPremiumCacheDao
import cash.p.terminal.premium.data.dao.BnbPremiumAddressDao
import cash.p.terminal.premium.data.dao.DemoPremiumUserDao
import cash.p.terminal.premium.data.dao.PremiumUserDao
import cash.p.terminal.premium.data.model.AccountPremiumCacheEntity
import cash.p.terminal.premium.data.model.BnbPremiumAddress
import cash.p.terminal.premium.data.model.DemoPremiumUser
import cash.p.terminal.premium.data.model.PremiumUser

@Database(
    entities = [
        PremiumUser::class,
        DemoPremiumUser::class,
        BnbPremiumAddress::class,
        AccountPremiumCacheEntity::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(PremiumTypeConverter::class)
@ConstructedBy(PremiumDatabaseConstructor::class)
abstract class PremiumDatabase : RoomDatabase() {

    internal abstract fun premiumUserDao(): PremiumUserDao
    internal abstract fun demoPremiumUserDao(): DemoPremiumUserDao
    internal abstract fun bnbPremiumAddressDao(): BnbPremiumAddressDao
    internal abstract fun accountPremiumCacheDao(): AccountPremiumCacheDao
}

@Suppress("KotlinNoActualForExpect")
expect object PremiumDatabaseConstructor : RoomDatabaseConstructor<PremiumDatabase> {
    override fun initialize(): PremiumDatabase
}
