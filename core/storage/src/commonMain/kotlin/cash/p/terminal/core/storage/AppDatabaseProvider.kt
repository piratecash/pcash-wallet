package cash.p.terminal.core.storage

/** Consumers resolve [database] per call: on desktop it changes when the vault is wiped and recreated. */
interface AppDatabaseProvider {
    val database: AppDatabase
}

class StaticAppDatabaseProvider(override val database: AppDatabase) : AppDatabaseProvider
