package cash.p.terminal.wallet.di

import cash.p.terminal.wallet.storage.MarketDatabase

/**
 * Opening the database is what runs the initial-dump callback; Room defers it until the first
 * query, so one cheap primary-key lookup keeps creation at build time as it has always been.
 */
internal fun MarketDatabase.forceCreation(): MarketDatabase = apply {
    syncerStateDao().get("")
}
