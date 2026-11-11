package cash.p.terminal.premium.data.database

import cash.p.terminal.wallet.managers.logIfBelowSessionRoot

/** Consumers resolve [database] per call: on desktop it changes when the vault is wiped and recreated. */
interface PremiumDatabaseProvider {
    val database: PremiumDatabase
}

class StaticPremiumDatabaseProvider(override val database: PremiumDatabase) : PremiumDatabaseProvider

/** Premium data lives on the level: consumers resolve the database per call, the answer changes with the session. */
interface PremiumDatabaseRouter {
    /** The lowest level this session can open; a lower one is served by this one. */
    val rootLevel: Int

    fun forLevel(level: Int): PremiumDatabase
}

internal val PremiumDatabaseRouter.sessionDatabase: PremiumDatabase get() = forLevel(rootLevel)

class SinglePremiumDatabaseRouter(private val databaseProvider: PremiumDatabaseProvider) : PremiumDatabaseRouter {
    override val rootLevel: Int = 0

    override fun forLevel(level: Int): PremiumDatabase {
        logIfBelowSessionRoot(level, rootLevel)
        return databaseProvider.database
    }
}
