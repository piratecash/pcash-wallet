package cash.p.terminal.core.providers

/** Marks a ZEC account created in this install, so its first sync can skip the deep history walk. */
interface ZcashDbMarker {
    fun markPristine(accountId: String)
}
