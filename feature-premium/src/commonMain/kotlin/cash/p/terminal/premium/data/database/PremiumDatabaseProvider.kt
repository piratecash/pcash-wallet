package cash.p.terminal.premium.data.database

/** Consumers resolve [database] per call: on desktop it changes when the vault is wiped and recreated. */
interface PremiumDatabaseProvider {
    val database: PremiumDatabase
}

class StaticPremiumDatabaseProvider(override val database: PremiumDatabase) : PremiumDatabaseProvider
