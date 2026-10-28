package cash.p.terminal.modules.multiswap.providers

/** Swap provider preferences persisted by the platform. */
interface SwapProvidersStorage {
    var disabledProviderIds: Set<String>

    /** JSON of the last known p.cash backend providers. */
    var backendProviders: String?
}
