package cash.p.terminal.premium.data.database

import android.content.Context
import androidx.room.Room
import cash.p.terminal.premium.data.database.migrations.Migration_5_6
import cash.p.terminal.premium.data.database.migrations.Migration_6_7
import cash.p.terminal.premium.data.database.migrations.Migration_7_8
import cash.p.terminal.premium.data.database.migrations.Migration_8_9

private const val DATABASE_NAME = "premium_database"

internal fun createPremiumDatabase(context: Context): PremiumDatabase =
    Room.databaseBuilder(
        context.applicationContext,
        PremiumDatabase::class.java,
        DATABASE_NAME
    )
        .addMigrations(
            Migration_5_6,
            Migration_6_7,
            Migration_7_8,
            Migration_8_9,
        )
        .build()
