package cash.p.terminal.core.managers

/** Account-keyed settings that exist only on some platforms, wiped with the account. */
interface PlatformAccountDataCleaner {
    fun clear(accountId: String)
}
